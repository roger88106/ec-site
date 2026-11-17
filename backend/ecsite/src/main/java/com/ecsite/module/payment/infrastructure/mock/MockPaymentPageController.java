package com.ecsite.module.payment.infrastructure.mock;

import com.ecsite.common.presentation.MockPageResponses;
import com.ecsite.module.payment.infrastructure.ConditionalOnPaymentMock;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.net.URI;

/**
 * mock の決済サービスの画面
 *
 * <p>外部の決済サービスの画面の代わりで、EC の API ではないので、OpenAPI に書かない。決済の mock フラグを立てたときだけ出す。
 */
@Controller
@ConditionalOnPaymentMock
@RequiredArgsConstructor
class MockPaymentPageController {

    /**
     * mock の決済サービス
     */
    private final MockPaymentGateway gateway;

    /**
     * 支払画面を出す。
     *
     * @param pageId 支払画面 ID
     * @return 画面
     */
    @GetMapping("/mock-payment/{pageId}")
    ResponseEntity<String> show(@PathVariable String pageId) {
        return render(pageId, null);
    }

    /**
     * 支払う。
     *
     * <p>与信の成功を通知してから、店舗の注文の画面に戻す。
     *
     * @param pageId 支払画面 ID
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
     * カードを拒否させる。
     *
     * <p>状態は変えず、拒否を示して同じ画面を出す。
     *
     * @param pageId 支払画面 ID
     * @return 画面
     */
    @PostMapping("/mock-payment/{pageId}/decline")
    ResponseEntity<String> decline(@PathVariable String pageId) {
        return render(pageId, "カードが拒否されました。別のカードでやり直してください。");
    }

    /**
     * やめる。
     *
     * <p>支払わずに、店舗の注文の画面に戻す。
     *
     * @param pageId 支払画面 ID
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
                .map(page -> MockPageResponses.html(HttpStatus.OK, MockPaymentPageHtml.of(pageId, page, error)))
                .orElseGet(() -> MockPageResponses.html(HttpStatus.NOT_FOUND, MockPaymentPageHtml.notFound()));
    }

    /**
     * 戻る先に移す（POST の後なので 303）。
     */
    private static ResponseEntity<String> redirect(String url) {
        return MockPageResponses.redirect(URI.create(url));
    }
}
