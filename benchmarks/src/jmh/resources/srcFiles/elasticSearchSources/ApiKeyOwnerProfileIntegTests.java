/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.security.profile;

import org.elasticsearch.client.internal.Client;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.InternalTestCluster;
import org.elasticsearch.test.SecurityIntegTestCase;
import org.elasticsearch.xpack.core.XPackSettings;
import org.elasticsearch.xpack.core.security.action.apikey.ApiKey;
import org.elasticsearch.xpack.core.security.action.apikey.CreateApiKeyAction;
import org.elasticsearch.xpack.core.security.action.apikey.CreateApiKeyRequest;
import org.elasticsearch.xpack.core.security.action.profile.Profile;
import org.elasticsearch.xpack.core.security.action.user.PutUserAction;
import org.elasticsearch.xpack.core.security.action.user.PutUserRequest;
import org.elasticsearch.xpack.security.authc.ApiKeyIntegTests;
import org.junit.Before;

import java.util.List;
import java.util.Map;

import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.WAIT_UNTIL;
import static org.elasticsearch.xpack.core.security.authc.support.UsernamePasswordToken.basicAuthHeaderValue;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

@ESIntegTestCase.ClusterScope(numDataNodes = 0, numClientNodes = 0, scope = ESIntegTestCase.Scope.TEST)
public class ApiKeyOwnerProfileIntegTests extends SecurityIntegTestCase {

    public static final SecureString FILE_USER_TEST_PASSWORD = new SecureString("file-user-test-password".toCharArray());
    public static final SecureString NATIVE_USER_TEST_PASSWORD = new SecureString("native-user-test-password".toCharArray());

    @Override
    protected Settings nodeSettings(int nodeOrdinal, Settings otherSettings) {
        final Settings.Builder builder = Settings.builder().put(super.nodeSettings(nodeOrdinal, otherSettings));
        builder.put(XPackSettings.API_KEY_SERVICE_ENABLED_SETTING.getKey(), true);
        builder.put(XPackSettings.TOKEN_SERVICE_ENABLED_SETTING.getKey(), true);
        return builder.build();
    }

    @Before
    public void createNativeUsers() {
        ensureGreen();
        {
            PutUserRequest putUserRequest = new PutUserRequest();
            putUserRequest.username("user_with_manage_api_key_role");
            putUserRequest.roles("manage_api_key_role");
            putUserRequest.passwordHash(getFastStoredHashAlgoForTests().hash(NATIVE_USER_TEST_PASSWORD));
            assertThat(client().execute(PutUserAction.INSTANCE, putUserRequest).actionGet().created(), is(true));
        }
        {
            PutUserRequest putUserRequest = new PutUserRequest();
            putUserRequest.username("user_with_manage_own_api_key_role");
            putUserRequest.roles("manage_own_api_key_role");
            putUserRequest.passwordHash(getFastStoredHashAlgoForTests().hash(NATIVE_USER_TEST_PASSWORD));
            assertThat(client().execute(PutUserAction.INSTANCE, putUserRequest).actionGet().created(), is(true));
        }
    }

    @Override
    public String configRoles() {
        return super.configRoles() + """
            manage_api_key_role:
              cluster: ["manage_api_key"]
            manage_own_api_key_role:
              cluster: ["manage_own_api_key"]
            """;
    }

    @Override
    public String configUsers() {
        final String usersPasswdHashed = new String(getFastStoredHashAlgoForTests().hash(FILE_USER_TEST_PASSWORD));
        return super.configUsers()
            + "user_with_manage_api_key_role:"
            + usersPasswdHashed
            + "\n"
            + "user_with_manage_own_api_key_role:"
            + usersPasswdHashed
            + "\n";
    }

    @Override
    public String configUsersRoles() {
        return super.configUsersRoles() + """
            manage_api_key_role:user_with_manage_api_key_role
            manage_own_api_key_role:user_with_manage_own_api_key_role
            """;
    }

    public void testApiKeyOwnerProfileWithoutDomain() {
        boolean ownKey = randomBoolean();
        final String username;
        if (ownKey) {
            username = "user_with_manage_own_api_key_role";
        } else {
            username = "user_with_manage_api_key_role";
        }
        SecureString password = randomFrom(FILE_USER_TEST_PASSWORD, NATIVE_USER_TEST_PASSWORD);
        Client client = client().filterWithHeader(Map.of("Authorization", basicAuthHeaderValue(username, password)));
        CreateApiKeyRequest request = new CreateApiKeyRequest("key1", null, null, null);
        request.setRefreshPolicy(randomFrom(IMMEDIATE, WAIT_UNTIL));
        boolean firstActivateProfile = randomBoolean();
        Profile userWithManageOwnProfile = null;
        if (firstActivateProfile) {
            userWithManageOwnProfile = AbstractProfileIntegTestCase.doActivateProfile(username, password);
        }
        String keyId = client.execute(CreateApiKeyAction.INSTANCE, request).actionGet().getId();
        if (false == firstActivateProfile) {
            userWithManageOwnProfile = AbstractProfileIntegTestCase.doActivateProfile(username, password);
        }
        Tuple<ApiKey, String> apiKeyWithProfileUid = ApiKeyIntegTests.getApiKeyInfoWithProfileUid(client, keyId, ownKey || randomBoolean());
        assertThat(apiKeyWithProfileUid.v1().getId(), is(keyId));
        assertThat(apiKeyWithProfileUid.v2(), is(userWithManageOwnProfile.uid()));
        assertAllKeysWithProfiles(new String[] { keyId }, new String[] { userWithManageOwnProfile.uid() });
    }

    public void testApiKeyOwnerJoinsDomain() throws Exception {
        String username = randomFrom("user_with_manage_own_api_key_role", "user_with_manage_api_key_role");
        SecureString password1;
        SecureString password2;
        if (randomBoolean()) {
            password1 = FILE_USER_TEST_PASSWORD;
            password2 = NATIVE_USER_TEST_PASSWORD;
        } else {
            password1 = NATIVE_USER_TEST_PASSWORD;
            password2 = FILE_USER_TEST_PASSWORD;
        }
        boolean firstActivateProfile = randomBoolean();
        Profile user2Profile = null;
        if (firstActivateProfile) {
            user2Profile = AbstractProfileIntegTestCase.doActivateProfile(username, password2);
        }
        Client client1 = client().filterWithHeader(Map.of("Authorization", basicAuthHeaderValue(username, password1)));
        CreateApiKeyRequest request = new CreateApiKeyRequest("key1", null, null, null);
        request.setRefreshPolicy(randomFrom(IMMEDIATE, WAIT_UNTIL));
        String keyId = client1.execute(CreateApiKeyAction.INSTANCE, request).actionGet().getId();
        if (false == firstActivateProfile) {
            user2Profile = AbstractProfileIntegTestCase.doActivateProfile(username, password2);
        }
        Tuple<ApiKey, String> apiKeyWithProfileUid = ApiKeyIntegTests.getApiKeyInfoWithProfileUid(
            client1,
            keyId,
            username.equals("user_with_manage_own_api_key_role") || randomBoolean()
        );
        assertThat(apiKeyWithProfileUid.v1().getId(), is(keyId));
        assertThat(apiKeyWithProfileUid.v2(), nullValue());
        assertAllKeysWithProfiles(new String[] { keyId }, new String[] { null });
        internalCluster().fullRestart(new InternalTestCluster.RestartCallback() {
            @Override
            public Settings onNodeStopped(String nodeName) {
                return Settings.builder().put("xpack.security.authc.domains.my_domain.realms", "file,index").build();
            }
        });
        ensureGreen();
        client1 = client().filterWithHeader(Map.of("Authorization", basicAuthHeaderValue(username, password1)));
        apiKeyWithProfileUid = ApiKeyIntegTests.getApiKeyInfoWithProfileUid(
            client1,
            keyId,
            username.equals("user_with_manage_own_api_key_role") || randomBoolean()
        );
        assertThat(apiKeyWithProfileUid.v1().getId(), is(keyId));
        assertThat(apiKeyWithProfileUid.v2(), is(user2Profile.uid()));
        assertAllKeysWithProfiles(new String[] { keyId }, new String[] { user2Profile.uid() });
    }

    public void testApiKeyOwnerLeavesDomain() throws Exception {
        internalCluster().fullRestart(new InternalTestCluster.RestartCallback() {
            @Override
            public Settings onNodeStopped(String nodeName) {
                return Settings.builder().put("xpack.security.authc.domains.file_and_index_domain.realms", "file,index").build();
            }
        });
        ensureGreen();

        String username = randomFrom("user_with_manage_own_api_key_role", "user_with_manage_api_key_role");
        SecureString password1;
        SecureString password2;
        if (randomBoolean()) {
            password1 = FILE_USER_TEST_PASSWORD;
            password2 = NATIVE_USER_TEST_PASSWORD;
        } else {
            password1 = NATIVE_USER_TEST_PASSWORD;
            password2 = FILE_USER_TEST_PASSWORD;
        }
        boolean firstActivateProfile = randomBoolean();
        Profile user2Profile = null;
        if (firstActivateProfile) {
            user2Profile = AbstractProfileIntegTestCase.doActivateProfile(username, password2);
        }
        Client client1 = client().filterWithHeader(Map.of("Authorization", basicAuthHeaderValue(username, password1)));
        CreateApiKeyRequest request = new CreateApiKeyRequest("key1", null, null, null);
        request.setRefreshPolicy(randomFrom(IMMEDIATE, WAIT_UNTIL));
        String keyId = client1.execute(CreateApiKeyAction.INSTANCE, request).actionGet().getId();
        if (false == firstActivateProfile) {
            user2Profile = AbstractProfileIntegTestCase.doActivateProfile(username, password2);
        }
        Tuple<ApiKey, String> apiKeyWithProfileUid = ApiKeyIntegTests.getApiKeyInfoWithProfileUid(
            client1,
            keyId,
            username.equals("user_with_manage_own_api_key_role") || randomBoolean()
        );
        assertThat(apiKeyWithProfileUid.v1().getId(), is(keyId));
        assertThat(apiKeyWithProfileUid.v2(), is(user2Profile.uid()));
        assertAllKeysWithProfiles(new String[] { keyId }, new String[] { user2Profile.uid() });
        internalCluster().fullRestart(new InternalTestCluster.RestartCallback() {
            @Override
            public Settings onNodeStopped(String nodeName) {
                Settings.Builder settingsBuilder = Settings.builder();
                settingsBuilder.put("xpack.security.authc.domains.file_and_index_domain.realms", (String) null);
                if (randomBoolean()) {
                    settingsBuilder.put("xpack.security.authc.domains.file_domain.realms", "file");
                }
                if (randomBoolean()) {
                    settingsBuilder.put("xpack.security.authc.domains.index_domain.realms", "index");
                }
                return settingsBuilder.build();
            }
        });
        ensureGreen();
        client1 = client().filterWithHeader(Map.of("Authorization", basicAuthHeaderValue(username, password1)));
        apiKeyWithProfileUid = ApiKeyIntegTests.getApiKeyInfoWithProfileUid(
            client1,
            keyId,
            username.equals("user_with_manage_own_api_key_role") || randomBoolean()
        );
        assertThat(apiKeyWithProfileUid.v1().getId(), is(keyId));
        assertThat(apiKeyWithProfileUid.v2(), nullValue());
        assertAllKeysWithProfiles(new String[] { keyId }, new String[] { null });
        Profile user1Profile = AbstractProfileIntegTestCase.doActivateProfile(username, password1);
        assertThat(user1Profile.uid(), not(user2Profile.uid()));
        apiKeyWithProfileUid = ApiKeyIntegTests.getApiKeyInfoWithProfileUid(
            client1,
            keyId,
            username.equals("user_with_manage_own_api_key_role") || randomBoolean()
        );
        assertThat(apiKeyWithProfileUid.v1().getId(), is(keyId));
        assertThat(apiKeyWithProfileUid.v2(), is(user1Profile.uid()));
        assertAllKeysWithProfiles(new String[] { keyId }, new String[] { user1Profile.uid() });
    }

    public void testDifferentKeyOwnersSameProfile() throws Exception {
        internalCluster().fullRestart(new InternalTestCluster.RestartCallback() {
            @Override
            public Settings onNodeStopped(String nodeName) {
                return Settings.builder().put("xpack.security.authc.domains.one_domain.realms", "file,index").build();
            }
        });
        ensureGreen();
        String username = randomFrom("user_with_manage_own_api_key_role", "user_with_manage_api_key_role");
        SecureString password1;
        SecureString password2;
        boolean user1IsFile;
        if (randomBoolean()) {
            password1 = FILE_USER_TEST_PASSWORD;
            user1IsFile = true;
            password2 = NATIVE_USER_TEST_PASSWORD;
        } else {
            password1 = NATIVE_USER_TEST_PASSWORD;
            user1IsFile = false;
            password2 = FILE_USER_TEST_PASSWORD;
        }
        Profile user1Profile = null;
        Profile user2Profile = null;
        boolean firstActivateProfile1 = randomBoolean();
        if (firstActivateProfile1) {
            user1Profile = AbstractProfileIntegTestCase.doActivateProfile(username, password1);
        }
        boolean firstActivateProfile2 = randomBoolean();
        if (firstActivateProfile2) {
            user2Profile = AbstractProfileIntegTestCase.doActivateProfile(username, password2);
        }
        Client client1 = client().filterWithHeader(Map.of("Authorization", basicAuthHeaderValue(username, password1)));
        CreateApiKeyRequest request1 = new CreateApiKeyRequest("key1", null, null, null);
        request1.setRefreshPolicy(randomFrom(IMMEDIATE, WAIT_UNTIL));
        String key1Id = client1.execute(CreateApiKeyAction.INSTANCE, request1).actionGet().getId();
        Client client2 = client().filterWithHeader(Map.of("Authorization", basicAuthHeaderValue(username, password2)));
        CreateApiKeyRequest request2 = new CreateApiKeyRequest("key2", null, null, null);
        request2.setRefreshPolicy(randomFrom(IMMEDIATE, WAIT_UNTIL));
        String key2Id = client2.execute(CreateApiKeyAction.INSTANCE, request2).actionGet().getId();
        if (false == firstActivateProfile1) {
            user1Profile = AbstractProfileIntegTestCase.doActivateProfile(username, password1);
        }
        if (false == firstActivateProfile2) {
            user2Profile = AbstractProfileIntegTestCase.doActivateProfile(username, password2);
        }
        assertThat(user1Profile.uid(), is(user2Profile.uid()));
        String profileUid = user1Profile.uid();
        Tuple<ApiKey, String> apiKeyWithProfileUid = ApiKeyIntegTests.getApiKeyInfoWithProfileUid(
            client1,
            key1Id,
            username.equals("user_with_manage_own_api_key_role") || randomBoolean()
        );
        assertThat(apiKeyWithProfileUid.v1().getId(), is(key1Id));
        if (user1IsFile) {
            assertThat(apiKeyWithProfileUid.v1().getRealm(), is("file"));
        } else {
            assertThat(apiKeyWithProfileUid.v1().getRealm(), is("index"));
        }
        assertThat(apiKeyWithProfileUid.v2(), is(profileUid));
        apiKeyWithProfileUid = ApiKeyIntegTests.getApiKeyInfoWithProfileUid(
            client2,
            key2Id,
            username.equals("user_with_manage_own_api_key_role") || randomBoolean()
        );
        assertThat(apiKeyWithProfileUid.v1().getId(), is(key2Id));
        if (user1IsFile) {
            assertThat(apiKeyWithProfileUid.v1().getRealm(), is("index"));
        } else {
            assertThat(apiKeyWithProfileUid.v1().getRealm(), is("file"));
        }
        assertThat(apiKeyWithProfileUid.v2(), is(profileUid));
        assertAllKeysWithProfiles(new String[] { key1Id, key2Id }, new String[] { profileUid, profileUid });
    }

    private void assertAllKeysWithProfiles(String[] keyIds, String[] profileUids) {
        assert keyIds.length == profileUids.length;
        Client client = client().filterWithHeader(
            Map.of(
                "Authorization",
                basicAuthHeaderValue("user_with_manage_api_key_role", randomFrom(FILE_USER_TEST_PASSWORD, NATIVE_USER_TEST_PASSWORD))
            )
        );
        List<Tuple<String, String>> allApiKeyIdsWithProfileUid = ApiKeyIntegTests.getAllApiKeyInfoWithProfileUid(client)
            .stream()
            .map(t -> new Tuple<>(t.v1().getId(), t.v2()))
            .toList();
        assertThat(allApiKeyIdsWithProfileUid, iterableWithSize(keyIds.length));
        for (int i = 0; i < keyIds.length; i++) {
            assertThat(allApiKeyIdsWithProfileUid, hasItem(new Tuple<>(keyIds[i], profileUids[i])));
        }
    }
}
