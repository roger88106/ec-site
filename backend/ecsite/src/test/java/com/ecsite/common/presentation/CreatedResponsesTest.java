package com.ecsite.common.presentation;

import com.ecsite.api.admin.model.CreatedId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 作成の応答の補助の単体テスト
 */
class CreatedResponsesTest {

    // ============================== 定数 ==============================

    /**
     * 作成した ID
     */
    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    /**
     * 今のリクエストのパス
     */
    private static final String REQUEST_PATH = "/api/admin/products";

    /**
     * 今のリクエストのパスに ID を追加した、ホストを含まない相対の URL
     */
    private static final URI REQUEST_LOCATION = URI.create(REQUEST_PATH + "/" + ID);

    /**
     * 今のリクエストを外す
     */
    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("作成の応答")
    class Created {

        @Test
        @DisplayName("管理 API：201 で、今のリクエストの URI に ID を追加した、ホストを含まない相対の URL の Location と、作成した ID を返す")
        void createsAdminResponse() {
            // 準備：クエリのないリクエスト
            bindRequest(null);

            // 操作：管理 API の作成の応答を作る
            var response = CreatedResponses.admin(ID);

            // 確認：201、今のリクエストの URL、作成した ID
            assertCreated(response, REQUEST_LOCATION, CreatedId::getId);
        }

        @Test
        @DisplayName("店舗の API：201 で、今のリクエストの URI に ID を追加した、ホストを含まない相対の URL の Location と、作成した ID を返す")
        void createsStoreResponse() {
            // 準備：クエリのないリクエスト
            bindRequest(null);

            // 操作：店舗の API の作成の応答を作る
            var response = CreatedResponses.store(ID);

            // 確認：201、今のリクエストの URL、作成した ID
            assertCreated(response, REQUEST_LOCATION, com.ecsite.api.store.model.CreatedId::getId);
        }

        @Test
        @DisplayName("店舗の API：別のパスを指すときは、アプリの起点にパスと ID を追加した、ホストを含まない相対の URL の Location を返す")
        void createsStoreResponseAtPath() {
            // 準備：クエリのないリクエスト
            bindRequest(null);

            // 操作：注文のパスを指す作成の応答を作る
            var response = CreatedResponses.storeAt("/api/store/orders/{id}", ID);

            // 確認：201、注文のパスの URL、作成した ID
            assertCreated(response, URI.create("/api/store/orders/" + ID), com.ecsite.api.store.model.CreatedId::getId);
        }

        @Test
        @DisplayName("店舗の API の作成した ID の本文だけを作れる（作成済みを 200 で返すとき）")
        void createsStoreBody() {
            // 操作と確認：作成した ID の本文
            assertThat(CreatedResponses.storeId(ID).getId()).isEqualTo(ID);
        }
    }

    @Nested
    @DisplayName("Location のクエリ")
    class Query {

        @Test
        @DisplayName("管理 API：リクエストにクエリがあっても、Location にはクエリを含めない")
        void excludesQueryFromAdminLocation() {
            // 準備：token を含むクエリのあるリクエスト
            bindRequest("token=secret&a=b");

            // 操作と確認：クエリのない URL
            assertThat(CreatedResponses.admin(ID).getHeaders().getLocation()).isEqualTo(REQUEST_LOCATION);
        }

        @Test
        @DisplayName("店舗の API：リクエストにクエリがあっても、Location にはクエリを含めない")
        void excludesQueryFromStoreLocation() {
            // 準備：token を含むクエリのあるリクエスト
            bindRequest("token=secret&a=b");

            // 操作と確認：クエリのない URL
            assertThat(CreatedResponses.store(ID).getHeaders().getLocation()).isEqualTo(REQUEST_LOCATION);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 作成の応答であることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態が 201</li>
     *   <li>Location が期待する URL</li>
     *   <li>本文がある</li>
     *   <li>本文の ID が作成した ID</li>
     * </ul>
     *
     * @param response 確認する応答
     * @param location 期待する Location
     * @param idOf     本文から ID を読む処理（本文の型が API ごとに違うため）
     * @param <T>      本文の型
     */
    private static <T> void assertCreated(ResponseEntity<T> response, URI location, Function<T, UUID> idOf) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isEqualTo(location);
        assertThat(response.getBody()).isNotNull();
        assertThat(idOf.apply(response.getBody())).isEqualTo(ID);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 作成の POST のリクエストを、今のリクエストにする。
     *
     * @param query クエリ。なければ null
     */
    private static void bindRequest(String query) {
        var request = new MockHttpServletRequest("POST", REQUEST_PATH);
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.setQueryString(query);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
