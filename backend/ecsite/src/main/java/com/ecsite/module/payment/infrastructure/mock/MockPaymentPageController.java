package com.ecsite.module.payment.infrastructure.mock;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * mock の支払の画面（docs/backend/modules/payment.md「mock」）。<br />
 * 外部の決済サービスの画面の代わりで、EC の API ではない（OpenAPI に書かない）。決済サービスが mock のときだけ出す。
 * 「支払う」「カードを拒否させる」「やめる」の操作を持ち、支払う・やめるときは店舗の注文の画面に戻す。
 */
@Controller
@ConditionalOnProperty(name = "ecsite.payment.gateway", havingValue = "mock", matchIfMissing = true)
@RequiredArgsConstructor
class MockPaymentPageController {

    /**
     * mock の決済サービス
     */
    private final MockPaymentGateway gateway;

    /**
     * 支払の画面を出す。
     *
     * @param pageId 支払の画面 ID
     * @return 画面
     */
    @GetMapping("/mock-payment/{pageId}")
    ResponseEntity<String> show(@PathVariable String pageId) {
        return render(pageId, null);
    }

    /**
     * 支払う。与信の成功を通知してから、店舗の注文の画面に戻す。
     *
     * @param pageId 支払の画面 ID
     * @return 戻る先への移動。開いていない画面なら、その旨の画面
     */
    @PostMapping("/mock-payment/{pageId}/pay")
    ResponseEntity<String> pay(@PathVariable String pageId) {
        var page = gateway.page(pageId).filter(found -> found.status() == MockPaymentGateway.PageStatus.OPEN);
        if (page.isEmpty()) {
            return render(pageId, null);
        }
        gateway.pay(pageId);
        return redirect(page.get().returnUrl());
    }

    /**
     * カードを拒否させる。状態は変えず、拒否を示して同じ画面を出す。
     *
     * @param pageId 支払の画面 ID
     * @return 画面
     */
    @PostMapping("/mock-payment/{pageId}/decline")
    ResponseEntity<String> decline(@PathVariable String pageId) {
        return render(pageId, "カードが拒否されました。別のカードでやり直してください。");
    }

    /**
     * やめる。支払わずに、店舗の注文の画面に戻す。
     *
     * @param pageId 支払の画面 ID
     * @return 戻る先への移動
     */
    @PostMapping("/mock-payment/{pageId}/cancel")
    ResponseEntity<String> cancel(@PathVariable String pageId) {
        return gateway.page(pageId)
                .map(page -> redirect(page.returnUrl()))
                .orElseGet(() -> render(pageId, null));
    }

// ============================== プライベートメソッド ==============================

    /**
     * 画面の状態に合わせて HTML を作る。
     */
    private ResponseEntity<String> render(String pageId, @Nullable String error) {
        return gateway.page(pageId)
                .map(page -> html(HttpStatus.OK, MockPaymentPageHtml.of(pageId, page, error)))
                .orElseGet(() -> html(HttpStatus.NOT_FOUND, MockPaymentPageHtml.notFound()));
    }

    /**
     * HTML の応答を作る。
     */
    private static ResponseEntity<String> html(HttpStatus status, String document) {
        return ResponseEntity.status(status).contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(document);
    }

    /**
     * 戻る先に移す（POST の後なので 303）。
     */
    private static ResponseEntity<String> redirect(String url) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, URI.create(url).toString()).build();
    }
}
