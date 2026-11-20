package com.ecsite.config.security;

import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.security.TestTokens;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IdP に届かないときの、token の検証の応答
 *
 * <p>IdP の代わりに、待ち受けのないポートを使う（このクラスだけ）。有効な token を付けたリクエストが、
 * 検証に必要な IdP の設定や署名の鍵を取得できないときの応答と、取得をやり直す回数を確認する。
 *
 * <p>失敗は filter から外に出て、サーブレットのコンテナがエラーの画面に転送する。MockMvc はその転送をしないので、
 * 業務ポートを実際に開き、HTTP で呼ぶ。
 *
 * <p>realm の decoder は、一度作れたら使い続ける。decoder を作れない場合と、作れたが鍵を取得できない場合で、
 * 署名の鍵の差し替えを分けるため、入れ子のクラスごとに Spring のコンテキストを分ける。
 */
@IntegrationTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdpUnreachableTest {

    // ============================== 定数 ==============================

    /**
     * サーブレットのコンテナの既定のエラーの本文の項目（Problem Details でない）
     */
    private static final String[] CONTAINER_ERROR_FIELDS = {"timestamp", "status", "error", "path"};

    // ============================== Bean ==============================

    @LocalServerPort
    int port;

    /**
     * HTTP の送り手
     */
    private final HttpClient http = HttpClient.newHttpClient();

    // ============================== テスト ==============================

    @Nested
    @DisplayName("IdP の設定を取得できず、decoder を作れないとき")
    class DecoderUnavailable {

        /**
         * realm（issuer）ごとの、decoder を作った回数
         */
        private static final Map<String, AtomicInteger> DECODER_CREATIONS = new ConcurrentHashMap<>();

        /**
         * realm の署名の鍵（待ち受けのないポートの IdP に向けた、本番の作り方に差し替える）
         */
        @TestBean
        RealmKeys realmKeys;

        /**
         * 本番の作り方で、待ち受けのないポートの issuer の decoder を作る署名の鍵を作る。
         *
         * <p>作るたびに、本来の issuer ごとに回数を数える。
         *
         * @return realm の署名の鍵
         */
        static RealmKeys realmKeys() {
            var unreachableIssuer = "http://127.0.0.1:" + unusedPort() + "/realms/unreachable";
            var production = new SecurityConfig().realmKeys();
            return issuerUri -> {
                DECODER_CREATIONS.computeIfAbsent(issuerUri, key -> new AtomicInteger()).incrementAndGet();
                return production.decoder(unreachableIssuer);
            };
        }

        @BeforeEach
        void resetCount() {
            DECODER_CREATIONS.clear();
        }

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(Api.class)
        @DisplayName("DC-477-10 有効な token を付けても、500 で、本文は Problem Details でない")
        void respondsContainerError(Api api) throws Exception {
            // 操作：有効な token を付けて呼ぶ
            var response = send(api);

            // 確認：コンテナの既定のエラーの応答
            assertContainerError(response, api);
        }

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(Api.class)
        @DisplayName("DC-477-10 リクエストのたびに、decoder を再作成する（IdP の設定を取得し直す）")
        void recreatesDecoderPerRequest(Api api) throws Exception {
            // 操作：有効な token を付けて 2 回呼ぶ
            send(api);
            send(api);

            // 確認：その realm の decoder を、リクエストごとに 1 回ずつ作った
            assertThat(DECODER_CREATIONS).containsOnlyKeys(api.issuer);
            assertThat(DECODER_CREATIONS.get(api.issuer)).hasValue(2);
        }
    }

    @Nested
    @DisplayName("decoder は作れたが、署名の鍵を取得できないとき")
    class KeysUnavailable {

        /**
         * realm（issuer）ごとの、decoder を作った回数
         *
         * <p>decoder は一度作れたら使い続けるので、コンテキストを起動してからの累計にする（テストごとに戻さない）。
         */
        private static final Map<String, AtomicInteger> DECODER_CREATIONS = new ConcurrentHashMap<>();

        /**
         * realm（issuer）ごとの、署名の鍵を取得しようとした回数
         */
        private static final Map<String, AtomicInteger> KEY_FETCHES = new ConcurrentHashMap<>();

        /**
         * realm の署名の鍵（待ち受けのないポートの鍵の URL に向けた decoder に差し替える）
         */
        @TestBean
        RealmKeys realmKeys;

        /**
         * 待ち受けのないポートから鍵を取得する decoder を作る署名の鍵を作る。
         *
         * <p>鍵の URL を直接指定するので、decoder は IdP に問い合わせずに作れる。鍵は token の検証のときに取得する。
         * decoder を作った回数と、鍵を取得しようとした回数を、本来の issuer ごとに数える。
         *
         * @return realm の署名の鍵
         */
        static RealmKeys realmKeys() {
            var unreachableKeys = "http://127.0.0.1:" + unusedPort() + "/realms/unreachable/protocol/openid-connect/certs";
            return issuerUri -> {
                DECODER_CREATIONS.computeIfAbsent(issuerUri, key -> new AtomicInteger()).incrementAndGet();
                var rest = new RestTemplate();
                rest.getInterceptors().add((request, body, execution) -> {
                    KEY_FETCHES.computeIfAbsent(issuerUri, key -> new AtomicInteger()).incrementAndGet();
                    return execution.execute(request, body);
                });
                return NimbusJwtDecoder.withJwkSetUri(unreachableKeys).restOperations(rest).build();
            };
        }

        @BeforeEach
        void resetCount() {
            KEY_FETCHES.clear();
        }

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(Api.class)
        @DisplayName("DC-477-10 有効な token を付けても、500 で、本文は Problem Details でない")
        void respondsContainerError(Api api) throws Exception {
            // 操作：有効な token を付けて呼ぶ
            var response = send(api);

            // 確認：コンテナの既定のエラーの応答
            assertContainerError(response, api);
        }

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(Api.class)
        @DisplayName("DC-477-10 リクエストのたびに署名の鍵を取得し直し、decoder は再作成しない")
        void refetchesKeysPerRequest(Api api) throws Exception {
            // 操作：有効な token を付けて 2 回呼ぶ
            send(api);
            send(api);

            // 確認：その realm の鍵を、リクエストごとに 1 回ずつ取得しようとした
            assertThat(KEY_FETCHES).containsOnlyKeys(api.issuer);
            assertThat(KEY_FETCHES.get(api.issuer)).hasValue(2);

            // 確認：その realm の decoder は、コンテキストを起動してから 1 回だけ作った
            assertThat(DECODER_CREATIONS.get(api.issuer)).hasValue(1);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 応答が、サーブレットのコンテナの既定のエラー（Problem Details でない 500）であることを確認する。
     *
     * <ul>
     *   <li>状態コードが 500</li>
     *   <li>Content-Type が {@code application/json}（{@code application/problem+json} でない）</li>
     *   <li>本文の項目が {@link #CONTAINER_ERROR_FIELDS} だけで、状態コードと呼んだパスを持つ（エラーコードがない）</li>
     *   <li>{@code WWW-Authenticate} がない（認証の失敗の応答でない）</li>
     * </ul>
     *
     * @param response 応答
     * @param api      呼んだ API
     */
    private static void assertContainerError(HttpResponse<String> response, Api api) {
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE)).hasValue(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = JsonPath.read(response.body(), "$");
        assertThat(body).containsOnlyKeys(CONTAINER_ERROR_FIELDS)
                .containsEntry("status", 500)
                .containsEntry("path", api.path);
        assertThat(response.headers().firstValue(HttpHeaders.WWW_AUTHENTICATE)).isEmpty();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 呼ぶ API
     */
    enum Api {

        /**
         * 管理 API（税区分の一覧。管理者の token）
         */
        ADMIN("管理 API", "/api/admin/tax-categories", TestTokens.ADMIN_ISSUER, () -> TestTokens.administrator().encode()),

        /**
         * 店舗 API（会員のカート。会員の token が必要な操作）
         */
        STORE("店舗 API", "/api/store/me/cart", TestTokens.STORE_ISSUER, () -> TestTokens.member().encode());

        /**
         * テストの名前に出す名前
         */
        private final String label;

        /**
         * パス
         */
        private final String path;

        /**
         * token を検証する realm の issuer
         */
        private final String issuer;

        /**
         * 有効な token を作る（有効期間が切れないように、リクエストごとに作る）
         */
        private final Supplier<String> token;

        Api(String label, String path, String issuer, Supplier<String> token) {
            this.label = label;
            this.path = path;
            this.issuer = issuer;
            this.token = token;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * 有効な token を付けて、業務ポートの API を GET で呼ぶ。
     *
     * @param api 呼ぶ API
     * @return 応答
     */
    private HttpResponse<String> send(Api api) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + api.path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + api.token.get())
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 待ち受けのないポートを探す。
     *
     * @return ポート（空いていることを確認してから閉じたもの）
     */
    private static int unusedPort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
