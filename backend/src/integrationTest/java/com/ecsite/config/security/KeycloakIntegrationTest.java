package com.ecsite.config.security;

import com.ecsite.TestcontainersConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IdP の設定の統合テスト（T3208、plan 003 第 2 節）。<br />
 * Keycloak を Testcontainers で起動して、両 realm の JSON（{@code keycloak/realms/}）を取り込み、
 * Keycloak が発行した token で管理 API を呼ぶ。バックエンドは本番と同じく、issuer の OpenID Connect の設定から公開鍵を取る
 * （テストの鍵に差し替える {@code TestSecurityConfig} は使わないので、{@code @IntegrationTest} とは別の設定で起動する）。<br />
 * token は、パスワードで取る（Direct Access Grant）。realm の JSON では web の client でこれを無効にしているので、
 * テストの中で有効にする（{@code scripts/dev/keycloak.sh} と同じ）。画面と同じ client の token（audience・scope の対応）で確かめるため。
 * 期限切れの token の拒否は、検証の処理が同じなので、テストの鍵の token で確かめる（AuthorizationTest）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, KeycloakIntegrationTest.KeycloakConfig.class})
@ActiveProfiles("integration-test")
@Transactional
class KeycloakIntegrationTest {

    /**
     * compose.yaml と同じイメージ
     */
    private static final String IMAGE = "quay.io/keycloak/keycloak:26.8.0";

    private static final int HTTP_PORT = 8080;
    private static final int MANAGEMENT_PORT = 9000;

    /**
     * realm の設定とテーマの置き場所。Gradle の integrationTest task が設定する
     */
    private static final Path REALMS_DIR = Path.of(System.getProperty("keycloak.realms.dir", "../keycloak/realms"));
    private static final Path THEME_DIR = Path.of(System.getProperty("keycloak.theme.dir", "../keycloak/themes/ec-site"));

    private static final String TAX_CATEGORIES = "/api/admin/tax-categories";

    /**
     * テストのクラスで共有する Keycloak（起動に時間がかかるので 1 つだけ）
     */
    private static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(DockerImageName.parse(IMAGE))
            .withCommand("start-dev", "--import-realm")
            .withEnv(Map.of(
                    "KC_BOOTSTRAP_ADMIN_USERNAME", "admin",
                    "KC_BOOTSTRAP_ADMIN_PASSWORD", "admin",
                    "KC_HEALTH_ENABLED", "true"))
            .withCopyFileToContainer(MountableFile.forHostPath(REALMS_DIR), "/opt/keycloak/data/import")
            .withCopyFileToContainer(MountableFile.forHostPath(THEME_DIR), "/opt/keycloak/themes/ec-site")
            .withExposedPorts(HTTP_PORT, MANAGEMENT_PORT)
            .waitingFor(Wait.forHttp("/health/ready").forPort(MANAGEMENT_PORT).withStartupTimeout(Duration.ofMinutes(3)));

    @Autowired
    MockMvc mockMvc;

    @BeforeAll
    static void enableDirectAccessGrants() {
        KEYCLOAK.start();
        enableDirectAccessGrant("ec-admin", "ec-admin-web");
        enableDirectAccessGrant("ec-store", "ec-store-web");
    }

    @Nested
    @DisplayName("AC-136、139 運営者の token で、管理 API を職務どおりに使える")
    class Operator {

        @Test
        @DisplayName("AC-139 管理者は参照も更新もできる")
        void administratorCanReadAndWrite() throws Exception {
            var token = adminToken("admin@example.com", "admin-pass-1");

            mockMvc.perform(withToken(get(TAX_CATEGORIES), token))
                    .andExpect(status().isOk());
            mockMvc.perform(withToken(post(TAX_CATEGORIES), token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"Keycloak の確認\"}"))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("AC-139 閲覧者は参照だけで、更新は 403")
        void viewerCanOnlyRead() throws Exception {
            var token = adminToken("viewer@example.com", "viewer-pass-1");

            mockMvc.perform(withToken(get(TAX_CATEGORIES), token))
                    .andExpect(status().isOk());
            mockMvc.perform(withToken(post(TAX_CATEGORIES), token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"閲覧者の更新\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("AC-137、138 受け付けない token")
    class Rejected {

        @Test
        @DisplayName("AC-138 会員の token では、管理 API は 401")
        void memberTokenIsRejected() throws Exception {
            var token = storeToken("member@example.com", "member-pass-1");

            mockMvc.perform(withToken(get(TAX_CATEGORIES), token))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-137 署名を書き換えた token は 401")
        void tamperedTokenIsRejected() throws Exception {
            var token = adminToken("admin@example.com", "admin-pass-1");
            var parts = token.split("\\.");
            var signature = Base64.getUrlDecoder().decode(parts[2]);
            signature[0] ^= 0x01;
            var tampered = parts[0] + "." + parts[1] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

            mockMvc.perform(withToken(get(TAX_CATEGORIES), tampered))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("realm の設定")
    class RealmSettings {

        @Test
        @DisplayName("AC-133 会員と運営者のアカウントは別の realm にあり、ほかの realm ではログインできない")
        void accountsAreSeparatedByRealm() {
            assertThatThrownBy(() -> adminToken("member@example.com", "member-pass-1"))
                    .hasMessageContaining("invalid_grant");
            assertThatThrownBy(() -> storeToken("admin@example.com", "admin-pass-1"))
                    .hasMessageContaining("invalid_grant");
        }

        @Test
        @DisplayName("AC-140 access token の有効期間は 5 分")
        void accessTokenLifespanIsFiveMinutes() {
            for (var token : List.of(adminToken("admin@example.com", "admin-pass-1"), storeToken("member@example.com", "member-pass-1"))) {
                var payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
                long issuedAt = ((Number) JsonPath.read(payload, "$.iat")).longValue();
                long expiresAt = ((Number) JsonPath.read(payload, "$.exp")).longValue();

                assertThat(expiresAt - issuedAt).isEqualTo(300);
            }
        }

        @Test
        @DisplayName("運営者の token の権限は、職務から展開された管理 API の client role だけ")
        void operatorTokenCarriesPermissions() {
            var payload = new String(Base64.getUrlDecoder().decode(adminToken("viewer@example.com", "viewer-pass-1").split("\\.")[1]),
                    StandardCharsets.UTF_8);
            List<String> roles = JsonPath.read(payload, "$.resource_access['ec-admin-api'].roles");

            assertThat(roles).containsExactlyInAnyOrder("tax:read", "category:read", "product:read", "inventory:read", "order:read");
            assertThat((Object) JsonPath.read(payload, "$.aud")).asString().contains("ec-admin-api");
        }
    }

    // ============================== Keycloak ==============================

    /**
     * バックエンドの issuer を、起動した Keycloak に向ける。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class KeycloakConfig {

        @Bean
        DynamicPropertyRegistrar keycloakProperties() {
            KEYCLOAK.start();
            return registry -> {
                registry.add("ecsite.security.admin.issuer-uri", () -> baseUrl() + "/realms/ec-admin");
                registry.add("ecsite.security.store.issuer-uri", () -> baseUrl() + "/realms/ec-store");
            };
        }
    }

    private static String baseUrl() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(HTTP_PORT);
    }

    private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String adminToken(String username, String password) {
        return token("ec-admin", "ec-admin-web", null, username, password);
    }

    private static String storeToken(String username, String password) {
        // ec-store-web は confidential。secret は realm の JSON のローカル用の値
        return token("ec-store", "ec-store-web", "ec-store-web-local-secret", username, password);
    }

    /**
     * パスワードで access token を取る。失敗すると、ステータスを含む例外になる。
     */
    private static String token(String realm, String clientId, String secret, String username, String password) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        if (secret != null) {
            form.add("client_secret", secret);
        }
        form.add("username", username);
        form.add("password", password);
        form.add("scope", "openid");
        var response = RestClient.create(baseUrl()).post()
                .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> {
                    throw new IllegalStateException("token を取れない：" + res.getStatusCode().value() + " "
                            + new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8));
                })
                .body(String.class);
        return JsonPath.read(response, "$.access_token");
    }

    /**
     * client で、パスワードで token を取る方式を有効にする（master の管理者で、管理 API を呼ぶ）。
     */
    private static void enableDirectAccessGrant(String realm, String clientId) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", "admin");
        form.add("password", "admin");
        var client = RestClient.create(baseUrl());
        String adminToken = JsonPath.read(client.post()
                .uri("/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class), "$.access_token");
        List<String> ids = JsonPath.read(client.get()
                .uri("/admin/realms/{realm}/clients?clientId={clientId}", realm, clientId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .body(String.class), "$[*].id");
        client.put()
                .uri("/admin/realms/{realm}/clients/{id}", realm, ids.getFirst())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("clientId", clientId, "directAccessGrantsEnabled", true))
                .retrieve()
                .toBodilessEntity();
    }
}
