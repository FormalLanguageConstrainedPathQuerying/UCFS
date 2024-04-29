/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.upgrades;

import com.carrotsearch.randomizedtesting.annotations.ParametersFactory;
import com.carrotsearch.randomizedtesting.annotations.TimeoutSuite;

import org.apache.lucene.tests.util.TimeUnits;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.test.rest.ESRestTestCase;
import org.elasticsearch.test.rest.yaml.ClientYamlTestCandidate;
import org.elasticsearch.test.rest.yaml.ESClientYamlSuiteTestCase;
import org.elasticsearch.xpack.test.rest.XPackRestTestConstants;
import org.elasticsearch.xpack.test.rest.XPackRestTestHelper;
import org.junit.Before;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.elasticsearch.test.rest.RestTestLegacyFeatures.COMPONENT_TEMPLATE_SUPPORTED;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@TimeoutSuite(millis = 5 * TimeUnits.MINUTE) 
public class UpgradeClusterClientYamlTestSuiteIT extends ESClientYamlSuiteTestCase {

    /**
     * Waits for the Machine Learning templates to be created by {@link org.elasticsearch.plugins.MetadataUpgrader}.
     * Only do this on the old cluster.  Users won't necessarily wait for templates to be upgraded during rolling
     * upgrades, so we cannot wait within the test framework, or we could miss production bugs.
     */
    @Before
    public void waitForTemplates() throws Exception {
        if (AbstractUpgradeTestCase.CLUSTER_TYPE == AbstractUpgradeTestCase.ClusterType.OLD) {
            try {
                boolean clusterUnderstandsComposableTemplates = clusterHasFeature(COMPONENT_TEMPLATE_SUPPORTED);
                XPackRestTestHelper.waitForTemplates(
                    client(),
                    XPackRestTestConstants.ML_POST_V7120_TEMPLATES,
                    clusterUnderstandsComposableTemplates
                );
            } catch (AssertionError e) {
                throw new AssertionError("Failure in test setup: Failed to initialize ML index templates", e);
            }
        }
    }

    @Before
    public void waitForWatcher() throws Exception {
        try {
            assertBusy(() -> {
                Response response = client().performRequest(new Request("GET", "_watcher/stats"));
                Map<String, Object> responseBody = entityAsMap(response);
                List<?> stats = (List<?>) responseBody.get("stats");
                assertThat(stats.size(), greaterThanOrEqualTo(3));
                for (Object stat : stats) {
                    Map<?, ?> statAsMap = (Map<?, ?>) stat;
                    assertThat(statAsMap.get("watcher_state"), equalTo("started"));
                }
            }, 1, TimeUnit.MINUTES);
        } catch (AssertionError e) {
            throw new AssertionError("Failure in test setup: Failed to initialize at least 3 watcher nodes", e);
        }
    }

    @Override
    protected boolean resetFeatureStates() {
        return false;
    }

    @Override
    protected boolean preserveIndicesUponCompletion() {
        return true;
    }

    @Override
    protected boolean preserveTemplatesUponCompletion() {
        return true;
    }

    @Override
    protected boolean preserveRollupJobsUponCompletion() {
        return true;
    }

    @Override
    protected boolean preserveILMPoliciesUponCompletion() {
        return true;
    }

    @Override
    protected boolean preserveDataStreamsUponCompletion() {
        return true;
    }

    @Override
    protected boolean preserveReposUponCompletion() {
        return true;
    }

    @Override
    protected boolean preserveSnapshotsUponCompletion() {
        return true;
    }

    @Override
    protected boolean preserveSearchableSnapshotsIndicesUponCompletion() {
        return true;
    }

    public UpgradeClusterClientYamlTestSuiteIT(ClientYamlTestCandidate testCandidate) {
        super(testCandidate);
    }

    @ParametersFactory
    public static Iterable<Object[]> parameters() throws Exception {
        return createParameters();
    }

    @Override
    protected Settings restClientSettings() {
        String token = "Basic " + Base64.getEncoder().encodeToString(("test_user:x-pack-test-password").getBytes(StandardCharsets.UTF_8));
        return Settings.builder()
            .put(ThreadContext.PREFIX + ".Authorization", token)
            .put(ESRestTestCase.CLIENT_SOCKET_TIMEOUT, "90s")
            .build();
    }
}
