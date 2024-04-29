/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.datastreams;

import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.rollover.Condition;
import org.elasticsearch.action.admin.indices.rollover.MaxDocsCondition;
import org.elasticsearch.action.admin.indices.rollover.OptimalShardCountCondition;
import org.elasticsearch.action.admin.indices.rollover.RolloverConditions;
import org.elasticsearch.action.admin.indices.rollover.RolloverInfo;
import org.elasticsearch.action.admin.indices.rollover.RolloverRequest;
import org.elasticsearch.action.admin.indices.rollover.RolloverResponse;
import org.elasticsearch.action.admin.indices.stats.CommonStats;
import org.elasticsearch.action.admin.indices.stats.IndicesStatsAction;
import org.elasticsearch.action.admin.indices.stats.ShardStats;
import org.elasticsearch.action.admin.indices.stats.TransportIndicesStatsAction;
import org.elasticsearch.action.admin.indices.template.put.TransportPutComposableIndexTemplateAction;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.datastreams.CreateDataStreamAction;
import org.elasticsearch.action.datastreams.autosharding.DataStreamAutoShardingService;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.ComposableIndexTemplate;
import org.elasticsearch.cluster.metadata.DataStream;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.Template;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.routing.RecoverySource;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.cluster.routing.UnassignedInfo;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.datastreams.lifecycle.DataStreamLifecycleService;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.mapper.DateFieldMapper;
import org.elasticsearch.index.shard.DocsStats;
import org.elasticsearch.index.shard.IndexingStats;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.index.shard.ShardPath;
import org.elasticsearch.index.store.StoreStats;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.transport.MockTransportService;
import org.elasticsearch.xcontent.XContentType;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.elasticsearch.action.datastreams.autosharding.DataStreamAutoShardingService.DATA_STREAMS_AUTO_SHARDING_ENABLED;
import static org.elasticsearch.cluster.metadata.MetadataIndexTemplateService.DEFAULT_TIMESTAMP_FIELD;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

public class DataStreamAutoshardingIT extends ESIntegTestCase {

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return List.of(DataStreamsPlugin.class, MockTransportService.TestPlugin.class, TestAutoshardingPlugin.class);
    }

    @Before
    public void configureClusterSettings() {
        updateClusterSettings(
            Settings.builder()
                .put(DataStreamLifecycleService.DATA_STREAM_LIFECYCLE_POLL_INTERVAL, "30d")
        );
    }

    @After
    public void resetClusterSetting() {
        updateClusterSettings(Settings.builder().putNull(DataStreamLifecycleService.DATA_STREAM_LIFECYCLE_POLL_INTERVAL));
    }

    public void testRolloverOnAutoShardCondition() throws Exception {
        final String dataStreamName = "logs-es";

        putComposableIndexTemplate(
            "my-template",
            List.of("logs-*"),
            Settings.builder().put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 3).put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 0).build()
        );
        final var createDataStreamRequest = new CreateDataStreamAction.Request(dataStreamName);
        assertAcked(client().execute(CreateDataStreamAction.INSTANCE, createDataStreamRequest).actionGet());

        indexDocs(dataStreamName, randomIntBetween(100, 200));

        {
            ClusterState clusterStateBeforeRollover = internalCluster().getCurrentMasterNodeInstance(ClusterService.class).state();
            DataStream dataStreamBeforeRollover = clusterStateBeforeRollover.getMetadata().dataStreams().get(dataStreamName);
            String assignedShardNodeId = clusterStateBeforeRollover.routingTable()
                .index(dataStreamBeforeRollover.getWriteIndex())
                .shard(0)
                .primaryShard()
                .currentNodeId();

            Index firstGenerationIndex = clusterStateBeforeRollover.metadata().dataStreams().get(dataStreamName).getWriteIndex();
            IndexMetadata firstGenerationMeta = clusterStateBeforeRollover.getMetadata().index(firstGenerationIndex);

            List<ShardStats> shards = new ArrayList<>(firstGenerationMeta.getNumberOfShards());
            for (int i = 0; i < firstGenerationMeta.getNumberOfShards(); i++) {
                shards.add(
                    getShardStats(
                        firstGenerationMeta,
                        i,
                        (long) Math.ceil(75.0 / firstGenerationMeta.getNumberOfShards()),
                        assignedShardNodeId
                    )
                );
            }

            mockStatsForIndex(clusterStateBeforeRollover, assignedShardNodeId, firstGenerationMeta, shards);
            assertAcked(indicesAdmin().rolloverIndex(new RolloverRequest(dataStreamName, null)).actionGet());

            ClusterState clusterStateAfterRollover = internalCluster().getCurrentMasterNodeInstance(ClusterService.class).state();
            DataStream dataStream = clusterStateAfterRollover.getMetadata().dataStreams().get(dataStreamName);
            IndexMetadata secondGenerationMeta = clusterStateAfterRollover.metadata().getIndexSafe(dataStream.getWriteIndex());

            assertThat(secondGenerationMeta.getNumberOfShards(), is(5));

            IndexMetadata index = clusterStateAfterRollover.metadata().index(firstGenerationIndex);
            Map<String, RolloverInfo> rolloverInfos = index.getRolloverInfos();
            assertThat(rolloverInfos.size(), is(1));
            List<Condition<?>> metConditions = rolloverInfos.get(dataStreamName).getMetConditions();
            assertThat(metConditions.size(), is(1));
            assertThat(metConditions.get(0).value(), instanceOf(Integer.class));
            int autoShardingRolloverInfo = (int) metConditions.get(0).value();
            assertThat(autoShardingRolloverInfo, is(5));
        }

        {
            ClusterState clusterStateBeforeRollover = internalCluster().getCurrentMasterNodeInstance(ClusterService.class).state();
            DataStream dataStreamBeforeRollover = clusterStateBeforeRollover.getMetadata().dataStreams().get(dataStreamName);
            String assignedShardNodeId = clusterStateBeforeRollover.routingTable()
                .index(dataStreamBeforeRollover.getWriteIndex())
                .shard(0)
                .primaryShard()
                .currentNodeId();

            IndexMetadata secondGenerationMeta = clusterStateBeforeRollover.metadata().index(dataStreamBeforeRollover.getIndices().get(1));
            List<ShardStats> shards = new ArrayList<>(secondGenerationMeta.getNumberOfShards());
            for (int i = 0; i < secondGenerationMeta.getNumberOfShards(); i++) {
                shards.add(
                    getShardStats(
                        secondGenerationMeta,
                        i,
                        (long) Math.ceil(100.0 / secondGenerationMeta.getNumberOfShards()),
                        assignedShardNodeId
                    )
                );
            }
            mockStatsForIndex(clusterStateBeforeRollover, assignedShardNodeId, secondGenerationMeta, shards);

            RolloverResponse response = indicesAdmin().rolloverIndex(new RolloverRequest(dataStreamName, null)).actionGet();
            assertAcked(response);
            Map<String, Boolean> conditionStatus = response.getConditionStatus();
            assertThat(conditionStatus.size(), is(0));

            ClusterState clusterStateAfterRollover = internalCluster().getCurrentMasterNodeInstance(ClusterService.class).state();
            DataStream dataStream = clusterStateAfterRollover.getMetadata().dataStreams().get(dataStreamName);
            IndexMetadata thirdGenerationMeta = clusterStateAfterRollover.metadata().getIndexSafe(dataStream.getWriteIndex());

            assertThat(thirdGenerationMeta.getNumberOfShards(), is(5));
        }

        {
            try {
                updateClusterSettings(
                    Settings.builder().put(DataStreamAutoShardingService.DATA_STREAMS_AUTO_SHARDING_INCREASE_SHARDS_COOLDOWN.getKey(), "0s")
                );

                ClusterState clusterStateBeforeRollover = internalCluster().getCurrentMasterNodeInstance(ClusterService.class).state();
                DataStream dataStreamBeforeRollover = clusterStateBeforeRollover.getMetadata().dataStreams().get(dataStreamName);
                String assignedShardNodeId = clusterStateBeforeRollover.routingTable()
                    .index(dataStreamBeforeRollover.getWriteIndex())
                    .shard(0)
                    .primaryShard()
                    .currentNodeId();

                IndexMetadata thirdGenIndex = clusterStateBeforeRollover.metadata().index(dataStreamBeforeRollover.getIndices().get(2));
                List<ShardStats> shards = new ArrayList<>(thirdGenIndex.getNumberOfShards());
                for (int i = 0; i < thirdGenIndex.getNumberOfShards(); i++) {
                    shards.add(
                        getShardStats(thirdGenIndex, i, (long) Math.ceil(100.0 / thirdGenIndex.getNumberOfShards()), assignedShardNodeId)
                    );
                }
                mockStatsForIndex(clusterStateBeforeRollover, assignedShardNodeId, thirdGenIndex, shards);

                RolloverRequest request = new RolloverRequest(dataStreamName, null);
                request.setConditions(RolloverConditions.newBuilder().addMaxIndexDocsCondition(1_000_000L).build());
                RolloverResponse response = indicesAdmin().rolloverIndex(request).actionGet();
                assertAcked(response);
                Map<String, Boolean> conditionStatus = response.getConditionStatus();
                assertThat(conditionStatus.size(), is(2));
                for (Map.Entry<String, Boolean> entry : conditionStatus.entrySet()) {
                    if (entry.getKey().equals(new MaxDocsCondition(1_000_000L).toString())) {
                        assertThat(entry.getValue(), is(false));
                    } else {
                        assertThat(entry.getKey(), is(new OptimalShardCountCondition(7).toString()));
                        assertThat(entry.getValue(), is(true));
                    }
                }

                ClusterState clusterStateAfterRollover = internalCluster().getCurrentMasterNodeInstance(ClusterService.class).state();
                DataStream dataStream = clusterStateAfterRollover.getMetadata().dataStreams().get(dataStreamName);
                IndexMetadata fourthGenerationMeta = clusterStateAfterRollover.metadata().getIndexSafe(dataStream.getWriteIndex());

                assertThat(fourthGenerationMeta.getNumberOfShards(), is(7));
            } finally {
                updateClusterSettings(
                    Settings.builder().putNull(DataStreamAutoShardingService.DATA_STREAMS_AUTO_SHARDING_INCREASE_SHARDS_COOLDOWN.getKey())
                );
            }
        }
    }

    public void testReduceShardsOnRollover() throws IOException {
        final String dataStreamName = "logs-es";

        putComposableIndexTemplate(
            "my-template",
            List.of("logs-*"),
            Settings.builder().put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 3).put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 0).build()
        );
        final var createDataStreamRequest = new CreateDataStreamAction.Request(dataStreamName);
        assertAcked(client().execute(CreateDataStreamAction.INSTANCE, createDataStreamRequest).actionGet());

        indexDocs(dataStreamName, randomIntBetween(100, 200));

        {
            ClusterState clusterStateBeforeRollover = internalCluster().getCurrentMasterNodeInstance(ClusterService.class).state();
            DataStream dataStreamBeforeRollover = clusterStateBeforeRollover.getMetadata().dataStreams().get(dataStreamName);
            String assignedShardNodeId = clusterStateBeforeRollover.routingTable()
                .index(dataStreamBeforeRollover.getWriteIndex())
                .shard(0)
                .primaryShard()
                .currentNodeId();

            Index firstGenerationIndex = clusterStateBeforeRollover.metadata().dataStreams().get(dataStreamName).getWriteIndex();
            IndexMetadata firstGenerationMeta = clusterStateBeforeRollover.getMetadata().index(firstGenerationIndex);

            List<ShardStats> shards = new ArrayList<>(firstGenerationMeta.getNumberOfShards());
            for (int i = 0; i < firstGenerationMeta.getNumberOfShards(); i++) {
                shards.add(getShardStats(firstGenerationMeta, i, i < 2 ? 1 : 0, assignedShardNodeId));
            }

            mockStatsForIndex(clusterStateBeforeRollover, assignedShardNodeId, firstGenerationMeta, shards);
            assertAcked(indicesAdmin().rolloverIndex(new RolloverRequest(dataStreamName, null)).actionGet());

            ClusterState clusterStateAfterRollover = internalCluster().getCurrentMasterNodeInstance(ClusterService.class).state();
            DataStream dataStream = clusterStateAfterRollover.getMetadata().dataStreams().get(dataStreamName);
            IndexMetadata secondGenerationMeta = clusterStateAfterRollover.metadata().getIndexSafe(dataStream.getWriteIndex());

            assertThat(secondGenerationMeta.getNumberOfShards(), is(3));
        }

        {
            try {
                updateClusterSettings(
                    Settings.builder().put(DataStreamAutoShardingService.DATA_STREAMS_AUTO_SHARDING_DECREASE_SHARDS_COOLDOWN.getKey(), "0s")
                );

                ClusterState clusterStateBeforeRollover = internalCluster().getCurrentMasterNodeInstance(ClusterService.class).state();
                DataStream dataStreamBeforeRollover = clusterStateBeforeRollover.getMetadata().dataStreams().get(dataStreamName);
                String assignedShardNodeId = clusterStateBeforeRollover.routingTable()
                    .index(dataStreamBeforeRollover.getWriteIndex())
                    .shard(0)
                    .primaryShard()
                    .currentNodeId();

                IndexMetadata secondGenerationIndex = clusterStateBeforeRollover.metadata()
                    .index(dataStreamBeforeRollover.getIndices().get(1));
                List<ShardStats> shards = new ArrayList<>(secondGenerationIndex.getNumberOfShards());
                for (int i = 0; i < secondGenerationIndex.getNumberOfShards(); i++) {
                    shards.add(getShardStats(secondGenerationIndex, i, i < 2 ? 1 : 0, assignedShardNodeId));
                }
                mockStatsForIndex(clusterStateBeforeRollover, assignedShardNodeId, secondGenerationIndex, shards);

                RolloverRequest request = new RolloverRequest(dataStreamName, null);
                request.setConditions(RolloverConditions.newBuilder().addMaxIndexDocsCondition(1_000_000L).build());
                RolloverResponse response = indicesAdmin().rolloverIndex(request).actionGet();
                assertThat(response.isRolledOver(), is(false));
                Map<String, Boolean> conditionStatus = response.getConditionStatus();
                assertThat(conditionStatus.size(), is(1));
                assertThat(conditionStatus.get(new MaxDocsCondition(1_000_000L).toString()), is(false));

                indexDocs(dataStreamName, 100);
                request = new RolloverRequest(dataStreamName, null);
                request.setConditions(RolloverConditions.newBuilder().addMaxIndexDocsCondition(1L).build());
                response = indicesAdmin().rolloverIndex(request).actionGet();
                assertThat(response.isRolledOver(), is(true));
                conditionStatus = response.getConditionStatus();
                assertThat(conditionStatus.size(), is(2));
                for (Map.Entry<String, Boolean> entry : conditionStatus.entrySet()) {
                    if (entry.getKey().equals(new MaxDocsCondition(1L).toString())) {
                        assertThat(conditionStatus.get(new MaxDocsCondition(1L).toString()), is(true));
                    } else {
                        assertThat(conditionStatus.get(new OptimalShardCountCondition(2).toString()), is(true));
                    }
                }

                ClusterState clusterStateAfterRollover = internalCluster().getCurrentMasterNodeInstance(ClusterService.class).state();
                DataStream dataStream = clusterStateAfterRollover.getMetadata().dataStreams().get(dataStreamName);
                IndexMetadata thirdGenerationMeta = clusterStateAfterRollover.metadata().getIndexSafe(dataStream.getWriteIndex());

                assertThat(thirdGenerationMeta.getNumberOfShards(), is(2));
            } finally {
                updateClusterSettings(
                    Settings.builder().putNull(DataStreamAutoShardingService.DATA_STREAMS_AUTO_SHARDING_DECREASE_SHARDS_COOLDOWN.getKey())
                );

            }

        }

    }

    public void testLazyRolloverKeepsPreviousAutoshardingDecision() throws IOException {
        final String dataStreamName = "logs-es";

        putComposableIndexTemplate(
            "my-template",
            List.of("logs-*"),
            Settings.builder().put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 3).put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 0).build()
        );
        final var createDataStreamRequest = new CreateDataStreamAction.Request(dataStreamName);
        assertAcked(client().execute(CreateDataStreamAction.INSTANCE, createDataStreamRequest).actionGet());

        indexDocs(dataStreamName, randomIntBetween(100, 200));

        {
            ClusterState clusterStateBeforeRollover = internalCluster().getCurrentMasterNodeInstance(ClusterService.class).state();
            DataStream dataStreamBeforeRollover = clusterStateBeforeRollover.getMetadata().dataStreams().get(dataStreamName);

            Index firstGenerationIndex = clusterStateBeforeRollover.metadata().dataStreams().get(dataStreamName).getWriteIndex();
            IndexMetadata firstGenerationMeta = clusterStateBeforeRollover.getMetadata().index(firstGenerationIndex);

            List<ShardStats> shards = new ArrayList<>(firstGenerationMeta.getNumberOfShards());
            String assignedShardNodeId = clusterStateBeforeRollover.routingTable()
                .index(dataStreamBeforeRollover.getWriteIndex())
                .shard(0)
                .primaryShard()
                .currentNodeId();
            for (int i = 0; i < firstGenerationMeta.getNumberOfShards(); i++) {
                shards.add(
                    getShardStats(
                        firstGenerationMeta,
                        i,
                        (long) Math.ceil(75.0 / firstGenerationMeta.getNumberOfShards()),
                        assignedShardNodeId
                    )
                );
            }

            mockStatsForIndex(clusterStateBeforeRollover, assignedShardNodeId, firstGenerationMeta, shards);
            assertAcked(indicesAdmin().rolloverIndex(new RolloverRequest(dataStreamName, null)).actionGet());

            ClusterState clusterStateAfterRollover = internalCluster().getCurrentMasterNodeInstance(ClusterService.class).state();
            DataStream dataStream = clusterStateAfterRollover.getMetadata().dataStreams().get(dataStreamName);
            IndexMetadata secondGenerationMeta = clusterStateAfterRollover.metadata().getIndexSafe(dataStream.getWriteIndex());

            assertThat(secondGenerationMeta.getNumberOfShards(), is(5));
        }

        {
            try {
                updateClusterSettings(
                    Settings.builder().put(DataStreamAutoShardingService.DATA_STREAMS_AUTO_SHARDING_INCREASE_SHARDS_COOLDOWN.getKey(), "0s")
                );

                ClusterState clusterStateBeforeRollover = internalCluster().getCurrentMasterNodeInstance(ClusterService.class).state();
                DataStream dataStreamBeforeRollover = clusterStateBeforeRollover.getMetadata().dataStreams().get(dataStreamName);

                String assignedShardNodeId = clusterStateBeforeRollover.routingTable()
                    .index(dataStreamBeforeRollover.getWriteIndex())
                    .shard(0)
                    .primaryShard()
                    .currentNodeId();
                IndexMetadata secondGenIndex = clusterStateBeforeRollover.metadata().index(dataStreamBeforeRollover.getIndices().get(1));
                List<ShardStats> shards = new ArrayList<>(secondGenIndex.getNumberOfShards());
                for (int i = 0; i < secondGenIndex.getNumberOfShards(); i++) {
                    shards.add(
                        getShardStats(secondGenIndex, i, (long) Math.ceil(100.0 / secondGenIndex.getNumberOfShards()), assignedShardNodeId)
                    );
                }

                mockStatsForIndex(clusterStateBeforeRollover, assignedShardNodeId, secondGenIndex, shards);

                RolloverRequest request = new RolloverRequest(dataStreamName, null);
                request.lazy(true);
                assertAcked(indicesAdmin().rolloverIndex(request).actionGet());

                indexDocs(dataStreamName, 10);
                ClusterState clusterStateAfterRollover = internalCluster().getCurrentMasterNodeInstance(ClusterService.class).state();
                DataStream dataStream = clusterStateAfterRollover.getMetadata().dataStreams().get(dataStreamName);
                IndexMetadata thirdGenerationIndex = clusterStateAfterRollover.metadata().getIndexSafe(dataStream.getWriteIndex());

                assertThat(thirdGenerationIndex.getNumberOfShards(), is(5));
            } finally {
                updateClusterSettings(
                    Settings.builder().putNull(DataStreamAutoShardingService.DATA_STREAMS_AUTO_SHARDING_INCREASE_SHARDS_COOLDOWN.getKey())
                );
            }
        }
    }

    private static ShardStats getShardStats(IndexMetadata indexMeta, int shardIndex, long targetWriteLoad, String assignedShardNodeId) {
        ShardId shardId = new ShardId(indexMeta.getIndex(), shardIndex);
        Path path = createTempDir().resolve("indices").resolve(indexMeta.getIndexUUID()).resolve(String.valueOf(shardIndex));
        ShardRouting shardRouting = ShardRouting.newUnassigned(
            shardId,
            true,
            RecoverySource.EmptyStoreRecoverySource.INSTANCE,
            new UnassignedInfo(UnassignedInfo.Reason.INDEX_CREATED, null),
            ShardRouting.Role.DEFAULT
        );
        shardRouting = shardRouting.initialize(assignedShardNodeId, null, ShardRouting.UNAVAILABLE_EXPECTED_SHARD_SIZE);
        shardRouting = shardRouting.moveToStarted(ShardRouting.UNAVAILABLE_EXPECTED_SHARD_SIZE);
        CommonStats stats = new CommonStats();
        stats.docs = new DocsStats(100, 0, randomByteSizeValue().getBytes());
        stats.store = new StoreStats();
        stats.indexing = new IndexingStats(new IndexingStats.Stats(1, 1, 1, 1, 1, 1, 1, 1, false, 1, targetWriteLoad, 1));
        return new ShardStats(shardRouting, new ShardPath(false, path, path, shardId), stats, null, null, null, false, 0);
    }

    static void putComposableIndexTemplate(String id, List<String> patterns, @Nullable Settings settings) throws IOException {
        TransportPutComposableIndexTemplateAction.Request request = new TransportPutComposableIndexTemplateAction.Request(id);
        request.indexTemplate(
            ComposableIndexTemplate.builder()
                .indexPatterns(patterns)
                .template(new Template(settings, null, null, null))
                .dataStreamTemplate(new ComposableIndexTemplate.DataStreamTemplate())
                .build()
        );
        client().execute(TransportPutComposableIndexTemplateAction.TYPE, request).actionGet();
    }

    static void indexDocs(String dataStream, int numDocs) {
        BulkRequest bulkRequest = new BulkRequest();
        for (int i = 0; i < numDocs; i++) {
            String value = DateFieldMapper.DEFAULT_DATE_TIME_FORMATTER.formatMillis(System.currentTimeMillis());
            bulkRequest.add(
                new IndexRequest(dataStream).opType(DocWriteRequest.OpType.CREATE)
                    .source(String.format(Locale.ROOT, "{\"%s\":\"%s\"}", DEFAULT_TIMESTAMP_FIELD, value), XContentType.JSON)
            );
        }
        BulkResponse bulkResponse = client().bulk(bulkRequest).actionGet();
        assertThat(bulkResponse.getItems().length, equalTo(numDocs));
        String backingIndexPrefix = DataStream.BACKING_INDEX_PREFIX + dataStream;
        for (BulkItemResponse itemResponse : bulkResponse) {
            assertThat(itemResponse.getFailureMessage(), nullValue());
            assertThat(itemResponse.status(), equalTo(RestStatus.CREATED));
            assertThat(itemResponse.getIndex(), startsWith(backingIndexPrefix));
        }
        indicesAdmin().refresh(new RefreshRequest(dataStream)).actionGet();
    }

    /**
     * Test plugin that registers an additional setting.
     */
    public static class TestAutoshardingPlugin extends Plugin {
        @Override
        public List<Setting<?>> getSettings() {
            return List.of(
                Setting.boolSetting(DATA_STREAMS_AUTO_SHARDING_ENABLED, false, Setting.Property.Dynamic, Setting.Property.NodeScope)
            );
        }

        @Override
        public Settings additionalSettings() {
            return Settings.builder().put(DATA_STREAMS_AUTO_SHARDING_ENABLED, true).build();
        }
    }

    private static void mockStatsForIndex(
        ClusterState clusterState,
        String assignedShardNodeId,
        IndexMetadata indexMetadata,
        List<ShardStats> shards
    ) {
        for (DiscoveryNode node : clusterState.nodes().getAllNodes()) {
            if (node.getId().equals(assignedShardNodeId)) {
                MockTransportService.getInstance(node.getName())
                    .addRequestHandlingBehavior(IndicesStatsAction.NAME + "[n]", (handler, request, channel, task) -> {
                        TransportIndicesStatsAction instance = internalCluster().getInstance(
                            TransportIndicesStatsAction.class,
                            node.getName()
                        );
                        channel.sendResponse(instance.new NodeResponse(node.getId(), indexMetadata.getNumberOfShards(), shards, List.of()));
                    });
            } else {
                MockTransportService.getInstance(node.getName())
                    .addRequestHandlingBehavior(IndicesStatsAction.NAME + "[n]", (handler, request, channel, task) -> {
                        TransportIndicesStatsAction instance = internalCluster().getInstance(
                            TransportIndicesStatsAction.class,
                            node.getName()
                        );
                        channel.sendResponse(instance.new NodeResponse(node.getId(), 0, List.of(), List.of()));
                    });
            }
        }
    }
}
