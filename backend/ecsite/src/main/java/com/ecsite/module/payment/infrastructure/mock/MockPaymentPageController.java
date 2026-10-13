package com.ecsite.module.payment.infrastructure.mock;

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
import org.springframework.web.util.HtmlUtils;

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
    private ResponseEntity<String> render(String pageId, String error) {
        var page = gateway.page(pageId);
        if (page.isEmpty()) {
            return html(HttpStatus.NOT_FOUND, "<p>支払の画面が見つかりません。</p>");
        }
        var found = page.get();
        var back = "<p><a href=\"" + HtmlUtils.htmlEscape(found.returnUrl()) + "\">注文の画面に戻る</a></p>";
        return switch (found.status()) {
            case PAID -> html(HttpStatus.OK, "<p>支払は済んでいます。</p>" + back);
            case CLOSED -> html(HttpStatus.OK, "<p>支払の期限が切れました。</p>" + back);
            case OPEN -> {
                var action = "/mock-payment/" + HtmlUtils.htmlEscape(pageId);
                var body = (error == null ? "" : "<p role=\"alert\">" + HtmlUtils.htmlEscape(error) + "</p>")
                        + "<p>お支払い金額：" + String.format("%,d", found.amount().yen()) + " 円</p>"
                        + form(action + "/pay", "支払う")
                        + form(action + "/decline", "カードを拒否させる")
                        + form(action + "/cancel", "やめる");
                yield html(HttpStatus.OK, body);
            }
        };
    }

    /**
     * 1 つのボタンだけの form を作る。
     */
    private static String form(String action, String label) {
        return "<form method=\"post\" action=\"" + action + "\"><button type=\"submit\">" + label + "</button></form>";
    }

    /**
     * HTML の応答を作る。
     */
    private static ResponseEntity<String> html(HttpStatus status, String body) {
        var document = "<!doctype html><html lang=\"ja\"><head><meta charset=\"utf-8\"><title>mock の支払</title></head>"
                + "<body><h1>mock の支払</h1>" + body + "</body></html>";
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
