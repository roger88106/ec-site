package com.ecsite.module.shipping.infrastructure.mock;

import com.ecsite.module.shipping.application.port.ShipmentRepository;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import com.ecsite.module.shipping.domain.Shipment;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * mock の物流サービスの画面（docs/backend/modules/shipping.md「mock」）。<br />
 * 外部の物流サービスの代わりで、EC の API ではない（OpenAPI に書かない）。物流サービスが mock のときだけ出す。
 * 出荷の一覧と、出荷ごとに「配送中」「配達完了」「返送」のボタンを持つ。ボタンを押すと、署名付きの通知を
 * HTTP で自分の通知の受け口に送る。
 */
@Controller
@ConditionalOnProperty(name = "ecsite.shipping.carrier", havingValue = "mock", matchIfMissing = true)
@RequiredArgsConstructor
class MockCarrierController {

    /**
     * 一覧に出す出荷の件数の上限
     */
    private static final int LIST_LIMIT = 50;

    /**
     * ボタン → 通知する配送状態（ボタンの並び順）
     */
    private static final Map<DeliveryStatus, String> BUTTONS = Map.of(
            DeliveryStatus.IN_TRANSIT, "配送中",
            DeliveryStatus.DELIVERED, "配達完了",
            DeliveryStatus.RETURNED, "返送");

    /**
     * 配送状態の表示
     */
    private static final Map<DeliveryStatus, String> STATUS_LABELS = Map.of(
            DeliveryStatus.SHIPPED, "出荷済み",
            DeliveryStatus.IN_TRANSIT, "配送中",
            DeliveryStatus.DELIVERED, "配達完了",
            DeliveryStatus.RETURNED, "返送");

    /**
     * 配送業者の表示
     */
    private static final Map<Carrier, String> CARRIER_LABELS = Map.of(
            Carrier.YAMATO, "ヤマト運輸",
            Carrier.SAGAWA, "佐川急便",
            Carrier.JAPAN_POST, "日本郵便");

    /**
     * 出荷のリポジトリ
     */
    private final ShipmentRepository shipmentRepository;

    /**
     * mock の物流サービス
     */
    private final MockCarrier mockCarrier;

    /**
     * 出荷の一覧を出す。
     *
     * @return 画面
     */
    @GetMapping("/mock-carrier")
    ResponseEntity<String> show() {
        return render(null);
    }

    /**
     * 出荷の配送状態を通知する。署名付きの通知を、この画面を出したサーバーの受け口に HTTP で送り、一覧に戻る。
     *
     * @param orderId 注文 ID
     * @param status  通知する配送状態（配送中、配達完了、返送）
     * @param request 要求（受け口の URL を求めるため）
     * @return 一覧への移動。通知が受け付けられなかったときは、その旨を示した一覧
     */
    @PostMapping("/mock-carrier/orders/{orderId}/notify")
    ResponseEntity<String> notifyStatus(@PathVariable UUID orderId, @RequestParam DeliveryStatus status,
                                        HttpServletRequest request) {
        var shipment = shipmentRepository.findAllByOrderIds(List.of(orderId)).stream().findFirst();
        if (shipment.isEmpty() || !BUTTONS.containsKey(status)) {
            return html(HttpStatus.NOT_FOUND, "<p>出荷が見つかりません。</p>" + backLink());
        }
        if (shipment.get().carrier() != Carrier.YAMATO) {
            return render("この mock は、ヤマト運輸の表示の名前でだけ通知できます。");
        }
        var notification = mockCarrier.notification(shipment.get().carrier(), shipment.get().trackingNumber().value(),
                MockCarrier.yamatoNameOf(status));
        try {
            var result = mockCarrier.send(baseUrl(request), notification);
            if (result / 100 != 2) {
                return render("通知が受け付けられませんでした（HTTP " + result + "）。");
            }
        } catch (RestClientException e) {
            return render("通知を送れませんでした：" + e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, "/mock-carrier").build();
    }

// ============================== プライベートメソッド ==============================

    /**
     * 受け口を持つ、このサーバーの URL（要求と同じ接続先）を求める。
     */
    private static String baseUrl(HttpServletRequest request) {
        return ServletUriComponentsBuilder.fromContextPath(request).build().toUriString();
    }

    /**
     * 出荷の一覧の HTML を作る。
     *
     * @param error 示す失敗。なければ null
     */
    private ResponseEntity<String> render(String error) {
        var shipments = shipmentRepository.findRecent(LIST_LIMIT);
        var body = new StringBuilder();
        if (error != null) {
            body.append("<p role=\"alert\">").append(HtmlUtils.htmlEscape(error)).append("</p>");
        }
        if (shipments.isEmpty()) {
            body.append("<p>出荷はまだありません。</p>");
        } else {
            body.append("<table><thead><tr><th>注文 ID</th><th>配送業者</th><th>送り状番号</th><th>配送状態</th><th>通知</th></tr></thead><tbody>");
            shipments.forEach(shipment -> body.append(row(shipment)));
            body.append("</tbody></table>");
        }
        return html(HttpStatus.OK, body.toString());
    }

    /**
     * 出荷 1 件の行を作る。
     */
    private static String row(Shipment shipment) {
        var action = "/mock-carrier/orders/" + shipment.orderId() + "/notify";
        var buttons = new StringBuilder();
        if (shipment.carrier() == Carrier.YAMATO) {
            for (var status : List.of(DeliveryStatus.IN_TRANSIT, DeliveryStatus.DELIVERED, DeliveryStatus.RETURNED)) {
                buttons.append("<form method=\"post\" action=\"").append(action).append("\" style=\"display:inline\">")
                        .append("<input type=\"hidden\" name=\"status\" value=\"").append(status.name()).append("\">")
                        .append("<button type=\"submit\">").append(BUTTONS.get(status)).append("</button></form> ");
            }
        } else {
            buttons.append("（ヤマト運輸の出荷だけ通知できます）");
        }
        return "<tr><td>" + shipment.orderId() + "</td>"
                + "<td>" + CARRIER_LABELS.get(shipment.carrier()) + "</td>"
                + "<td>" + HtmlUtils.htmlEscape(shipment.trackingNumber().value()) + "</td>"
                + "<td>" + STATUS_LABELS.get(shipment.status()) + "</td>"
                + "<td>" + buttons + "</td></tr>";
    }

    /**
     * 一覧に戻るリンクを作る。
     */
    private static String backLink() {
        return "<p><a href=\"/mock-carrier\">一覧に戻る</a></p>";
    }

    /**
     * HTML の応答を作る。
     */
    private static ResponseEntity<String> html(HttpStatus status, String body) {
        var document = "<!doctype html><html lang=\"ja\"><head><meta charset=\"utf-8\"><title>mock の物流サービス</title></head>"
                + "<body><h1>mock の物流サービス</h1>" + body + "</body></html>";
        return ResponseEntity.status(status).contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(document);
    }
}
