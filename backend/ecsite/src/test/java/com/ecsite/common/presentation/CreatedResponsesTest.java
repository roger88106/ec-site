package com.ecsite.common.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 作成の応答の補助の単体テスト（spec 013 の DC-137。バックエンドの書き方 第 2.2 節）
 */
class CreatedResponsesTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @BeforeEach
    void setUpRequest() {
        var request = new MockHttpServletRequest("POST", "/api/admin/products");
        request.setServerName("localhost");
        request.setServerPort(8080);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("管理 API：201 で、今のリクエストの URI に ID を追加した、ホストを含まない相対の URL の Location と、作成した ID を返す")
    void createsAdminResponse() {
        var response = CreatedResponses.admin(ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create("/api/admin/products/" + ID));
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(ID);
    }

    @Test
    @DisplayName("店舗の API：201 で、今のリクエストの URI に ID を追加した、ホストを含まない相対の URL の Location と、作成した ID を返す")
    void createsStoreResponse() {
        var response = CreatedResponses.store(ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create("/api/admin/products/" + ID));
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(ID);
    }

    @Test
    @DisplayName("店舗の API：別のパスを指すときは、アプリの起点にパスと ID を追加した、ホストを含まない相対の URL の Location を返す")
    void createsStoreResponseAtPath() {
        var response = CreatedResponses.storeAt("/api/store/orders/{id}", ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create("/api/store/orders/" + ID));
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(ID);
    }

    @Test
    @DisplayName("店舗の API の作成した ID の本文だけを作れる（作成済みを 200 で返すとき）")
    void createsStoreBody() {
        assertThat(CreatedResponses.storeId(ID).getId()).isEqualTo(ID);
    }
}
