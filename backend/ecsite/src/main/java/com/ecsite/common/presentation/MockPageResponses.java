package com.ecsite.common.presentation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * mock の画面（決済サービス、配送業者）の応答
 *
 * <p>mock の画面は EC の API ではなく、HTML を返して、操作の後に別の画面へ移す。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MockPageResponses {

    /**
     * HTML の応答を作る。文字コードは UTF-8。
     *
     * @param status   HTTP ステータス
     * @param document HTML
     * @return 応答
     */
    public static ResponseEntity<String> html(HttpStatus status, String document) {
        return ResponseEntity.status(status).contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(document);
    }

    /**
     * 別の画面に移す応答（303）を作る。
     *
     * @param location 移す先
     * @return 応答
     */
    public static ResponseEntity<String> redirect(URI location) {
        // POST の後に GET で移させるため、303 にする
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build();
    }
}
