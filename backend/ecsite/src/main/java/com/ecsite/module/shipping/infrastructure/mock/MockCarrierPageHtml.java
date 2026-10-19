package com.ecsite.module.shipping.infrastructure.mock;

import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import com.ecsite.module.shipping.domain.Shipment;
import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * mock の物流サービスの画面の HTML を組み立てる（{@link MockCarrierController} の補助）。<br />
 * 外から来る値（送り状番号、エラーの文）は、ここで HTML の特殊文字を置き換える。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MockCarrierPageHtml {

    /**
     * 通知できる配送状態（ボタンの並び順）
     */
    static final List<DeliveryStatus> NOTIFIABLE = List.of(
            DeliveryStatus.IN_TRANSIT, DeliveryStatus.DELIVERED, DeliveryStatus.RETURNED);

    /**
     * 配送状態の表示
     */
    private static final Map<DeliveryStatus, String> STATUS_LABELS = Map.of(
            DeliveryStatus.SHIPPED, "出荷済み",
            DeliveryStatus.IN_TRANSIT, "配送中",
            DeliveryStatus.DELIVERED, "配達完了",
            DeliveryStatus.RETURNED, "返送");

    /**
     * 出荷の一覧の HTML を返す。
     *
     * @param shipments   出荷
     * @param carrierName 配送業者 → 表示の名前
     * @param error       示す失敗。なければ null
     * @return HTML
     */
    static String list(List<Shipment> shipments, Function<Carrier, String> carrierName, @Nullable String error) {
        var body = new StringBuilder();
        if (error != null) {
            body.append("<p role=\"alert\">").append(HtmlUtils.htmlEscape(error)).append("</p>");
        }
        if (shipments.isEmpty()) {
            body.append("<p>出荷はまだありません。</p>");
        } else {
            body.append("<table><thead><tr><th>注文 ID</th><th>配送業者</th><th>送り状番号</th><th>配送状態</th><th>通知</th></tr></thead><tbody>");
            shipments.forEach(shipment -> body.append(row(shipment, carrierName)));
            body.append("</tbody></table>");
        }
        return document(body.toString());
    }

    /**
     * 出荷が見つからないときの HTML を返す。
     *
     * @return HTML
     */
    static String notFound() {
        return document("<p>出荷が見つかりません。</p><p><a href=\"/mock-carrier\">一覧に戻る</a></p>");
    }

    /**
     * 出荷 1 件の行を作る。
     */
    private static String row(Shipment shipment, Function<Carrier, String> carrierName) {
        var action = "/mock-carrier/orders/" + shipment.orderId() + "/notify";
        var buttons = new StringBuilder();
        if (shipment.carrier() == Carrier.YAMATO) {
            for (var status : NOTIFIABLE) {
                buttons.append("<form method=\"post\" action=\"").append(action).append("\" style=\"display:inline\">")
                        .append("<input type=\"hidden\" name=\"status\" value=\"").append(status.name()).append("\">")
                        .append("<button type=\"submit\">").append(STATUS_LABELS.get(status)).append("</button></form> ");
            }
        } else {
            buttons.append("（").append(HtmlUtils.htmlEscape(carrierName.apply(Carrier.YAMATO)))
                    .append("の出荷だけ通知できます）");
        }
        return "<tr><td>" + shipment.orderId() + "</td>"
                + "<td>" + HtmlUtils.htmlEscape(carrierName.apply(shipment.carrier())) + "</td>"
                + "<td>" + HtmlUtils.htmlEscape(shipment.trackingNumber().value()) + "</td>"
                + "<td>" + STATUS_LABELS.get(shipment.status()) + "</td>"
                + "<td>" + buttons + "</td></tr>";
    }

    /**
     * 本文を HTML の文書で包む。
     */
    private static String document(String body) {
        return "<!doctype html><html lang=\"ja\"><head><meta charset=\"utf-8\"><title>mock の物流サービス</title></head>"
                + "<body><h1>mock の物流サービス</h1>" + body + "</body></html>";
    }
}
