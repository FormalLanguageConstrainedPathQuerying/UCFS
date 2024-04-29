/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.remotecluster;

import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.common.UUIDs;
import org.elasticsearch.core.Strings;
import org.elasticsearch.test.cluster.ElasticsearchCluster;
import org.elasticsearch.test.cluster.util.resource.Resource;
import org.elasticsearch.test.junit.RunnableTestRuleAdapter;
import org.junit.ClassRule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * Tests cross-cluster painless/execute API under RCS2.0 security model
 */
public class RemoteClusterSecurityRCS2PainlessExecuteIT extends AbstractRemoteClusterSecurityTestCase {

    private static final AtomicReference<Map<String, Object>> API_KEY_MAP_REF = new AtomicReference<>();
    private static final AtomicReference<Map<String, Object>> REST_API_KEY_MAP_REF = new AtomicReference<>();
    private static final AtomicBoolean SSL_ENABLED_REF = new AtomicBoolean();
    private static final AtomicBoolean NODE1_RCS_SERVER_ENABLED = new AtomicBoolean();
    private static final AtomicBoolean NODE2_RCS_SERVER_ENABLED = new AtomicBoolean();
    private static final AtomicInteger INVALID_SECRET_LENGTH = new AtomicInteger();

    static {
        fulfillingCluster = ElasticsearchCluster.local()
            .name("fulfilling-cluster")
            .nodes(3)
            .apply(commonClusterConfig)
            .setting("remote_cluster.port", "0")
            .setting("xpack.security.remote_cluster_server.ssl.enabled", () -> String.valueOf(SSL_ENABLED_REF.get()))
            .setting("xpack.security.remote_cluster_server.ssl.key", "remote-cluster.key")
            .setting("xpack.security.remote_cluster_server.ssl.certificate", "remote-cluster.crt")
            .setting("xpack.security.authc.token.enabled", "true")
            .keystore("xpack.security.remote_cluster_server.ssl.secure_key_passphrase", "remote-cluster-password")
            .node(0, spec -> spec.setting("remote_cluster_server.enabled", "true"))
            .node(1, spec -> spec.setting("remote_cluster_server.enabled", () -> String.valueOf(NODE1_RCS_SERVER_ENABLED.get())))
            .node(2, spec -> spec.setting("remote_cluster_server.enabled", () -> String.valueOf(NODE2_RCS_SERVER_ENABLED.get())))
            .build();

        queryCluster = ElasticsearchCluster.local()
            .name("query-cluster")
            .apply(commonClusterConfig)
            .setting("xpack.security.remote_cluster_client.ssl.enabled", () -> String.valueOf(SSL_ENABLED_REF.get()))
            .setting("xpack.security.remote_cluster_client.ssl.certificate_authorities", "remote-cluster-ca.crt")
            .setting("xpack.security.authc.token.enabled", "true")
            .keystore("cluster.remote.my_remote_cluster.credentials", () -> {
                if (API_KEY_MAP_REF.get() == null) {
                    final Map<String, Object> apiKeyMap = createCrossClusterAccessApiKey("""
                        {
                          "search": [
                            {
                                "names": ["index*"]
                            }
                          ]
                        }""");
                    API_KEY_MAP_REF.set(apiKeyMap);
                }
                return (String) API_KEY_MAP_REF.get().get("encoded");
            })
            .keystore("cluster.remote.invalid_remote.credentials", randomEncodedApiKey())
            .keystore("cluster.remote.wrong_api_key_type.credentials", () -> {
                if (REST_API_KEY_MAP_REF.get() == null) {
                    initFulfillingClusterClient();
                    final var createApiKeyRequest = new Request("POST", "/_security/api_key");
                    createApiKeyRequest.setJsonEntity("""
                        {
                          "name": "rest_api_key"
                        }""");
                    try {
                        final Response createApiKeyResponse = performRequestWithAdminUser(fulfillingClusterClient, createApiKeyRequest);
                        assertOK(createApiKeyResponse);
                        REST_API_KEY_MAP_REF.set(responseAsMap(createApiKeyResponse));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                return (String) REST_API_KEY_MAP_REF.get().get("encoded");
            })
            .keystore(
                "cluster.remote.invalid_secret_length.credentials",
                () -> Base64.getEncoder()
                    .encodeToString(
                        (UUIDs.base64UUID() + ":" + randomAlphaOfLength(INVALID_SECRET_LENGTH.get())).getBytes(StandardCharsets.UTF_8)
                    )
            )
            .rolesFile(Resource.fromClasspath("roles.yml"))
            .user(REMOTE_METRIC_USER, PASS.toString(), "read_remote_shared_metrics", false)
            .build();
    }

    @ClassRule
    public static TestRule clusterRule = RuleChain.outerRule(new RunnableTestRuleAdapter(() -> {
        SSL_ENABLED_REF.set(usually());
        NODE1_RCS_SERVER_ENABLED.set(randomBoolean());
        NODE2_RCS_SERVER_ENABLED.set(randomBoolean());
        INVALID_SECRET_LENGTH.set(randomValueOtherThan(22, () -> randomIntBetween(0, 99)));
    })).around(fulfillingCluster).around(queryCluster);

    @SuppressWarnings({ "unchecked", "checkstyle:LineLength" })
    public void testPainlessExecute() throws Exception {
        configureRemoteCluster();

        {
            final var putRoleRequest = new Request("PUT", "/_security/role/" + REMOTE_SEARCH_ROLE);
            putRoleRequest.setJsonEntity("""
                {
                  "indices": [
                    {
                      "names": ["local_index", "my_local*"],
                      "privileges": ["read"]
                    }
                  ]
                }""");
            assertOK(adminClient().performRequest(putRoleRequest));

            final var putUserRequest = new Request("PUT", "/_security/user/" + REMOTE_SEARCH_USER);
            putUserRequest.setJsonEntity("""
                {
                  "password": "x-pack-test-password",
                  "roles" : ["remote_search"]
                }""");
            assertOK(adminClient().performRequest(putUserRequest));

            final var indexDocRequest = new Request("POST", "/local_index/_doc?refresh=true");
            indexDocRequest.setJsonEntity("{\"local_foo\": \"local_bar\"}");
            assertOK(client().performRequest(indexDocRequest));

            final Request bulkRequest = new Request("POST", "/_bulk?refresh=true");
            bulkRequest.setJsonEntity(Strings.format("""
                { "index": { "_index": "index1" } }
                { "foo": "bar" }
                { "index": { "_index": "secretindex" } }
                { "bar": "foo" }
                """));
            assertOK(performRequestAgainstFulfillingCluster(bulkRequest));
        }

        {
            Request painlessExecuteLocal = createPainlessExecuteRequest("local_index");

            Response response = performRequestWithRemoteSearchUser(painlessExecuteLocal);
            assertOK(response);
            String responseBody = EntityUtils.toString(response.getEntity());
            assertThat(responseBody, equalTo("{\"result\":[\"test\"]}"));
        }
        {
            Request painlessExecuteRemote = createPainlessExecuteRequest("my_remote_cluster:index1");
            ResponseException exc = expectThrows(ResponseException.class, () -> performRequestWithRemoteSearchUser(painlessExecuteRemote));
            assertThat(exc.getResponse().getStatusLine().getStatusCode(), is(403));
            String errorResponseBody = EntityUtils.toString(exc.getResponse().getEntity());
            assertThat(errorResponseBody, containsString("unauthorized for user [remote_search_user]"));
            assertThat(errorResponseBody, containsString("\"type\":\"security_exception\""));
        }
        {
            var updateRoleRequest = new Request("PUT", "/_security/role/" + REMOTE_SEARCH_ROLE);
            updateRoleRequest.setJsonEntity("""
                {
                  "indices": [
                    {
                      "names": ["local_index", "my_local*"],
                      "privileges": ["read"]
                    }
                  ],
                  "remote_indices": [
                    {
                      "names": ["index*"],
                      "privileges": ["read", "read_cross_cluster"],
                      "clusters": ["my_remote_cluster"]
                    }
                  ]
                }""");

            assertOK(adminClient().performRequest(updateRoleRequest));
        }
        {
            Request painlessExecuteRemote = createPainlessExecuteRequest("my_remote_cluster:secretindex");
            ResponseException exc = expectThrows(ResponseException.class, () -> performRequestWithRemoteSearchUser(painlessExecuteRemote));
            String errorResponseBody = EntityUtils.toString(exc.getResponse().getEntity());
            assertThat(exc.getResponse().getStatusLine().getStatusCode(), is(403));
            assertThat(errorResponseBody, containsString("unauthorized for user [remote_search_user]"));
            assertThat(errorResponseBody, containsString("on indices [secretindex]"));
            assertThat(errorResponseBody, containsString("\"type\":\"security_exception\""));
        }
        {
            Request painlessExecuteRemote = createPainlessExecuteRequest("my_remote_cluster:index1");
            Response response = performRequestWithRemoteSearchUser(painlessExecuteRemote);
            String responseBody = EntityUtils.toString(response.getEntity());
            assertOK(response);
            assertThat(responseBody, equalTo("{\"result\":[\"test\"]}"));
        }
        {
            Request painlessExecuteLocal = createPainlessExecuteRequest("index_not_present");
            ResponseException exc = expectThrows(ResponseException.class, () -> performRequestWithRemoteSearchUser(painlessExecuteLocal));
            assertThat(exc.getResponse().getStatusLine().getStatusCode(), is(403));
            String errorResponseBody = EntityUtils.toString(exc.getResponse().getEntity());
            assertThat(errorResponseBody, containsString("unauthorized for user [remote_search_user]"));
            assertThat(errorResponseBody, containsString("on indices [index_not_present]"));
            assertThat(errorResponseBody, containsString("\"type\":\"security_exception\""));
        }
        {
            Request painlessExecuteLocal = createPainlessExecuteRequest("my_local_123");
            ResponseException exc = expectThrows(ResponseException.class, () -> performRequestWithRemoteSearchUser(painlessExecuteLocal));
            assertThat(exc.getResponse().getStatusLine().getStatusCode(), is(404));
            String errorResponseBody = EntityUtils.toString(exc.getResponse().getEntity());
            assertThat(errorResponseBody, containsString("\"type\":\"index_not_found_exception\""));
        }
        {
            Request painlessExecuteLocal = createPainlessExecuteRequest("my_local*");
            ResponseException exc = expectThrows(ResponseException.class, () -> performRequestWithRemoteSearchUser(painlessExecuteLocal));
            assertThat(exc.getResponse().getStatusLine().getStatusCode(), is(400));
            String errorResponseBody = EntityUtils.toString(exc.getResponse().getEntity());
            assertThat(errorResponseBody, containsString("indices:data/read/scripts/painless/execute does not support wildcards"));
            assertThat(errorResponseBody, containsString("\"type\":\"illegal_argument_exception\""));
        }
        {
            Request painlessExecuteRemote = createPainlessExecuteRequest("my_remote_cluster:abc123");
            ResponseException exc = expectThrows(ResponseException.class, () -> performRequestWithRemoteSearchUser(painlessExecuteRemote));
            assertThat(exc.getResponse().getStatusLine().getStatusCode(), is(403));
            String errorResponseBody = EntityUtils.toString(exc.getResponse().getEntity());
            assertThat(errorResponseBody, containsString("unauthorized for user [remote_search_user]"));
            assertThat(errorResponseBody, containsString("on indices [abc123]"));
            assertThat(errorResponseBody, containsString("\"type\":\"security_exception\""));
        }
        {
            Request painlessExecuteRemote = createPainlessExecuteRequest("my_remote_cluster:index123");
            ResponseException exc = expectThrows(ResponseException.class, () -> performRequestWithRemoteSearchUser(painlessExecuteRemote));
            assertThat(exc.getResponse().getStatusLine().getStatusCode(), is(404));
            String errorResponseBody = EntityUtils.toString(exc.getResponse().getEntity());
            assertThat(errorResponseBody, containsString("\"type\":\"index_not_found_exception\""));
        }
        {
            Request painlessExecuteRemote = createPainlessExecuteRequest("my_remote_cluster:index*");
            ResponseException exc = expectThrows(ResponseException.class, () -> performRequestWithRemoteSearchUser(painlessExecuteRemote));
            assertThat(exc.getResponse().getStatusLine().getStatusCode(), is(400));
            String errorResponseBody = EntityUtils.toString(exc.getResponse().getEntity());
            assertThat(errorResponseBody, containsString("indices:data/read/scripts/painless/execute does not support wildcards"));
            assertThat(errorResponseBody, containsString("\"type\":\"illegal_argument_exception\""));
        }
    }

    private static Request createPainlessExecuteRequest(String indexExpression) {
        Request painlessExecuteLocal = new Request("POST", "_scripts/painless/_execute");
        String body = """
            {
                "script": {
                    "source": "emit(\\"test\\")"
                },
                "context": "keyword_field",
                "context_setup": {
                    "index": "INDEX_EXPRESSION_HERE",
                    "document": {
                        "@timestamp": "2023-05-06T16:22:22.000Z"
                    }
                }
            }""".replace("INDEX_EXPRESSION_HERE", indexExpression);
        painlessExecuteLocal.setJsonEntity(body);
        return painlessExecuteLocal;
    }

    private Response performRequestWithRemoteSearchUser(final Request request) throws IOException {
        request.setOptions(
            RequestOptions.DEFAULT.toBuilder().addHeader("Authorization", headerFromRandomAuthMethod(REMOTE_SEARCH_USER, PASS))
        );
        return client().performRequest(request);
    }
}
