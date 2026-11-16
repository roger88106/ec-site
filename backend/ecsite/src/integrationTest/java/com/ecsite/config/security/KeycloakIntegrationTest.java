package com.ecsite.config.security;

import com.ecsite.contract.Contracts;
import com.ecsite.support.config.TestcontainersConfig;
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
 * IdP の設定の統合テスト
 *
 * <p>Keycloak を Testcontainers で起動して、両 realm の JSON（{@code docker/keycloak/realms/}）を取り込み、
 * Keycloak が発行した token で管理 API を呼ぶ。バックエンドは本番と同じく、issuer の OpenID Connect の設定から公開鍵を取る
 * （テストの鍵に差し替える {@code TestSecurityConfig} は使わないので、{@code @IntegrationTest} とは別の設定で起動する）。
 *
 * <p>token は、パスワードで取る（Direct Access Grant）。realm の JSON では web の client でこれを無効にしているので、
 * テストの中で有効にする（{@code scripts/dev/keycloak.sh} と同じ）。画面と同じ client の token（audience・scope の対応）で確認するため。
 * 期限切れの token の拒否は、検証の処理が同じなので、テストの鍵の token で確認する（AuthorizationTest）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, KeycloakIntegrationTest.KeycloakConfig.class})
@ActiveProfiles("integration-test")
@Transactional
class KeycloakIntegrationTest {

    // ============================== 定数 ==============================

    /**
     * docker/compose.yaml と同じイメージ
     */
    private static final String IMAGE = "quay.io/keycloak/keycloak:26.8.2";

    /**
     * Keycloak の HTTP のポート
     */
    private static final int HTTP_PORT = 8080;

    /**
     * Keycloak の運用のポート
     */
    private static final int MANAGEMENT_PORT = 9000;

    /**
     * realm の設定の置き場所
     *
     * <p>Gradle の integrationTest task が渡す system property を、契約の補助で読む。
     */
    private static final Path REALMS_DIR = Contracts.realmsDir();

    /**
     * テーマの置き場所
     *
     * <p>realm の設定と同じく、契約の補助で読む。
     */
    private static final Path THEME_DIR = Contracts.themeDir();

    /**
     * 税区分の管理 API のパス
     */
    private static final String TAX_CATEGORIES = "/api/admin/tax-categories";

    /**
     * 会員のカートの店舗 API のパス
     */
    private static final String MEMBER_CART = "/api/store/me/cart";

    /**
     * 管理者のアカウント（realm の JSON のローカル用のユーザー）
     */
    private static final String ADMIN = "admin@example.com";

    /**
     * 管理者のパスワード
     */
    private static final String ADMIN_PASSWORD = "admin-pass-1";

    /**
     * 閲覧者のアカウント
     */
    private static final String VIEWER = "viewer@example.com";

    /**
     * 閲覧者のパスワード
     */
    private static final String VIEWER_PASSWORD = "viewer-pass-1";

    /**
     * 会員のアカウント
     */
    private static final String MEMBER = "member@example.com";

    /**
     * 会員のパスワード
     */
    private static final String MEMBER_PASSWORD = "member-pass-1";

    /**
     * テストのクラスで共有する Keycloak
     *
     * <p>起動に時間がかかるので 1 つだけ起動する。
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

    // ============================== Bean ==============================

    /**
     * API を呼ぶ MockMvc
     */
    @Autowired
    MockMvc mockMvc;

    @BeforeAll
    static void enableDirectAccessGrants() {
        KEYCLOAK.start();
        enableDirectAccessGrant("ec-admin", "ec-admin-web");
        enableDirectAccessGrant("ec-store", "ec-store-web");
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("運営者の職務どおりの管理 API の利用")
    class Operator {

        @Test
        @DisplayName("AC-139 管理者は参照できる")
        void administratorCanRead() throws Exception {
            // 準備：管理者の token
            var token = adminToken(ADMIN, ADMIN_PASSWORD);

            // 操作と確認：税区分の一覧を参照できる
            mockMvc.perform(withToken(get(TAX_CATEGORIES), token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("AC-139 管理者は更新できる")
        void administratorCanWrite() throws Exception {
            // 準備：管理者の token
            var token = adminToken(ADMIN, ADMIN_PASSWORD);

            // 操作と確認：税区分を登録できる
            mockMvc.perform(withToken(post(TAX_CATEGORIES), token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"Keycloak の確認\"}"))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("AC-139 閲覧者は参照できる")
        void viewerCanRead() throws Exception {
            // 準備：閲覧者の token
            var token = adminToken(VIEWER, VIEWER_PASSWORD);

            // 操作と確認：税区分の一覧を参照できる
            mockMvc.perform(withToken(get(TAX_CATEGORIES), token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("AC-139 閲覧者の更新は 403")
        void viewerCannotWrite() throws Exception {
            // 準備：閲覧者の token
            var token = adminToken(VIEWER, VIEWER_PASSWORD);

            // 操作と確認：税区分を登録すると 403
            mockMvc.perform(withToken(post(TAX_CATEGORIES), token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"閲覧者の更新\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("管理 API が受け付けない token")
    class Rejected {

        @Test
        @DisplayName("AC-138 会員の token では、管理 API は 401")
        void memberTokenIsRejected() throws Exception {
            // 準備：会員の token
            var token = storeToken(MEMBER, MEMBER_PASSWORD);

            // 操作と確認：税区分の一覧は 401
            mockMvc.perform(withToken(get(TAX_CATEGORIES), token))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-137 署名を書き換えた token は 401")
        void tamperedTokenIsRejected() throws Exception {
            // 準備：管理者の token の署名の 1 ビットを変える
            var token = adminToken(ADMIN, ADMIN_PASSWORD);
            var parts = token.split("\\.", -1);
            var signature = Base64.getUrlDecoder().decode(parts[2]);
            signature[0] ^= 0x01;
            var tampered = parts[0] + "." + parts[1] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

            // 操作と確認：税区分の一覧は 401
            mockMvc.perform(withToken(get(TAX_CATEGORIES), tampered))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("店舗 API の会員の操作")
    class Store {

        @Test
        @DisplayName("AC-121 会員の token で、会員のカートを使える")
        void memberTokenIsAccepted() throws Exception {
            // 準備：会員の token
            var token = storeToken(MEMBER, MEMBER_PASSWORD);

            // 操作と確認：会員のカートを開ける
            mockMvc.perform(withToken(get(MEMBER_CART), token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("AC-138 運営者の token では、会員の操作は 401")
        void operatorTokenIsRejected() throws Exception {
            // 準備：管理者の token
            var token = adminToken(ADMIN, ADMIN_PASSWORD);

            // 操作と確認：会員のカートは 401
            mockMvc.perform(withToken(get(MEMBER_CART), token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("realm の設定")
    class RealmSettings {

        @Test
        @DisplayName("AC-133 会員のアカウントでは、運営者の realm でログインできない")
        void memberCannotLogInToAdminRealm() {
            // 操作と確認：運営者の realm で会員のアカウントの token を取ると、拒否される
            assertThatThrownBy(() -> adminToken(MEMBER, MEMBER_PASSWORD)).hasMessageContaining("invalid_grant");
        }

        @Test
        @DisplayName("AC-133 運営者のアカウントでは、会員の realm でログインできない")
        void operatorCannotLogInToStoreRealm() {
            // 操作と確認：会員の realm で管理者のアカウントの token を取ると、拒否される
            assertThatThrownBy(() -> storeToken(ADMIN, ADMIN_PASSWORD)).hasMessageContaining("invalid_grant");
        }

        @Test
        @DisplayName("AC-140 運営者の access token の有効期間は 5 分")
        void adminAccessTokenLifespanIsFiveMinutes() {
            // 操作：管理者の token を取る
            var payload = payload(adminToken(ADMIN, ADMIN_PASSWORD));

            // 確認：発行から期限までが 300 秒
            assertThat(lifespanSeconds(payload)).isEqualTo(300);
        }

        @Test
        @DisplayName("AC-140 会員の access token の有効期間は 5 分")
        void storeAccessTokenLifespanIsFiveMinutes() {
            // 操作：会員の token を取る
            var payload = payload(storeToken(MEMBER, MEMBER_PASSWORD));

            // 確認：発行から期限までが 300 秒
            assertThat(lifespanSeconds(payload)).isEqualTo(300);
        }

        @Test
        @DisplayName("運営者の token の権限は、職務から展開された管理 API の client role だけ")
        void operatorTokenCarriesPermissions() {
            // 操作：閲覧者の token を取る
            var payload = payload(adminToken(VIEWER, VIEWER_PASSWORD));

            // 確認：管理 API の client role が、閲覧者の職務の参照の権限だけ
            List<String> roles = JsonPath.read(payload, "$.resource_access['ec-admin-api'].roles");
            assertThat(roles).containsExactlyInAnyOrder("tax:read", "category:read", "product:read", "inventory:read", "order:read");
        }

        @Test
        @DisplayName("運営者の token の audience に、管理 API が入る")
        void operatorTokenHasAdminApiAudience() {
            // 操作：閲覧者の token を取る
            var payload = payload(adminToken(VIEWER, VIEWER_PASSWORD));

            // 確認：audience に管理 API がある
            assertThat((Object) JsonPath.read(payload, "$.aud")).asString().contains("ec-admin-api");
        }
    }

    // ============================== Keycloak ==============================

    /**
     * 起動した Keycloak に issuer を向ける設定
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

    /**
     * 起動した Keycloak の URL を返す。
     *
     * @return URL
     */
    private static String baseUrl() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(HTTP_PORT);
    }

    /**
     * リクエストに token を付ける。
     *
     * @param request リクエスト
     * @param token   access token
     * @return token を付けたリクエスト
     */
    private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    /**
     * token の本体（payload）の JSON を返す。
     *
     * @param token access token
     * @return 本体の JSON
     */
    private static String payload(String token) {
        return new String(Base64.getUrlDecoder().decode(token.split("\\.", -1)[1]), StandardCharsets.UTF_8);
    }

    /**
     * token の発行から期限までの秒数を返す。
     *
     * @param payload token の本体の JSON
     * @return 秒数
     */
    private static long lifespanSeconds(String payload) {
        long issuedAt = ((Number) JsonPath.read(payload, "$.iat")).longValue();
        long expiresAt = ((Number) JsonPath.read(payload, "$.exp")).longValue();
        return expiresAt - issuedAt;
    }

    /**
     * 運営者の realm で、管理画面の client の access token を取る。
     *
     * @param username アカウント
     * @param password パスワード
     * @return access token
     */
    private static String adminToken(String username, String password) {
        return token("ec-admin", "ec-admin-web", null, username, password);
    }

    /**
     * 会員の realm で、店舗の client の access token を取る。
     *
     * @param username アカウント
     * @param password パスワード
     * @return access token
     */
    private static String storeToken(String username, String password) {
        // ec-store-web は confidential。secret は realm の JSON のローカル用の値
        return token("ec-store", "ec-store-web", "ec-store-web-local-secret", username, password);
    }

    /**
     * パスワードで access token を取る。
     *
     * <p>失敗すると、ステータスを含む例外になる。
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
