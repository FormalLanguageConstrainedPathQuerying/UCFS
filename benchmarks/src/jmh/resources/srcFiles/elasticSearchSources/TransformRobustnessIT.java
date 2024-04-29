/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.transform.integration;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.xpack.core.transform.TransformField;
import org.elasticsearch.xpack.core.transform.transforms.persistence.TransformInternalIndexConstants;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.elasticsearch.core.Strings.format;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class TransformRobustnessIT extends TransformRestTestCase {

    private static final String DANGLING_TASK_ERROR_MESSAGE =
        "Found task for transform [simple_continuous_pivot], but no configuration for it. "
            + "To delete this transform use DELETE with force=true.";

    public void testTaskRemovalAfterInternalIndexGotDeleted() throws Exception {
        String indexName = "continuous_reviews";
        createReviewsIndex(indexName);
        String transformId = "simple_continuous_pivot";
        String transformIndex = "pivot_reviews_continuous";
        final Request createTransformRequest = new Request("PUT", TransformField.REST_BASE_PATH_TRANSFORMS + transformId);
        String config = createConfig(indexName, transformIndex);
        createTransformRequest.setJsonEntity(config);
        Map<String, Object> createTransformResponse = entityAsMap(client().performRequest(createTransformRequest));
        assertThat(createTransformResponse.get("acknowledged"), equalTo(Boolean.TRUE));
        assertThat(getTransforms(null), hasSize(1));
        assertThat(getTransformTasks(), is(empty()));
        startAndWaitForContinuousTransform(transformId, transformIndex, null);
        assertTrue(indexExists(transformIndex));

        assertThat(getTransformTasks(), hasSize(1));
        assertOnePivotValue(transformIndex + "/_search?q=reviewer:user_0", 3.776978417);
        assertOnePivotValue(transformIndex + "/_search?q=reviewer:user_5", 3.72);
        assertNotNull(getTransformState(transformId));

        assertThat(getTransforms(null), hasSize(1));

        beEvilAndDeleteTheTransformIndex();
        assertThat(getTransforms(List.of(Map.of("type", "dangling_task", "reason", DANGLING_TASK_ERROR_MESSAGE))), is(empty()));
        assertThat(getTransformTasks(), hasSize(1));

        Request stopTransformRequest = new Request("POST", TransformField.REST_BASE_PATH_TRANSFORMS + transformId + "/_stop");
        ResponseException e = expectThrows(ResponseException.class, () -> client().performRequest(stopTransformRequest));

        assertThat(e.getResponse().getStatusLine().getStatusCode(), is(equalTo(409)));
        assertThat(
            e.getMessage(),
            containsString("Detected transforms with no config [" + transformId + "]. Use force to stop/delete them.")
        );
        stopTransformRequest.addParameter(TransformField.FORCE.getPreferredName(), Boolean.toString(true));

        stopTransformRequest.addParameter(TransformField.WAIT_FOR_COMPLETION.getPreferredName(), Boolean.toString(true));

        Map<String, Object> stopTransformResponse = entityAsMap(client().performRequest(stopTransformRequest));
        assertThat(stopTransformResponse.get("acknowledged"), equalTo(Boolean.TRUE));

        assertThat(getTransformTasks(), is(empty()));

        deleteTransform(transformId);
    }

    public void testBatchTransformLifecycltInALoop() throws IOException {
        createReviewsIndex();

        String transformId = "test_batch_lifecycle_in_a_loop";
        String destIndex = transformId + "-dest";
        for (int i = 0; i < 100; ++i) {
            try {
                createPivotReviewsTransform(transformId, destIndex, null);
                assertThat(getTransformTasks(), is(empty()));
                assertThat(getTransformTasksFromClusterState(transformId), is(empty()));

                startAndWaitForTransform(transformId, destIndex);

                assertThat(getTransformTasks(), is(empty()));
                assertThat(getTransformTasksFromClusterState(transformId), is(empty()));

                deleteTransform(transformId);
            } catch (AssertionError | Exception e) {
                throw new AssertionError(format("Failure at iteration %d: %s", i, e.getMessage()), e);
            }
        }
    }

    public void testInterruptedBatchTransformLifecycltInALoop() throws IOException {
        createReviewsIndex();

        String transformId = "test_interrupted_batch_lifecycle_in_a_loop";
        String destIndex = transformId + "-dest";
        for (int i = 0; i < 100; ++i) {
            long sleepAfterStartMillis = randomLongBetween(0, 1_000);
            boolean force = randomBoolean();
            try {
                createPivotReviewsTransform(transformId, destIndex, null);
                assertThat(getTransformTasks(), is(empty()));
                assertThat(getTransformTasksFromClusterState(transformId), is(empty()));

                startTransform(transformId);
                assertThat(getTransformTasks(), hasSize(1));
                assertThat(getTransformTasksFromClusterState(transformId), hasSize(1));

                Thread.sleep(sleepAfterStartMillis);

                stopTransform(transformId, force);
                if (force) {
                    assertBusy(() -> assertThat(getTransformTasks(), is(empty())));
                } else {
                    assertThat(getTransformTasks(), is(empty()));
                }
                assertThat(getTransformTasksFromClusterState(transformId), is(empty()));

                deleteTransform(transformId);
            } catch (AssertionError | Exception e) {
                throw new AssertionError(
                    format("Failure at iteration %d (sleepAfterStart=%sms,force=%s): %s", i, sleepAfterStartMillis, force, e.getMessage()),
                    e
                );
            }
        }
    }

    public void testContinuousTransformLifecycleInALoop() throws Exception {
        createReviewsIndex();

        String transformId = "test_cont_lifecycle_in_a_loop";
        String destIndex = transformId + "-dest";
        for (int i = 0; i < 100; ++i) {
            long sleepAfterStartMillis = randomLongBetween(0, 5_000);
            boolean force = randomBoolean();
            try {
                createContinuousPivotReviewsTransform(transformId, destIndex, null);
                assertThat(getTransformTasks(), is(empty()));
                assertThat(getTransformTasksFromClusterState(transformId), is(empty()));

                startTransform(transformId);
                assertThat(getTransformTasks(), hasSize(1));
                assertThat(getTransformTasksFromClusterState(transformId), hasSize(1));

                Thread.sleep(sleepAfterStartMillis);
                assertThat(getTransformTasks(), hasSize(1));
                assertThat(getTransformTasksFromClusterState(transformId), hasSize(1));

                stopTransform(transformId, force);
                if (force) {
                    assertBusy(() -> assertThat(getTransformTasks(), is(empty())));
                } else {
                    assertThat(getTransformTasks(), is(empty()));
                }
                assertThat(getTransformTasksFromClusterState(transformId), is(empty()));

                deleteTransform(transformId);
            } catch (AssertionError | Exception e) {
                throw new AssertionError(
                    format("Failure at iteration %d (sleepAfterStart=%sms,force=%s): %s", i, sleepAfterStartMillis, force, e.getMessage()),
                    e
                );
            }
        }
    }

    public void testCancellingTransformTask() throws Exception {
        createReviewsIndex();
        String transformId = "cancelling_transform_task";
        String transformIndex = transformId + "-dest";

        Request createTransformRequest = new Request("PUT", TransformField.REST_BASE_PATH_TRANSFORMS + transformId);
        createTransformRequest.setJsonEntity(createConfig(REVIEWS_INDEX_NAME, transformIndex));
        assertAcknowledged(client().performRequest(createTransformRequest));
        assertThat(getTransforms(null), hasSize(1));

        assertThat(getTransformTasks(), is(empty()));

        startTransform(transformId);

        List<String> tasks = getTransformTasks();
        assertThat(tasks, hasSize(1));

        Thread.sleep(randomLongBetween(0, 5_000));

        Request cancelTaskRequest = new Request("POST", "/_tasks/" + tasks.get(0) + "/_cancel");
        assertOK(client().performRequest(cancelTaskRequest));

        assertBusy(() -> {
            Map<?, ?> transformStatsAsMap = getTransformStateAndStats(transformId);
            assertEquals("Stats were: " + transformStatsAsMap, "stopped", XContentMapValues.extractValue("state", transformStatsAsMap));
        }, 30, TimeUnit.SECONDS);

        assertThat(getTransforms(null), hasSize(1));

        assertThat(getTransformTasks(), is(empty()));
    }

    private void beEvilAndDeleteTheTransformIndex() throws IOException {
        final Request deleteRequest = new Request("DELETE", TransformInternalIndexConstants.LATEST_INDEX_NAME);
        deleteRequest.setOptions(
            expectWarnings(
                "this request accesses system indices: ["
                    + TransformInternalIndexConstants.LATEST_INDEX_NAME
                    + "], but in a future major version, direct access to system indices will "
                    + "be prevented by default"
            )
        );
        adminClient().performRequest(deleteRequest);
    }

    private static String createConfig(String sourceIndex, String destIndex) {
        return format("""
            {
              "source": {
                "index": "%s"
              },
              "dest": {
                "index": "%s"
              },
              "frequency": "1s",
              "sync": {
                "time": {
                  "field": "timestamp",
                  "delay": "1s"
                }
              },
              "pivot": {
                "group_by": {
                  "reviewer": {
                    "terms": {
                      "field": "user_id"
                    }
                  }
                },
                "aggregations": {
                  "avg_rating": {
                    "avg": {
                      "field": "stars"
                    }
                  }
                }
              }
            }""", sourceIndex, destIndex);
    }
}
