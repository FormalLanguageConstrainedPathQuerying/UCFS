/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.get;

import org.elasticsearch.action.ActionListenerResponseHandler;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.get.ShardMultiGetFromTranslogUtil;
import org.elasticsearch.action.get.TransportShardMultiGetFomTranslogAction;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.PlainActionFuture;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.elasticsearch.action.get.ShardMultiGetFromTranslogUtil.getFailures;
import static org.elasticsearch.action.get.ShardMultiGetFromTranslogUtil.getLocations;
import static org.elasticsearch.action.get.ShardMultiGetFromTranslogUtil.getResponses;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertNoFailures;
import static org.hamcrest.Matchers.equalTo;

public class ShardMultiGetFomTranslogActionIT extends ESIntegTestCase {

    private static final String INDEX = "test";
    private static final String ALIAS = "alias";

    public void testShardMultiGetFromTranslog() throws Exception {
        assertAcked(
            prepareCreate(INDEX).setSettings(
                Settings.builder()
                    .put("index.refresh_interval", -1)
                    .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 0)
                    .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 1)
            ).addAlias(new Alias(ALIAS).writeIndex(randomFrom(true, false, null)))
        );
        ensureGreen();

        var shardRouting = randomFrom(clusterService().state().routingTable().allShards(INDEX));
        var indicesService = internalCluster().getInstance(
            IndicesService.class,
            clusterService().state().nodes().get(shardRouting.currentNodeId()).getName()
        );
        var initialGeneration = indicesService.indexServiceSafe(shardRouting.index())
            .getShard(shardRouting.id())
            .getEngineOrNull()
            .getLastCommittedSegmentInfos()
            .getGeneration();

        var indexResponse = prepareIndex(INDEX).setId("0").setSource("field1", "value2").get();
        client().prepareGet("test", indexResponse.getId()).get();

        var mgetIds = List.of("1", "2", "3");
        var response = getFromTranslog(shardRouting, mgetIds);
        var multiGetShardResponse = response.multiGetShardResponse();
        assertThat(getLocations(multiGetShardResponse).size(), equalTo(3));
        assertThat(getFailures(multiGetShardResponse).size(), equalTo(3));
        assertTrue(getFailures(multiGetShardResponse).stream().allMatch(Objects::isNull));
        assertThat(getResponses(multiGetShardResponse).size(), equalTo(3));
        assertTrue(getResponses(multiGetShardResponse).stream().allMatch(Objects::isNull));
        assertThat(response.segmentGeneration(), equalTo(initialGeneration));

        var bulkRequest = client().prepareBulk();
        var idsToIndex = randomSubsetOf(2, mgetIds);
        for (String id : idsToIndex) {
            bulkRequest.add(new IndexRequest(INDEX).id(id).source("field1", "value1"));
        }
        bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.NONE);
        var bulkResponse = bulkRequest.get();
        assertNoFailures(bulkResponse);
        response = getFromTranslog(shardRouting, mgetIds);
        multiGetShardResponse = response.multiGetShardResponse();
        assertThat(getLocations(multiGetShardResponse).size(), equalTo(3));
        assertThat(getFailures(multiGetShardResponse).size(), equalTo(3));
        assertTrue(getFailures(multiGetShardResponse).stream().allMatch(Objects::isNull));
        var getResponses = getResponses(multiGetShardResponse);
        assertThat(getResponses.size(), equalTo(3));
        for (int location = 0; location < mgetIds.size(); location++) {
            var id = mgetIds.get(location);
            var getResponse = getResponses.get(location);
            if (idsToIndex.contains(id)) {
                assertNotNull(getResponse);
                assertThat(getResponse.getId(), equalTo(id));
                var bulkResponseForId = Arrays.stream(bulkResponse.getItems()).filter(r -> r.getId().equals(id)).toList();
                assertThat(bulkResponseForId.size(), equalTo(1));
                assertThat(getResponse.getVersion(), equalTo(bulkResponseForId.get(0).getVersion()));
            } else {
                assertNull(getResponse);
            }
        }
        assertThat(response.segmentGeneration(), equalTo(initialGeneration));
        var idToDelete = randomFrom(idsToIndex);
        client().prepareDelete(INDEX, idToDelete).get();
        response = getFromTranslog(shardRouting, idsToIndex);
        multiGetShardResponse = response.multiGetShardResponse();
        assertThat(getLocations(multiGetShardResponse).size(), equalTo(2));
        assertTrue(getFailures(multiGetShardResponse).stream().allMatch(Objects::isNull));
        getResponses = getResponses(multiGetShardResponse);
        assertThat(getResponses.size(), equalTo(2));
        assertTrue(getResponses.stream().allMatch(Objects::nonNull));
        for (var getResponse : getResponses) {
            var shouldExist = getResponse.getId().equals(idToDelete) ? false : true;
            assertThat(getResponse.isExists(), equalTo(shouldExist));
        }
        assertThat(response.segmentGeneration(), equalTo(-1L));

        indexResponse = prepareIndex(INDEX).setSource("field1", "value2").get();
        response = getFromTranslog(shardRouting, List.of(indexResponse.getId()));
        multiGetShardResponse = response.multiGetShardResponse();
        assertThat(getLocations(multiGetShardResponse).size(), equalTo(1));
        assertTrue(getFailures(multiGetShardResponse).stream().allMatch(Objects::isNull));
        getResponses = getResponses(multiGetShardResponse);
        assertThat(getResponses.size(), equalTo(1));
        assertNotNull(getResponses.get(0));
        assertThat(getResponses.get(0).getId(), equalTo(indexResponse.getId()));
        assertThat(getResponses.get(0).getVersion(), equalTo(indexResponse.getVersion()));
        assertThat(response.segmentGeneration(), equalTo(-1L));
        refresh(INDEX);
        response = getFromTranslog(shardRouting, List.of(indexResponse.getId()));
        multiGetShardResponse = response.multiGetShardResponse();
        assertThat(getLocations(multiGetShardResponse).size(), equalTo(1));
        assertTrue(getFailures(multiGetShardResponse).stream().allMatch(Objects::isNull));
        assertTrue(
            "after a refresh we should not be able to get from translog",
            getResponses(multiGetShardResponse).stream().allMatch(Objects::isNull)
        );
        assertThat(response.segmentGeneration(), equalTo(initialGeneration));
        prepareIndex(INDEX).setSource("field1", "value3").get();
        refresh(INDEX);
        refresh(INDEX);
        prepareIndex(INDEX).setSource("field1", "value4").get();
        response = getFromTranslog(shardRouting, List.of("non-existent"));
        multiGetShardResponse = response.multiGetShardResponse();
        assertThat(getLocations(multiGetShardResponse).size(), equalTo(1));
        assertThat(getFailures(multiGetShardResponse).size(), equalTo(1));
        assertNull(getFailures(multiGetShardResponse).get(0));
        assertThat(getResponses(multiGetShardResponse).size(), equalTo(1));
        assertNull(getResponses(multiGetShardResponse).get(0));
        assertThat(response.segmentGeneration(), equalTo(initialGeneration));
    }

    private TransportShardMultiGetFomTranslogAction.Response getFromTranslog(ShardRouting shardRouting, List<String> ids) throws Exception {
        var multiGetRequest = client().prepareMultiGet().addIds(indexOrAlias(), ids).request();
        var multiGetShardRequest = ShardMultiGetFromTranslogUtil.newMultiGetShardRequest(multiGetRequest, shardRouting.shardId());
        var node = clusterService().state().nodes().get(shardRouting.currentNodeId());
        assertNotNull(node);
        TransportShardMultiGetFomTranslogAction.Request request = new TransportShardMultiGetFomTranslogAction.Request(
            multiGetShardRequest,
            shardRouting.shardId()
        );
        var transportService = internalCluster().getInstance(TransportService.class);
        PlainActionFuture<TransportShardMultiGetFomTranslogAction.Response> response = new PlainActionFuture<>();
        transportService.sendRequest(
            node,
            TransportShardMultiGetFomTranslogAction.NAME,
            request,
            new ActionListenerResponseHandler<>(
                response,
                TransportShardMultiGetFomTranslogAction.Response::new,
                transportService.getThreadPool().executor(ThreadPool.Names.GET)
            )
        );
        return response.get();
    }

    private String indexOrAlias() {
        return randomBoolean() ? INDEX : ALIAS;
    }
}
