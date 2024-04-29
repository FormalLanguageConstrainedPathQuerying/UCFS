/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.ccr.action;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreClusterStateListener;
import org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreSnapshotRequest;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.ActiveShardCount;
import org.elasticsearch.action.support.ActiveShardsObserver;
import org.elasticsearch.action.support.master.TransportMasterNodeAction;
import org.elasticsearch.client.internal.Client;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.DataStream;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.IndexScopedSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.license.LicenseUtils;
import org.elasticsearch.snapshots.RestoreInfo;
import org.elasticsearch.snapshots.RestoreService;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.RemoteClusterService;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xpack.ccr.CcrLicenseChecker;
import org.elasticsearch.xpack.ccr.CcrSettings;
import org.elasticsearch.xpack.ccr.repository.CcrRepository;
import org.elasticsearch.xpack.core.ccr.action.FollowParameters;
import org.elasticsearch.xpack.core.ccr.action.PutFollowAction;
import org.elasticsearch.xpack.core.ccr.action.ResumeFollowAction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static org.elasticsearch.cluster.metadata.DataStream.BACKING_INDEX_PREFIX;
import static org.elasticsearch.xpack.ccr.Ccr.CCR_THREAD_POOL_NAME;

public final class TransportPutFollowAction extends TransportMasterNodeAction<PutFollowAction.Request, PutFollowAction.Response> {

    private static final Logger logger = LogManager.getLogger(TransportPutFollowAction.class);

    private final IndexScopedSettings indexScopedSettings;
    private final Client client;
    private final Executor remoteClientResponseExecutor;
    private final RestoreService restoreService;
    private final CcrLicenseChecker ccrLicenseChecker;

    @Inject
    public TransportPutFollowAction(
        final ThreadPool threadPool,
        final TransportService transportService,
        final ClusterService clusterService,
        final IndexScopedSettings indexScopedSettings,
        final ActionFilters actionFilters,
        final IndexNameExpressionResolver indexNameExpressionResolver,
        final Client client,
        final RestoreService restoreService,
        final CcrLicenseChecker ccrLicenseChecker
    ) {
        super(
            PutFollowAction.NAME,
            transportService,
            clusterService,
            threadPool,
            actionFilters,
            PutFollowAction.Request::new,
            indexNameExpressionResolver,
            PutFollowAction.Response::new,
            EsExecutors.DIRECT_EXECUTOR_SERVICE
        );
        this.indexScopedSettings = indexScopedSettings;
        this.client = client;
        this.remoteClientResponseExecutor = threadPool.executor(CCR_THREAD_POOL_NAME);
        this.restoreService = restoreService;
        this.ccrLicenseChecker = Objects.requireNonNull(ccrLicenseChecker);
    }

    @Override
    protected void masterOperation(
        Task task,
        final PutFollowAction.Request request,
        final ClusterState state,
        final ActionListener<PutFollowAction.Response> listener
    ) {
        if (ccrLicenseChecker.isCcrAllowed() == false) {
            listener.onFailure(LicenseUtils.newComplianceException("ccr"));
            return;
        }
        String remoteCluster = request.getRemoteCluster();
        client.getRemoteClusterClient(
            remoteCluster,
            remoteClientResponseExecutor,
            RemoteClusterService.DisconnectedStrategy.RECONNECT_IF_DISCONNECTED
        );

        String leaderIndex = request.getLeaderIndex();
        ccrLicenseChecker.checkRemoteClusterLicenseAndFetchLeaderIndexMetadataAndHistoryUUIDs(
            client,
            remoteCluster,
            leaderIndex,
            listener::onFailure,
            (historyUUID, tuple) -> createFollowerIndex(tuple.v1(), tuple.v2(), request, listener)
        );
    }

    private void createFollowerIndex(
        final IndexMetadata leaderIndexMetadata,
        final DataStream remoteDataStream,
        final PutFollowAction.Request request,
        final ActionListener<PutFollowAction.Response> listener
    ) {
        if (leaderIndexMetadata == null) {
            listener.onFailure(new IllegalArgumentException("leader index [" + request.getLeaderIndex() + "] does not exist"));
            return;
        }
        if (IndexSettings.INDEX_SOFT_DELETES_SETTING.get(leaderIndexMetadata.getSettings()) == false) {
            listener.onFailure(
                new IllegalArgumentException("leader index [" + request.getLeaderIndex() + "] does not have soft deletes enabled")
            );
            return;
        }
        if (leaderIndexMetadata.isSearchableSnapshot()) {
            listener.onFailure(
                new IllegalArgumentException(
                    "leader index ["
                        + request.getLeaderIndex()
                        + "] is a searchable snapshot index and cannot be used as a leader index for cross-cluster replication purpose"
                )
            );
            return;
        }

        final Settings replicatedRequestSettings = TransportResumeFollowAction.filter(request.getSettings());
        if (replicatedRequestSettings.isEmpty() == false) {
            final List<String> unknownKeys = replicatedRequestSettings.keySet()
                .stream()
                .filter(s -> indexScopedSettings.get(s) == null)
                .collect(Collectors.toList());
            final String message;
            if (unknownKeys.isEmpty()) {
                message = String.format(
                    Locale.ROOT,
                    "can not put follower index that could override leader settings %s",
                    replicatedRequestSettings
                );
            } else {
                message = String.format(
                    Locale.ROOT,
                    "unknown setting%s [%s]",
                    unknownKeys.size() == 1 ? "" : "s",
                    String.join(",", unknownKeys)
                );
            }
            listener.onFailure(new IllegalArgumentException(message));
            return;
        }

        final Settings overrideSettings = Settings.builder()
            .put(IndexMetadata.SETTING_INDEX_PROVIDED_NAME, request.getFollowerIndex())
            .put(CcrSettings.CCR_FOLLOWING_INDEX_SETTING.getKey(), true)
            .put(request.getSettings())
            .build();

        final String leaderClusterRepoName = CcrRepository.NAME_PREFIX + request.getRemoteCluster();
        final RestoreSnapshotRequest restoreRequest = new RestoreSnapshotRequest(leaderClusterRepoName, CcrRepository.LATEST).indices(
            request.getLeaderIndex()
        )
            .indicesOptions(request.indicesOptions())
            .renamePattern("^(.*)$")
            .renameReplacement(Matcher.quoteReplacement(request.getFollowerIndex()))
            .masterNodeTimeout(request.masterNodeTimeout())
            .indexSettings(overrideSettings)
            .quiet(true);

        final Client clientWithHeaders = CcrLicenseChecker.wrapClient(
            this.client,
            threadPool.getThreadContext().getHeaders(),
            clusterService.state()
        );
        ActionListener<RestoreService.RestoreCompletionResponse> delegatelistener = listener.delegateFailure(
            (delegatedListener, response) -> afterRestoreStarted(clientWithHeaders, request, delegatedListener, response)
        );
        if (remoteDataStream == null) {
            restoreService.restoreSnapshot(restoreRequest, delegatelistener);
        } else {
            String followerIndexName = request.getFollowerIndex();
            BiConsumer<ClusterState, Metadata.Builder> updater = (currentState, mdBuilder) -> {
                final String localDataStreamName;

                final String dsName = request.getDataStreamName();
                if (Strings.hasText(dsName)) {
                    localDataStreamName = dsName;
                } else {
                    localDataStreamName = remoteDataStream.getName();
                }
                final DataStream localDataStream = mdBuilder.dataStreamMetadata().dataStreams().get(localDataStreamName);
                final Index followerIndex = mdBuilder.get(followerIndexName).getIndex();
                assert followerIndex != null : "expected followerIndex " + followerIndexName + " to exist in the state, but it did not";

                final DataStream updatedDataStream = updateLocalDataStream(
                    followerIndex,
                    localDataStream,
                    localDataStreamName,
                    remoteDataStream
                );
                mdBuilder.put(updatedDataStream);
            };
            restoreService.restoreSnapshot(restoreRequest, delegatelistener, updater);
        }
    }

    private void afterRestoreStarted(
        Client clientWithHeaders,
        PutFollowAction.Request request,
        ActionListener<PutFollowAction.Response> originalListener,
        RestoreService.RestoreCompletionResponse response
    ) {
        final ActionListener<PutFollowAction.Response> listener;
        if (ActiveShardCount.NONE.equals(request.waitForActiveShards())) {
            originalListener.onResponse(new PutFollowAction.Response(true, false, false));
            listener = new ActionListener<>() {

                @Override
                public void onResponse(PutFollowAction.Response response) {
                    logger.debug("put follow {} completed with {}", request, response);
                }

                @Override
                public void onFailure(Exception e) {
                    logger.debug(() -> "put follow " + request + " failed during the restore process", e);
                }
            };
        } else {
            listener = originalListener;
        }

        RestoreClusterStateListener.createAndRegisterListener(
            clusterService,
            response,
            listener.delegateFailure((delegatedListener, restoreSnapshotResponse) -> {
                RestoreInfo restoreInfo = restoreSnapshotResponse.getRestoreInfo();
                if (restoreInfo == null) {
                    delegatedListener.onResponse(new PutFollowAction.Response(true, false, false));
                } else if (restoreInfo.failedShards() == 0) {
                    initiateFollowing(clientWithHeaders, request, delegatedListener);
                } else {
                    assert restoreInfo.failedShards() > 0 : "Should have failed shards";
                    delegatedListener.onResponse(new PutFollowAction.Response(true, false, false));
                }
            }),
            threadPool.getThreadContext()
        );
    }

    private void initiateFollowing(
        final Client clientWithHeaders,
        final PutFollowAction.Request request,
        final ActionListener<PutFollowAction.Response> listener
    ) {
        assert request.waitForActiveShards() != ActiveShardCount.DEFAULT : "PutFollowAction does not support DEFAULT.";
        FollowParameters parameters = request.getParameters();
        ResumeFollowAction.Request resumeFollowRequest = new ResumeFollowAction.Request();
        resumeFollowRequest.setFollowerIndex(request.getFollowerIndex());
        resumeFollowRequest.setParameters(new FollowParameters(parameters));
        resumeFollowRequest.masterNodeTimeout(request.masterNodeTimeout());
        clientWithHeaders.execute(
            ResumeFollowAction.INSTANCE,
            resumeFollowRequest,
            listener.delegateFailureAndWrap(
                (l, r) -> ActiveShardsObserver.waitForActiveShards(
                    clusterService,
                    new String[] { request.getFollowerIndex() },
                    request.waitForActiveShards(),
                    request.ackTimeout(),
                    l.map(result -> new PutFollowAction.Response(true, result, r.isAcknowledged()))
                )
            )
        );
    }

    /**
     * Given the backing index that the follower is going to follow, the local data stream (if it
     * exists) and the remote data stream, return the new local data stream for the local cluster
     * (the follower) updated with whichever information is necessary to restore the new
     * soon-to-be-followed index.
     */
    static DataStream updateLocalDataStream(
        Index backingIndexToFollow,
        DataStream localDataStream,
        String localDataStreamName,
        DataStream remoteDataStream
    ) {
        if (localDataStream == null) {
            return remoteDataStream.copy()
                .setName(localDataStreamName)
                .setIndices(List.of(backingIndexToFollow))
                .setReplicated(true)
                .setRolloverOnWrite(false)
                .build();
        } else {
            if (localDataStream.isReplicated() == false) {
                throw new IllegalArgumentException(
                    "cannot follow backing index ["
                        + backingIndexToFollow.getName()
                        + "], because local data stream ["
                        + localDataStream.getName()
                        + "] is no longer marked as replicated"
                );
            }

            final List<Index> backingIndices;
            if (localDataStream.getIndices().contains(backingIndexToFollow) == false) {
                backingIndices = new ArrayList<>(localDataStream.getIndices());
                backingIndices.add(backingIndexToFollow);

                String partitionByBackingIndexBaseName = BACKING_INDEX_PREFIX + localDataStream.getName();
                backingIndices.sort(
                    Comparator.comparing((Index o) -> o.getName().contains(partitionByBackingIndexBaseName) ? 1 : -1)
                        .thenComparing((Index o) -> {
                            int backingPrefixPosition = o.getName().indexOf(BACKING_INDEX_PREFIX);
                            return backingPrefixPosition > -1 ? o.getName().substring(backingPrefixPosition) : o.getName();
                        })
                );
            } else {
                backingIndices = localDataStream.getIndices();
            }

            return localDataStream.copy()
                .setIndices(backingIndices)
                .setGeneration(remoteDataStream.getGeneration())
                .setMetadata(remoteDataStream.getMetadata())
                .build();
        }
    }

    @Override
    protected ClusterBlockException checkBlock(final PutFollowAction.Request request, final ClusterState state) {
        return state.blocks().indexBlockedException(ClusterBlockLevel.METADATA_WRITE, request.getFollowerIndex());
    }
}
