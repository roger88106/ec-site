package com.ecsite.module.payment.infrastructure.mock;

import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.web.util.HtmlUtils;

/**
 * mock の支払の画面の HTML を組み立てる（{@link MockPaymentPageController} の補助）。<br />
 * 外から来る値（支払の画面 ID、戻る先の URL、エラーの文）は、ここで HTML の特殊文字を置き換える。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MockPaymentPageHtml {

    /**
     * 支払の画面が見つからないときの HTML を返す。
     *
     * @return HTML
     */
    static String notFound() {
        return document("<p>支払の画面が見つかりません。</p>");
    }

    /**
     * 画面の状態に合わせた HTML を返す。
     *
     * @param pageId 支払の画面 ID
     * @param page   支払の画面
     * @param error  示すエラーの文。なければ null
     * @return HTML
     */
    static String of(String pageId, MockPaymentGateway.Page page, @Nullable String error) {
        var back = "<p><a href=\"" + HtmlUtils.htmlEscape(page.returnUrl()) + "\">注文の画面に戻る</a></p>";
        return switch (page.status()) {
            case PAID -> document("<p>支払は済んでいます。</p>" + back);
            case CLOSED -> document("<p>支払の期限が切れました。</p>" + back);
            case OPEN -> {
                var action = "/mock-payment/" + HtmlUtils.htmlEscape(pageId);
                yield document((error == null ? "" : "<p role=\"alert\">" + HtmlUtils.htmlEscape(error) + "</p>")
                        + "<p>お支払い金額：" + String.format("%,d", page.amount().yen()) + " 円</p>"
                        + form(action + "/pay", "支払う")
                        + form(action + "/decline", "カードを拒否させる")
                        + form(action + "/cancel", "やめる"));
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
     * 本文を HTML の文書で包む。
     */
    private static String document(String body) {
        return "<!doctype html><html lang=\"ja\"><head><meta charset=\"utf-8\"><title>mock の支払</title></head>"
                + "<body><h1>mock の支払</h1>" + body + "</body></html>";
    }
}
