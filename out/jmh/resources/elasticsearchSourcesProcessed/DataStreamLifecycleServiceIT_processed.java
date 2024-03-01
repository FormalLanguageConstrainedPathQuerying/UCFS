/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.datastreams.lifecycle;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.IndicesRequest;
import org.elasticsearch.action.admin.cluster.settings.ClusterGetSettingsAction;
import org.elasticsearch.action.admin.indices.flush.FlushRequest;
import org.elasticsearch.action.admin.indices.forcemerge.ForceMergeAction;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.rollover.RolloverRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsAction;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.action.admin.indices.template.put.TransportPutComposableIndexTemplateAction;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.datastreams.CreateDataStreamAction;
import org.elasticsearch.action.datastreams.GetDataStreamAction;
import org.elasticsearch.action.datastreams.ModifyDataStreamsAction;
import org.elasticsearch.action.datastreams.lifecycle.ErrorEntry;
import org.elasticsearch.action.datastreams.lifecycle.ExplainIndexDataStreamLifecycle;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.broadcast.BroadcastResponse;
import org.elasticsearch.cluster.coordination.StableMasterHealthIndicatorService;
import org.elasticsearch.cluster.metadata.ComposableIndexTemplate;
import org.elasticsearch.cluster.metadata.DataStream;
import org.elasticsearch.cluster.metadata.DataStreamAction;
import org.elasticsearch.cluster.metadata.DataStreamLifecycle;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.Template;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.datastreams.DataStreamsPlugin;
import org.elasticsearch.datastreams.lifecycle.action.ExplainDataStreamLifecycleAction;
import org.elasticsearch.datastreams.lifecycle.action.PutDataStreamLifecycleAction;
import org.elasticsearch.datastreams.lifecycle.health.DataStreamLifecycleHealthIndicatorService;
import org.elasticsearch.health.Diagnosis;
import org.elasticsearch.health.GetHealthAction;
import org.elasticsearch.health.HealthIndicatorResult;
import org.elasticsearch.health.HealthStatus;
import org.elasticsearch.health.node.DataStreamLifecycleHealthInfo;
import org.elasticsearch.health.node.DslErrorInfo;
import org.elasticsearch.health.node.FetchHealthInfoCacheAction;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.MergePolicyConfig;
import org.elasticsearch.index.mapper.DateFieldMapper;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.transport.MockTransportService;
import org.elasticsearch.xcontent.XContentType;
import org.junit.After;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.elasticsearch.cluster.metadata.DataStreamTestHelper.backingIndexEqualTo;
import static org.elasticsearch.cluster.metadata.IndexMetadata.APIBlock.READ_ONLY;
import static org.elasticsearch.cluster.metadata.MetadataIndexTemplateService.DEFAULT_TIMESTAMP_FIELD;
import static org.elasticsearch.datastreams.lifecycle.DataStreamLifecycleService.DATA_STREAM_MERGE_POLICY_TARGET_FACTOR_SETTING;
import static org.elasticsearch.datastreams.lifecycle.DataStreamLifecycleService.DATA_STREAM_MERGE_POLICY_TARGET_FLOOR_SEGMENT_SETTING;
import static org.elasticsearch.datastreams.lifecycle.DataStreamLifecycleService.ONE_HUNDRED_MB;
import static org.elasticsearch.datastreams.lifecycle.DataStreamLifecycleService.TARGET_MERGE_FACTOR_VALUE;
import static org.elasticsearch.datastreams.lifecycle.health.DataStreamLifecycleHealthIndicatorService.STAGNATING_BACKING_INDICES_DIAGNOSIS_DEF;
import static org.elasticsearch.datastreams.lifecycle.health.DataStreamLifecycleHealthIndicatorService.STAGNATING_INDEX_IMPACT;
import static org.elasticsearch.index.IndexSettings.LIFECYCLE_ORIGINATION_DATE;
import static org.elasticsearch.indices.ShardLimitValidator.SETTING_CLUSTER_MAX_SHARDS_PER_NODE;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

public class DataStreamLifecycleServiceIT extends ESIntegTestCase {
    private static final Logger logger = LogManager.getLogger(DataStreamLifecycleServiceIT.class);

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return List.of(DataStreamsPlugin.class, MockTransportService.TestPlugin.class);
    }

    @Override
    protected Settings nodeSettings(int nodeOrdinal, Settings otherSettings) {
        Settings.Builder settings = Settings.builder().put(super.nodeSettings(nodeOrdinal, otherSettings));
        settings.put(DataStreamLifecycleService.DATA_STREAM_LIFECYCLE_POLL_INTERVAL, "1s");
        settings.put(DataStreamLifecycle.CLUSTER_LIFECYCLE_DEFAULT_ROLLOVER_SETTING.getKey(), "min_docs=1,max_docs=1");
        settings.put(DataStreamLifecycleService.DATA_STREAM_SIGNALLING_ERROR_RETRY_INTERVAL_SETTING.getKey(), "3");
        return settings.build();
    }

    @After
    public void cleanup() {
        updateClusterSettings(Settings.builder().putNull("*"));
    }

    public void testRolloverLifecycle() throws Exception {
        DataStreamLifecycle lifecycle = new DataStreamLifecycle();

        putComposableIndexTemplate("id1", null, List.of("metrics-foo*"), null, null, lifecycle);
        String dataStreamName = "metrics-foo";
        CreateDataStreamAction.Request createDataStreamRequest = new CreateDataStreamAction.Request(dataStreamName);
        client().execute(CreateDataStreamAction.INSTANCE, createDataStreamRequest).get();

        indexDocs(dataStreamName, 1);

        assertBusy(() -> {
            GetDataStreamAction.Request getDataStreamRequest = new GetDataStreamAction.Request(new String[] { dataStreamName });
            GetDataStreamAction.Response getDataStreamResponse = client().execute(GetDataStreamAction.INSTANCE, getDataStreamRequest)
                .actionGet();
            assertThat(getDataStreamResponse.getDataStreams().size(), equalTo(1));
            assertThat(getDataStreamResponse.getDataStreams().get(0).getDataStream().getName(), equalTo(dataStreamName));
            List<Index> backingIndices = getDataStreamResponse.getDataStreams().get(0).getDataStream().getIndices();
            assertThat(backingIndices.size(), equalTo(2));
            String backingIndex = backingIndices.get(0).getName();
            assertThat(backingIndex, backingIndexEqualTo(dataStreamName, 1));
            String writeIndex = backingIndices.get(1).getName();
            assertThat(writeIndex, backingIndexEqualTo(dataStreamName, 2));
        });
    }

    public void testRolloverAndRetention() throws Exception {
        DataStreamLifecycle lifecycle = DataStreamLifecycle.newBuilder().dataRetention(0).build();

        putComposableIndexTemplate("id1", null, List.of("metrics-foo*"), null, null, lifecycle);

        String dataStreamName = "metrics-foo";
        CreateDataStreamAction.Request createDataStreamRequest = new CreateDataStreamAction.Request(dataStreamName);
        client().execute(CreateDataStreamAction.INSTANCE, createDataStreamRequest).get();

        indexDocs(dataStreamName, 1);

        assertBusy(() -> {
            GetDataStreamAction.Request getDataStreamRequest = new GetDataStreamAction.Request(new String[] { dataStreamName });
            GetDataStreamAction.Response getDataStreamResponse = client().execute(GetDataStreamAction.INSTANCE, getDataStreamRequest)
                .actionGet();
            assertThat(getDataStreamResponse.getDataStreams().size(), equalTo(1));
            assertThat(getDataStreamResponse.getDataStreams().get(0).getDataStream().getName(), equalTo(dataStreamName));
            List<Index> backingIndices = getDataStreamResponse.getDataStreams().get(0).getDataStream().getIndices();
            assertThat(backingIndices.size(), equalTo(1));
            String writeIndex = backingIndices.get(0).getName();
            assertThat(writeIndex, backingIndexEqualTo(dataStreamName, 2));
        });
    }

    public void testOriginationDate() throws Exception {
        /*
         * In this test, we set up a datastream with 7 day retention. Then we add two indices to it -- one with an origination date 365
         * days ago, and one with an origination date 1 day ago. After data stream lifecycle runs, we expect the one with the old
         * origination date to have been deleted, and the one with the newer origination date to remain.
         */
        DataStreamLifecycle lifecycle = DataStreamLifecycle.newBuilder().dataRetention(TimeValue.timeValueDays(7)).build();

        putComposableIndexTemplate("id1", null, List.of("metrics-foo*"), null, null, lifecycle);

        String dataStreamName = "metrics-foo";
        CreateDataStreamAction.Request createDataStreamRequest = new CreateDataStreamAction.Request(dataStreamName);
        client().execute(CreateDataStreamAction.INSTANCE, createDataStreamRequest).get();

        indexDocs(dataStreamName, 1);

        String mapping = """
             {
                "properties":{
                    "@timestamp": {
                        "type": "date"
                    }
                }
            }""";
        TransportPutComposableIndexTemplateAction.Request request = new TransportPutComposableIndexTemplateAction.Request("id2");
        request.indexTemplate(
            ComposableIndexTemplate.builder()
                .indexPatterns(List.of("index_*"))
                .template(new Template(null, CompressedXContent.fromJSON(mapping), null, null))
                .build()
        );
        client().execute(TransportPutComposableIndexTemplateAction.TYPE, request).actionGet();

        String indexWithOldOriginationDate = "index_old";
        long originTimeMillis = System.currentTimeMillis() - TimeValue.timeValueDays(365).millis();
        createIndex(indexWithOldOriginationDate, Settings.builder().put(LIFECYCLE_ORIGINATION_DATE, originTimeMillis).build());
        client().execute(
            ModifyDataStreamsAction.INSTANCE,
            new ModifyDataStreamsAction.Request(List.of(DataStreamAction.addBackingIndex(dataStreamName, indexWithOldOriginationDate)))
        ).get();

        String indexWithNewOriginationDate = "index_new";
        originTimeMillis = System.currentTimeMillis() - TimeValue.timeValueDays(1).millis();
        createIndex(indexWithNewOriginationDate, Settings.builder().put(LIFECYCLE_ORIGINATION_DATE, originTimeMillis).build());
        client().execute(
            ModifyDataStreamsAction.INSTANCE,
            new ModifyDataStreamsAction.Request(List.of(DataStreamAction.addBackingIndex(dataStreamName, indexWithNewOriginationDate)))
        ).get();

        assertBusy(() -> {
            GetDataStreamAction.Request getDataStreamRequest = new GetDataStreamAction.Request(new String[] { dataStreamName });
            GetDataStreamAction.Response getDataStreamResponse = client().execute(GetDataStreamAction.INSTANCE, getDataStreamRequest)
                .actionGet();
            assertThat(getDataStreamResponse.getDataStreams().size(), equalTo(1));
            assertThat(getDataStreamResponse.getDataStreams().get(0).getDataStream().getName(), equalTo(dataStreamName));
            List<Index> backingIndices = getDataStreamResponse.getDataStreams().get(0).getDataStream().getIndices();
            Set<String> indexNames = backingIndices.stream().map(index -> index.getName()).collect(Collectors.toSet());
            assertTrue(indexNames.contains("index_new"));
            assertFalse(indexNames.contains("index_old"));
        });
    }

    public void testUpdatingLifecycleAppliesToAllBackingIndices() throws Exception {
        DataStreamLifecycle lifecycle = new DataStreamLifecycle();

        putComposableIndexTemplate("id1", null, List.of("metrics-foo*"), null, null, lifecycle);

        String dataStreamName = "metrics-foo";
        CreateDataStreamAction.Request createDataStreamRequest = new CreateDataStreamAction.Request(dataStreamName);
        client().execute(CreateDataStreamAction.INSTANCE, createDataStreamRequest).get();
        client().admin().indices().rolloverIndex(new RolloverRequest(dataStreamName, null)).actionGet();
        client().admin().indices().rolloverIndex(new RolloverRequest(dataStreamName, null)).actionGet();
        int finalGeneration = 3;

        updateLifecycle(dataStreamName, TimeValue.timeValueMillis(1));
        assertBusy(() -> {
            GetDataStreamAction.Request getDataStreamRequest = new GetDataStreamAction.Request(new String[] { dataStreamName });
            GetDataStreamAction.Response getDataStreamResponse = client().execute(GetDataStreamAction.INSTANCE, getDataStreamRequest)
                .actionGet();
            assertThat(getDataStreamResponse.getDataStreams().size(), equalTo(1));
            DataStream dataStream = getDataStreamResponse.getDataStreams().get(0).getDataStream();
            assertThat(dataStream.getName(), equalTo(dataStreamName));
            List<Index> backingIndices = dataStream.getIndices();
            assertThat(backingIndices.size(), equalTo(1));
            String writeIndex = dataStream.getWriteIndex().getName();
            assertThat(writeIndex, backingIndexEqualTo(dataStreamName, finalGeneration));
        });
    }

    public void testAutomaticForceMerge() throws Exception {
        /*
         * This test makes sure that (1) data stream lifecycle does _not_ call forcemerge on an index in the same data stream lifecycle
         * pass when it rolls over the index and that (2) it _does_ call forcemerge on an index that was rolled over in a previous data
         * stream lifecycle pass. It's harder than you would think to detect through the REST API that forcemerge has been called. The
         * reason is that segment merging happens automatically during indexing, and when forcemerge is called it likely does nothing
         * because all necessary merging has already happened automatically. So in order to detect whether forcemerge has been called, we
         * use a SendRequestBehavior in the MockTransportService to detect it.
         */
        DataStreamLifecycle lifecycle = new DataStreamLifecycle();
        disableDataStreamLifecycle();
        String dataStreamName = "metrics-foo";
        putComposableIndexTemplate(
            "id1",
            null,
            List.of(dataStreamName + "*"),
            Settings.builder()
                .put("index.number_of_replicas", 1)
                .put("index.number_of_shards", 1)
                .put(MergePolicyConfig.INDEX_MERGE_POLICY_FLOOR_SEGMENT_SETTING.getKey(), ONE_HUNDRED_MB)
                .put(MergePolicyConfig.INDEX_MERGE_POLICY_MERGE_FACTOR_SETTING.getKey(), TARGET_MERGE_FACTOR_VALUE)
                .build(),
            null,
            lifecycle
        );
        final Set<String> forceMergedIndices = new HashSet<>();
        for (DiscoveryNode node : internalCluster().getInstance(ClusterService.class, internalCluster().getMasterName())
            .state()
            .getNodes()) {
            MockTransportService.getInstance(node.getName())
                .addRequestHandlingBehavior(ForceMergeAction.NAME + "[n]", (handler, request, channel, task) -> {
                    String index = ((IndicesRequest) request).indices()[0];
                    forceMergedIndices.add(index);
                    logger.info("Force merging {}", index);
                    handler.messageReceived(request, channel, task);
                });
        }

        CreateDataStreamAction.Request createDataStreamRequest = new CreateDataStreamAction.Request(dataStreamName);
        client().execute(CreateDataStreamAction.INSTANCE, createDataStreamRequest).get();
        int finalGeneration = randomIntBetween(3, 4);
        for (int currentGeneration = 1; currentGeneration < finalGeneration; currentGeneration++) {
            final String toBeRolledOverIndex = getBackingIndices(dataStreamName).get(currentGeneration - 1);
            for (int i = 0; i < randomIntBetween(10, 50); i++) {
                indexDocs(dataStreamName, randomIntBetween(1, 300));
                BroadcastResponse flushResponse = indicesAdmin().flush(new FlushRequest(toBeRolledOverIndex)).actionGet();
                assertThat(flushResponse.getStatus(), equalTo(RestStatus.OK));
            }

            final String toBeForceMergedIndex;
            if (currentGeneration == 1) {
                toBeForceMergedIndex = null; 
            } else {
                toBeForceMergedIndex = getBackingIndices(dataStreamName).get(currentGeneration - 2);
            }
            int currentBackingIndexCount = currentGeneration;
            DataStreamLifecycleService dataStreamLifecycleService = internalCluster().getInstance(
                DataStreamLifecycleService.class,
                internalCluster().getMasterName()
            );
            ClusterService clusterService = internalCluster().getInstance(ClusterService.class, internalCluster().getMasterName());
            dataStreamLifecycleService.run(clusterService.state());
            assertBusy(() -> {
                GetDataStreamAction.Request getDataStreamRequest = new GetDataStreamAction.Request(new String[] { dataStreamName });
                GetDataStreamAction.Response getDataStreamResponse = client().execute(GetDataStreamAction.INSTANCE, getDataStreamRequest)
                    .actionGet();
                assertThat(getDataStreamResponse.getDataStreams().size(), equalTo(1));
                DataStream dataStream = getDataStreamResponse.getDataStreams().get(0).getDataStream();
                assertThat(dataStream.getName(), equalTo(dataStreamName));
                List<Index> backingIndices = dataStream.getIndices();
                assertThat(backingIndices.size(), equalTo(currentBackingIndexCount + 1));
                String writeIndex = dataStream.getWriteIndex().getName();
                assertThat(writeIndex, backingIndexEqualTo(dataStreamName, currentBackingIndexCount + 1));
                /*
                 * We only expect forcemerge to happen on the 2nd data stream lifecycle run and later, since on the first there's only the
                 *  single write index to be rolled over.
                 */
                if (currentBackingIndexCount > 1) {
                    assertThat(
                        "The segments for " + toBeForceMergedIndex + " were not merged",
                        forceMergedIndices.contains(toBeForceMergedIndex),
                        equalTo(true)
                    );
                }
                assertThat(
                    "The segments for " + toBeRolledOverIndex + " were unexpectedly merged",
                    forceMergedIndices.contains(toBeRolledOverIndex),
                    equalTo(false)
                );
            });
        }
    }

    private static void disableDataStreamLifecycle() {
        updateClusterSettings(Settings.builder().put(DataStreamLifecycleService.DATA_STREAM_LIFECYCLE_POLL_INTERVAL, TimeValue.MAX_VALUE));
    }

    public void testErrorRecordingOnRollover() throws Exception {
        DataStreamLifecycle lifecycle = new DataStreamLifecycle();
        /*
         * We set index.auto_expand_replicas to 0-1 so that if we get a single-node cluster it is not yellow. The cluster being yellow
         * could result in data stream lifecycle's automatic forcemerge failing, which would result in an unexpected error in the error
         * store.
         */
        putComposableIndexTemplate(
            "id1",
            null,
            List.of("metrics-foo*"),
            Settings.builder().put(IndexMetadata.SETTING_AUTO_EXPAND_REPLICAS, "0-1").build(),
            null,
            lifecycle
        );

        String dataStreamName = "metrics-foo";
        CreateDataStreamAction.Request createDataStreamRequest = new CreateDataStreamAction.Request(dataStreamName);
        client().execute(CreateDataStreamAction.INSTANCE, createDataStreamRequest).get();

        indexDocs(dataStreamName, 1);

        assertBusy(() -> {
            GetDataStreamAction.Request getDataStreamRequest = new GetDataStreamAction.Request(new String[] { dataStreamName });
            GetDataStreamAction.Response getDataStreamResponse = client().execute(GetDataStreamAction.INSTANCE, getDataStreamRequest)
                .actionGet();
            assertThat(getDataStreamResponse.getDataStreams().size(), equalTo(1));
            assertThat(getDataStreamResponse.getDataStreams().get(0).getDataStream().getName(), equalTo(dataStreamName));
            List<Index> backingIndices = getDataStreamResponse.getDataStreams().get(0).getDataStream().getIndices();
            assertThat(backingIndices.size(), equalTo(2));
            String backingIndex = backingIndices.get(0).getName();
            assertThat(backingIndex, backingIndexEqualTo(dataStreamName, 1));
            String writeIndex = backingIndices.get(1).getName();
            assertThat(writeIndex, backingIndexEqualTo(dataStreamName, 2));
        });

        updateClusterSettings(Settings.builder().put(SETTING_CLUSTER_MAX_SHARDS_PER_NODE.getKey(), 1));

        indexDocs(dataStreamName, 1);

        String writeIndexName = getBackingIndices(dataStreamName).get(1);
        assertBusy(() -> {
            ErrorEntry writeIndexRolloverError = null;
            Iterable<DataStreamLifecycleService> lifecycleServices = internalCluster().getInstances(DataStreamLifecycleService.class);

            for (DataStreamLifecycleService lifecycleService : lifecycleServices) {
                writeIndexRolloverError = lifecycleService.getErrorStore().getError(writeIndexName);
                if (writeIndexRolloverError != null) {
                    break;
                }
            }

            assertThat(writeIndexRolloverError, is(notNullValue()));
            assertThat(writeIndexRolloverError.error(), containsString("maximum normal shards open"));

            ExplainDataStreamLifecycleAction.Request explainRequest = new ExplainDataStreamLifecycleAction.Request(
                new String[] { dataStreamName }
            );
            ExplainDataStreamLifecycleAction.Response response = client().execute(ExplainDataStreamLifecycleAction.INSTANCE, explainRequest)
                .actionGet();
            boolean found = false;
            for (ExplainIndexDataStreamLifecycle index : response.getIndices()) {
                if (index.getError() != null && index.getError().retryCount() > 3) {
                    found = true;
                    break;
                }
            }
            assertTrue(found);
        }, 30, TimeUnit.SECONDS);

        assertBusy(() -> {
            FetchHealthInfoCacheAction.Response healthNodeResponse = client().execute(
                FetchHealthInfoCacheAction.INSTANCE,
                new FetchHealthInfoCacheAction.Request()
            ).get();
            DataStreamLifecycleHealthInfo dslHealthInfoOnHealthNode = healthNodeResponse.getHealthInfo().dslHealthInfo();
            assertThat(dslHealthInfoOnHealthNode, is(not(DataStreamLifecycleHealthInfo.NO_DSL_ERRORS)));
            assertThat(dslHealthInfoOnHealthNode.dslErrorsInfo().size(), is(1));
            DslErrorInfo errorInfo = dslHealthInfoOnHealthNode.dslErrorsInfo().get(0);

            assertThat(errorInfo.indexName(), is(writeIndexName));
            assertThat(errorInfo.retryCount(), greaterThanOrEqualTo(3));
        });

        GetHealthAction.Response healthResponse = client().execute(GetHealthAction.INSTANCE, new GetHealthAction.Request(true, 1000))
            .actionGet();
        HealthIndicatorResult masterIsStableIndicator = healthResponse.findIndicator(StableMasterHealthIndicatorService.NAME);
        if (masterIsStableIndicator.status() == HealthStatus.GREEN) {
            assertThat(healthResponse.getStatus(), is(HealthStatus.RED));
            HealthIndicatorResult dslIndicator = healthResponse.findIndicator(DataStreamLifecycleHealthIndicatorService.NAME);
            assertThat(dslIndicator.status(), is(HealthStatus.YELLOW));
            assertThat(dslIndicator.impacts(), is(STAGNATING_INDEX_IMPACT));
            assertThat(
                dslIndicator.symptom(),
                is("A backing index has repeatedly encountered errors whilst trying to advance in its lifecycle")
            );

            Diagnosis diagnosis = dslIndicator.diagnosisList().get(0);
            assertThat(diagnosis.definition(), is(STAGNATING_BACKING_INDICES_DIAGNOSIS_DEF));
            assertThat(diagnosis.affectedResources().get(0).getValues(), containsInAnyOrder(writeIndexName));
        }

        updateClusterSettings(Settings.builder().putNull("*"));

        assertBusy(() -> {
            List<String> backingIndices = getBackingIndices(dataStreamName);
            assertThat(backingIndices.size(), equalTo(3));
            String writeIndex = backingIndices.get(2);
            assertThat(writeIndex, backingIndexEqualTo(dataStreamName, 3));

            String previousWriteInddex = backingIndices.get(1);
            Iterable<DataStreamLifecycleService> lifecycleServices = internalCluster().getInstances(DataStreamLifecycleService.class);

            for (DataStreamLifecycleService lifecycleService : lifecycleServices) {
                assertThat(lifecycleService.getErrorStore().getError(previousWriteInddex), nullValue());
            }
        });

        assertBusy(() -> {
            FetchHealthInfoCacheAction.Response healthNodeResponse = client().execute(
                FetchHealthInfoCacheAction.INSTANCE,
                new FetchHealthInfoCacheAction.Request()
            ).get();
            DataStreamLifecycleHealthInfo dslHealthInfoOnHealthNode = healthNodeResponse.getHealthInfo().dslHealthInfo();
            assertThat(dslHealthInfoOnHealthNode, is(DataStreamLifecycleHealthInfo.NO_DSL_ERRORS));
        });

        healthResponse = client().execute(GetHealthAction.INSTANCE, new GetHealthAction.Request(true, 1000)).actionGet();
        masterIsStableIndicator = healthResponse.findIndicator(StableMasterHealthIndicatorService.NAME);
        if (masterIsStableIndicator.status() == HealthStatus.GREEN) {
            assertThat(healthResponse.getStatus(), is(HealthStatus.GREEN));
        }
    }

    public void testErrorRecordingOnRetention() throws Exception {
        DataStreamLifecycle lifecycle = new DataStreamLifecycle();

        /*
         * We set index.auto_expand_replicas to 0-1 so that if we get a single-node cluster it is not yellow. The cluster being yellow
         * could result in data stream lifecycle's automatic forcemerge failing, which would result in an unexpected error in the error
         * store.
         */
        putComposableIndexTemplate(
            "id1",
            null,
            List.of("metrics-foo*"),
            Settings.builder().put(IndexMetadata.SETTING_AUTO_EXPAND_REPLICAS, "0-1").build(),
            null,
            lifecycle
        );

        String dataStreamName = "metrics-foo";
        CreateDataStreamAction.Request createDataStreamRequest = new CreateDataStreamAction.Request(dataStreamName);
        client().execute(CreateDataStreamAction.INSTANCE, createDataStreamRequest).get();
        indexDocs(dataStreamName, 1);

        assertBusy(() -> {
            GetDataStreamAction.Request getDataStreamRequest = new GetDataStreamAction.Request(new String[] { dataStreamName });
            GetDataStreamAction.Response getDataStreamResponse = client().execute(GetDataStreamAction.INSTANCE, getDataStreamRequest)
                .actionGet();
            assertThat(getDataStreamResponse.getDataStreams().size(), equalTo(1));
            assertThat(getDataStreamResponse.getDataStreams().get(0).getDataStream().getName(), equalTo(dataStreamName));
            List<Index> backingIndices = getDataStreamResponse.getDataStreams().get(0).getDataStream().getIndices();
            assertThat(backingIndices.size(), equalTo(2));
            String backingIndex = backingIndices.get(0).getName();
            assertThat(backingIndex, backingIndexEqualTo(dataStreamName, 1));
            String writeIndex = backingIndices.get(1).getName();
            assertThat(writeIndex, backingIndexEqualTo(dataStreamName, 2));
        });

        List<String> dsBackingIndices = getBackingIndices(dataStreamName);
        String firstGenerationIndex = dsBackingIndices.get(0);
        String secondGenerationIndex = dsBackingIndices.get(1);

        updateIndexSettings(Settings.builder().put(READ_ONLY.settingName(), true), firstGenerationIndex);
        try {
            updateLifecycle(dataStreamName, TimeValue.timeValueSeconds(1));

            assertBusy(() -> {
                GetDataStreamAction.Request getDataStreamRequest = new GetDataStreamAction.Request(new String[] { dataStreamName });
                GetDataStreamAction.Response getDataStreamResponse = client().execute(GetDataStreamAction.INSTANCE, getDataStreamRequest)
                    .actionGet();
                assertThat(getDataStreamResponse.getDataStreams().size(), equalTo(1));
                assertThat(getDataStreamResponse.getDataStreams().get(0).getDataStream().getName(), equalTo(dataStreamName));
                List<Index> backingIndices = getDataStreamResponse.getDataStreams().get(0).getDataStream().getIndices();
                assertThat(backingIndices.size(), equalTo(2));
                String writeIndex = backingIndices.get(1).getName();
                assertThat(writeIndex, backingIndexEqualTo(dataStreamName, 2));

                ErrorEntry recordedRetentionExecutionError = null;
                Iterable<DataStreamLifecycleService> lifecycleServices = internalCluster().getInstances(DataStreamLifecycleService.class);

                for (DataStreamLifecycleService lifecycleService : lifecycleServices) {
                    recordedRetentionExecutionError = lifecycleService.getErrorStore().getError(firstGenerationIndex);
                    if (recordedRetentionExecutionError != null && recordedRetentionExecutionError.retryCount() > 3) {
                        break;
                    }
                }

                assertThat(recordedRetentionExecutionError, is(notNullValue()));
                assertThat(recordedRetentionExecutionError.error(), containsString("blocked by: [FORBIDDEN/5/index read-only (api)"));
            });

            assertBusy(() -> {
                FetchHealthInfoCacheAction.Response healthNodeResponse = client().execute(
                    FetchHealthInfoCacheAction.INSTANCE,
                    new FetchHealthInfoCacheAction.Request()
                ).get();
                DataStreamLifecycleHealthInfo dslHealthInfoOnHealthNode = healthNodeResponse.getHealthInfo().dslHealthInfo();
                assertThat(dslHealthInfoOnHealthNode, is(not(DataStreamLifecycleHealthInfo.NO_DSL_ERRORS)));
                assertThat(dslHealthInfoOnHealthNode.dslErrorsInfo().size(), is(2));
                DslErrorInfo errorInfo = dslHealthInfoOnHealthNode.dslErrorsInfo().get(0);
                assertThat(errorInfo.retryCount(), greaterThanOrEqualTo(3));
                assertThat(List.of(firstGenerationIndex, secondGenerationIndex).contains(errorInfo.indexName()), is(true));
            });

            GetHealthAction.Response healthResponse = client().execute(GetHealthAction.INSTANCE, new GetHealthAction.Request(true, 1000))
                .actionGet();
            HealthIndicatorResult masterIsStableIndicator = healthResponse.findIndicator(StableMasterHealthIndicatorService.NAME);
            if (masterIsStableIndicator.status() == HealthStatus.GREEN) {
                assertThat(healthResponse.getStatus(), is(HealthStatus.YELLOW));
                HealthIndicatorResult dslIndicator = healthResponse.findIndicator(DataStreamLifecycleHealthIndicatorService.NAME);
                assertThat(dslIndicator.status(), is(HealthStatus.YELLOW));
                assertThat(dslIndicator.impacts(), is(STAGNATING_INDEX_IMPACT));
                assertThat(
                    dslIndicator.symptom(),
                    is("2 backing indices have repeatedly encountered errors whilst trying to advance in its lifecycle")
                );

                Diagnosis diagnosis = dslIndicator.diagnosisList().get(0);
                assertThat(diagnosis.definition(), is(STAGNATING_BACKING_INDICES_DIAGNOSIS_DEF));
                assertThat(
                    diagnosis.affectedResources().get(0).getValues(),
                    containsInAnyOrder(firstGenerationIndex, secondGenerationIndex)
                );
            }

            updateIndexSettings(Settings.builder().put(READ_ONLY.settingName(), false), firstGenerationIndex);

            assertBusy(() -> {
                GetDataStreamAction.Request getDataStreamRequest = new GetDataStreamAction.Request(new String[] { dataStreamName });
                GetDataStreamAction.Response getDataStreamResponse = client().execute(GetDataStreamAction.INSTANCE, getDataStreamRequest)
                    .actionGet();
                assertThat(getDataStreamResponse.getDataStreams().size(), equalTo(1));
                assertThat(getDataStreamResponse.getDataStreams().get(0).getDataStream().getName(), equalTo(dataStreamName));
                List<Index> backingIndices = getDataStreamResponse.getDataStreams().get(0).getDataStream().getIndices();
                assertThat(backingIndices.size(), equalTo(1));

                Iterable<DataStreamLifecycleService> lifecycleServices = internalCluster().getInstances(DataStreamLifecycleService.class);
                for (DataStreamLifecycleService lifecycleService : lifecycleServices) {
                    assertThat(lifecycleService.getErrorStore().getError(firstGenerationIndex), nullValue());
                }
            });

            assertBusy(() -> {
                FetchHealthInfoCacheAction.Response healthNodeResponse = client().execute(
                    FetchHealthInfoCacheAction.INSTANCE,
                    new FetchHealthInfoCacheAction.Request()
                ).get();
                DataStreamLifecycleHealthInfo dslHealthInfoOnHealthNode = healthNodeResponse.getHealthInfo().dslHealthInfo();
                assertThat(dslHealthInfoOnHealthNode, is(DataStreamLifecycleHealthInfo.NO_DSL_ERRORS));
            });

            healthResponse = client().execute(GetHealthAction.INSTANCE, new GetHealthAction.Request(true, 1000)).actionGet();
            masterIsStableIndicator = healthResponse.findIndicator(StableMasterHealthIndicatorService.NAME);
            if (masterIsStableIndicator.status() == HealthStatus.GREEN) {
                assertThat(healthResponse.getStatus(), is(HealthStatus.GREEN));
                HealthIndicatorResult dslIndicator = healthResponse.findIndicator(DataStreamLifecycleHealthIndicatorService.NAME);
                assertThat(dslIndicator.status(), is(HealthStatus.GREEN));
                assertThat(dslIndicator.impacts().size(), is(0));
                assertThat(dslIndicator.symptom(), is("Data streams are executing their lifecycles without issues"));
                assertThat(dslIndicator.diagnosisList().size(), is(0));
            }
        } finally {
            try {
                updateIndexSettings(Settings.builder().put(READ_ONLY.settingName(), false), firstGenerationIndex);
            } catch (Exception e) {
            }
        }
    }

    public void testDataLifecycleServiceConfiguresTheMergePolicy() throws Exception {
        DataStreamLifecycle lifecycle = new DataStreamLifecycle();

        putComposableIndexTemplate(
            "id1",
            null,
            List.of("metrics-foo*"),
            Settings.builder().put(IndexMetadata.SETTING_AUTO_EXPAND_REPLICAS, "0-1").build(),
            null,
            lifecycle
        );

        String dataStreamName = "metrics-foo";
        CreateDataStreamAction.Request createDataStreamRequest = new CreateDataStreamAction.Request(dataStreamName);
        client().execute(CreateDataStreamAction.INSTANCE, createDataStreamRequest).get();
        indexDocs(dataStreamName, 1);

        assertBusy(() -> {
            GetDataStreamAction.Request getDataStreamRequest = new GetDataStreamAction.Request(new String[] { dataStreamName });
            GetDataStreamAction.Response getDataStreamResponse = client().execute(GetDataStreamAction.INSTANCE, getDataStreamRequest)
                .actionGet();
            assertThat(getDataStreamResponse.getDataStreams().size(), equalTo(1));
            assertThat(getDataStreamResponse.getDataStreams().get(0).getDataStream().getName(), equalTo(dataStreamName));
            List<Index> backingIndices = getDataStreamResponse.getDataStreams().get(0).getDataStream().getIndices();
            assertThat(backingIndices.size(), equalTo(2));
            String backingIndex = backingIndices.get(0).getName();
            assertThat(backingIndex, backingIndexEqualTo(dataStreamName, 1));
            String writeIndex = backingIndices.get(1).getName();
            assertThat(writeIndex, backingIndexEqualTo(dataStreamName, 2));
        });

        String firstGenerationIndex = getBackingIndices(dataStreamName).get(0);
        ClusterGetSettingsAction.Response response = client().execute(
            ClusterGetSettingsAction.INSTANCE,
            new ClusterGetSettingsAction.Request()
        ).get();
        Settings clusterSettings = response.persistentSettings();

        Integer targetFactor = DATA_STREAM_MERGE_POLICY_TARGET_FACTOR_SETTING.get(clusterSettings);
        ByteSizeValue targetFloor = DATA_STREAM_MERGE_POLICY_TARGET_FLOOR_SEGMENT_SETTING.get(clusterSettings);

        assertBusy(() -> {
            GetSettingsRequest getSettingsRequest = new GetSettingsRequest().indices(firstGenerationIndex).includeDefaults(true);
            GetSettingsResponse getSettingsResponse = client().execute(GetSettingsAction.INSTANCE, getSettingsRequest).actionGet();
            assertThat(
                getSettingsResponse.getSetting(firstGenerationIndex, MergePolicyConfig.INDEX_MERGE_POLICY_MERGE_FACTOR_SETTING.getKey()),
                is(targetFactor.toString())
            );
            assertThat(
                getSettingsResponse.getSetting(firstGenerationIndex, MergePolicyConfig.INDEX_MERGE_POLICY_FLOOR_SEGMENT_SETTING.getKey()),
                is(targetFloor.getStringRep())
            );
        });

        updateClusterSettings(
            Settings.builder()
                .put(DATA_STREAM_MERGE_POLICY_TARGET_FACTOR_SETTING.getKey(), 5)
                .put(DATA_STREAM_MERGE_POLICY_TARGET_FLOOR_SEGMENT_SETTING.getKey(), ByteSizeValue.ofMb(5))
        );


        indexDocs(dataStreamName, 1);

        assertBusy(() -> {
            List<String> backingIndices = getBackingIndices(dataStreamName);
            assertThat(backingIndices.size(), equalTo(3));
        });

        String secondGenerationIndex = getBackingIndices(dataStreamName).get(1);
        assertBusy(() -> {
            GetSettingsRequest getSettingsRequest = new GetSettingsRequest().indices(secondGenerationIndex).includeDefaults(true);
            GetSettingsResponse getSettingsResponse = client().execute(GetSettingsAction.INSTANCE, getSettingsRequest).actionGet();
            assertThat(
                getSettingsResponse.getSetting(secondGenerationIndex, MergePolicyConfig.INDEX_MERGE_POLICY_MERGE_FACTOR_SETTING.getKey()),
                is("5")
            );
            assertThat(
                getSettingsResponse.getSetting(secondGenerationIndex, MergePolicyConfig.INDEX_MERGE_POLICY_FLOOR_SEGMENT_SETTING.getKey()),
                is(ByteSizeValue.ofMb(5).getStringRep())
            );
        });
    }

    public void testReenableDataStreamLifecycle() throws Exception {
        DataStreamLifecycle lifecycle = new DataStreamLifecycle(null, null, false);

        putComposableIndexTemplate("id1", null, List.of("metrics-foo*"), null, null, lifecycle);
        String dataStreamName = "metrics-foo";
        CreateDataStreamAction.Request createDataStreamRequest = new CreateDataStreamAction.Request(dataStreamName);
        client().execute(CreateDataStreamAction.INSTANCE, createDataStreamRequest).get();

        indexDocs(dataStreamName, 10);
        List<String> backingIndices = getBackingIndices(dataStreamName);
        {
            String writeIndex = backingIndices.get(0);

            ExplainDataStreamLifecycleAction.Response dataStreamLifecycleExplainResponse = client().execute(
                ExplainDataStreamLifecycleAction.INSTANCE,
                new ExplainDataStreamLifecycleAction.Request(new String[] { writeIndex })
            ).actionGet();
            assertThat(dataStreamLifecycleExplainResponse.getIndices().size(), is(1));
            for (ExplainIndexDataStreamLifecycle index : dataStreamLifecycleExplainResponse.getIndices()) {
                assertThat(index.isManagedByLifecycle(), is(false));
            }
        }

        {
            assertThat(backingIndices.size(), equalTo(1));
            String writeIndex = backingIndices.get(0);
            assertThat(writeIndex, backingIndexEqualTo(dataStreamName, 1));
        }


        client().execute(
            PutDataStreamLifecycleAction.INSTANCE,
            new PutDataStreamLifecycleAction.Request(new String[] { dataStreamName }, null, true)
        );

        assertBusy(() -> {
            List<String> currentBackingIndices = getBackingIndices(dataStreamName);
            assertThat(currentBackingIndices.size(), equalTo(2));
            String backingIndex = currentBackingIndices.get(0);
            assertThat(backingIndex, backingIndexEqualTo(dataStreamName, 1));
            String writeIndex = currentBackingIndices.get(1);
            assertThat(writeIndex, backingIndexEqualTo(dataStreamName, 2));
        });
    }

    private static List<String> getBackingIndices(String dataStreamName) {
        GetDataStreamAction.Request getDataStreamRequest = new GetDataStreamAction.Request(new String[] { dataStreamName });
        GetDataStreamAction.Response getDataStreamResponse = client().execute(GetDataStreamAction.INSTANCE, getDataStreamRequest)
            .actionGet();
        assertThat(getDataStreamResponse.getDataStreams().size(), equalTo(1));
        assertThat(getDataStreamResponse.getDataStreams().get(0).getDataStream().getName(), equalTo(dataStreamName));
        return getDataStreamResponse.getDataStreams().get(0).getDataStream().getIndices().stream().map(Index::getName).toList();
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

    static void putComposableIndexTemplate(
        String id,
        @Nullable String mappings,
        List<String> patterns,
        @Nullable Settings settings,
        @Nullable Map<String, Object> metadata,
        @Nullable DataStreamLifecycle lifecycle
    ) throws IOException {
        TransportPutComposableIndexTemplateAction.Request request = new TransportPutComposableIndexTemplateAction.Request(id);
        request.indexTemplate(
            ComposableIndexTemplate.builder()
                .indexPatterns(patterns)
                .template(new Template(settings, mappings == null ? null : CompressedXContent.fromJSON(mappings), null, lifecycle))
                .metadata(metadata)
                .dataStreamTemplate(new ComposableIndexTemplate.DataStreamTemplate())
                .build()
        );
        client().execute(TransportPutComposableIndexTemplateAction.TYPE, request).actionGet();
    }

    static void updateLifecycle(String dataStreamName, TimeValue dataRetention) {
        PutDataStreamLifecycleAction.Request putDataLifecycleRequest = new PutDataStreamLifecycleAction.Request(
            new String[] { dataStreamName },
            dataRetention
        );
        assertAcked(client().execute(PutDataStreamLifecycleAction.INSTANCE, putDataLifecycleRequest));
    }
}
