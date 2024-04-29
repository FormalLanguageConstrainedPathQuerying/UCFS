/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.ml.integration;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.analysis.common.CommonAnalysisPlugin;
import org.elasticsearch.cluster.routing.allocation.DiskThresholdSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.datastreams.DataStreamsPlugin;
import org.elasticsearch.index.mapper.extras.MapperExtrasPlugin;
import org.elasticsearch.index.query.MatchPhraseQueryBuilder;
import org.elasticsearch.ingest.common.IngestCommonPlugin;
import org.elasticsearch.license.LicenseSettings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.reindex.ReindexPlugin;
import org.elasticsearch.test.AbstractMultiClustersTestCase;
import org.elasticsearch.test.disruption.NetworkDisruption;
import org.elasticsearch.xpack.core.XPackSettings;
import org.elasticsearch.xpack.core.ilm.LifecycleSettings;
import org.elasticsearch.xpack.core.ml.MachineLearningField;
import org.elasticsearch.xpack.core.ml.action.CloseJobAction;
import org.elasticsearch.xpack.core.ml.action.GetJobsStatsAction;
import org.elasticsearch.xpack.core.ml.action.GetJobsStatsAction.Response.JobStats;
import org.elasticsearch.xpack.core.ml.action.OpenJobAction;
import org.elasticsearch.xpack.core.ml.action.PutDatafeedAction;
import org.elasticsearch.xpack.core.ml.action.PutJobAction;
import org.elasticsearch.xpack.core.ml.action.StartDatafeedAction;
import org.elasticsearch.xpack.core.ml.action.StopDatafeedAction;
import org.elasticsearch.xpack.core.ml.datafeed.ChunkingConfig;
import org.elasticsearch.xpack.core.ml.datafeed.DatafeedConfig;
import org.elasticsearch.xpack.core.ml.job.config.Job;
import org.elasticsearch.xpack.core.ml.job.config.JobState;
import org.elasticsearch.xpack.ilm.IndexLifecycle;
import org.elasticsearch.xpack.ml.LocalStateMachineLearning;
import org.elasticsearch.xpack.ml.support.BaseMlIntegTestCase;
import org.elasticsearch.xpack.shutdown.ShutdownPlugin;
import org.elasticsearch.xpack.wildcard.Wildcard;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.is;

public class DatafeedCcsIT extends AbstractMultiClustersTestCase {

    private static final String REMOTE_CLUSTER = "remote_cluster";
    private static final String DATA_INDEX = "data";

    @Override
    protected Settings nodeSettings() {
        return Settings.builder()
            .put(LicenseSettings.SELF_GENERATED_LICENSE_TYPE.getKey(), "trial")
            .put(MachineLearningField.AUTODETECT_PROCESS.getKey(), false)
            .put(XPackSettings.SECURITY_ENABLED.getKey(), false)
            .put(XPackSettings.WATCHER_ENABLED.getKey(), false)
            .put(XPackSettings.GRAPH_ENABLED.getKey(), false)
            .put(LifecycleSettings.LIFECYCLE_HISTORY_INDEX_ENABLED_SETTING.getKey(), false)
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_WATERMARK_SETTING.getKey(), "1b")
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_WATERMARK_SETTING.getKey(), "1b")
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_DISK_FLOOD_STAGE_WATERMARK_SETTING.getKey(), "1b")
            .build();
    }

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins(String clusterAlias) {
        return List.of(
            LocalStateMachineLearning.class,
            CommonAnalysisPlugin.class,
            IngestCommonPlugin.class,
            ReindexPlugin.class,
            ShutdownPlugin.class,
            BaseMlIntegTestCase.MockPainlessScriptEngine.TestPlugin.class,
            IndexLifecycle.class,
            DataStreamsPlugin.class,
            MapperExtrasPlugin.class,
            Wildcard.class
        );
    }

    @Override
    protected Collection<String> remoteClusterAlias() {
        return List.of(REMOTE_CLUSTER);
    }

    @Override
    protected boolean reuseClusters() {
        return false;
    }

    public void testDatafeedWithCcsRemoteHealthy() throws Exception {
        setSkipUnavailable(randomBoolean());
        String jobId = "ccs-healthy-job";
        String datafeedId = jobId;
        long numDocs = randomIntBetween(32, 2048);
        long endTimeMs = indexRemoteDocs(numDocs);
        setupJobAndDatafeed(jobId, datafeedId, endTimeMs);
        try {
            assertBusy(() -> {
                JobStats jobStats = getJobStats(jobId);
                assertThat(jobStats.getState(), is(JobState.CLOSED));
                assertThat(jobStats.getDataCounts().getProcessedRecordCount(), is(numDocs));
            }, 3, TimeUnit.MINUTES);
        } catch (AssertionError ae) {
            try {
                client(LOCAL_CLUSTER).execute(CloseJobAction.INSTANCE, new CloseJobAction.Request(jobId).setForce(true)).actionGet();
            } catch (Exception e) {
                ae.addSuppressed(e);
            }
            throw ae;
        } finally {
            clearSkipUnavailable();
        }
    }

    @AwaitsFix(bugUrl = "https:
    public void testDatafeedWithCcsRemoteUnavailable() throws Exception {
        setSkipUnavailable(randomBoolean());
        String jobId = "ccs-unavailable-job";
        String datafeedId = jobId;
        long numDocs = randomIntBetween(32, 2048);
        indexRemoteDocs(numDocs);
        setupJobAndDatafeed(jobId, datafeedId, null);
        try {
            NetworkDisruption networkDisruption = new NetworkDisruption(
                new NetworkDisruption.IsolateAllNodes(Set.of(cluster(REMOTE_CLUSTER).getNodeNames())),
                NetworkDisruption.DISCONNECT
            );
            cluster(REMOTE_CLUSTER).setDisruptionScheme(networkDisruption);
            networkDisruption.startDisrupting();
            assertBusy(() -> {
                if (doesLocalAuditMessageExist("Datafeed is encountering errors extracting data") == false) {
                    JobStats jobStats = getJobStats(jobId);
                    assertThat(jobStats.getDataCounts().getProcessedRecordCount(), is(numDocs));
                }
            });
            networkDisruption.removeAndEnsureHealthy(cluster(REMOTE_CLUSTER));
            assertBusy(() -> {
                JobStats jobStats = getJobStats(jobId);
                assertThat(jobStats.getState(), is(JobState.OPENED));
                assertThat(jobStats.getDataCounts().getProcessedRecordCount(), is(numDocs));
            }, 3, TimeUnit.MINUTES);
        } finally {
            client(LOCAL_CLUSTER).execute(StopDatafeedAction.INSTANCE, new StopDatafeedAction.Request(datafeedId)).actionGet();
            client(LOCAL_CLUSTER).execute(CloseJobAction.INSTANCE, new CloseJobAction.Request(jobId)).actionGet();
            clearSkipUnavailable();
        }
    }

    /**
     * Index some datafeed data into the remote cluster.
     * @return The epoch millisecond timestamp of the most recent document.
     */
    private long indexRemoteDocs(long numDocs) {
        client(REMOTE_CLUSTER).admin().indices().prepareCreate(DATA_INDEX).setMapping("time", "type=date").get();
        long now = System.currentTimeMillis();
        long weekAgo = now - 604800000;
        long twoWeeksAgo = weekAgo - 604800000;
        BaseMlIntegTestCase.indexDocs(client(REMOTE_CLUSTER), logger, DATA_INDEX, numDocs, twoWeeksAgo, weekAgo);
        return weekAgo;
    }

    private boolean doesLocalAuditMessageExist(String message) {
        try {
            SearchResponse response = client(LOCAL_CLUSTER).prepareSearch(".ml-notifications*")
                .setQuery(new MatchPhraseQueryBuilder("message", message))
                .get();
            try {
                return response.getHits().getTotalHits().value > 0;
            } finally {
                response.decRef();
            }
        } catch (ElasticsearchException e) {
            return false;
        }
    }

    private JobStats getJobStats(String jobId) {
        return client(LOCAL_CLUSTER).execute(GetJobsStatsAction.INSTANCE, new GetJobsStatsAction.Request(jobId))
            .actionGet()
            .getResponse()
            .results()
            .get(0);
    }

    /**
     * Create and start a job and datafeed on the local cluster but searching for data in the remote cluster.
     */
    private void setupJobAndDatafeed(String jobId, String datafeedId, Long endTimeMs) throws Exception {
        Job.Builder job = BaseMlIntegTestCase.createScheduledJob(jobId, ByteSizeValue.ofMb(20));
        client(LOCAL_CLUSTER).execute(PutJobAction.INSTANCE, new PutJobAction.Request(job)).actionGet();

        DatafeedConfig.Builder config = BaseMlIntegTestCase.createDatafeedBuilder(
            datafeedId,
            job.getId(),
            List.of(REMOTE_CLUSTER + ":" + DATA_INDEX)
        );
        config.setChunkingConfig(ChunkingConfig.newManual(TimeValue.timeValueMinutes(10)));
        client(LOCAL_CLUSTER).execute(PutDatafeedAction.INSTANCE, new PutDatafeedAction.Request(config.build())).actionGet();

        client(LOCAL_CLUSTER).execute(OpenJobAction.INSTANCE, new OpenJobAction.Request(job.getId()));
        assertBusy(() -> {
            GetJobsStatsAction.Response statsResponse = client(LOCAL_CLUSTER).execute(
                GetJobsStatsAction.INSTANCE,
                new GetJobsStatsAction.Request(job.getId())
            ).actionGet();
            assertThat(statsResponse.getResponse().results().get(0).getState(), is(JobState.OPENED));
        }, 30, TimeUnit.SECONDS);

        StartDatafeedAction.DatafeedParams datafeedParams = new StartDatafeedAction.DatafeedParams(config.getId(), 0L);
        datafeedParams.setEndTime(endTimeMs);
        client(LOCAL_CLUSTER).execute(StartDatafeedAction.INSTANCE, new StartDatafeedAction.Request(datafeedParams)).actionGet();
    }

    private void setSkipUnavailable(boolean skip) {
        client(LOCAL_CLUSTER).admin()
            .cluster()
            .prepareUpdateSettings()
            .setPersistentSettings(Settings.builder().put("cluster.remote." + REMOTE_CLUSTER + ".skip_unavailable", skip).build())
            .get();
    }

    private void clearSkipUnavailable() {
        client(LOCAL_CLUSTER).admin()
            .cluster()
            .prepareUpdateSettings()
            .setPersistentSettings(Settings.builder().putNull("cluster.remote." + REMOTE_CLUSTER + ".skip_unavailable").build())
            .get();
    }
}
