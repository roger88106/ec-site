package com.ecsite.module.shipping.infrastructure.mock;

import com.ecsite.common.message.MessageHelper;
import com.ecsite.module.shipping.application.port.ShipmentRepository;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * mock の物流サービスの画面（docs/design/modules/shipping.md「mock」）。<br />
 * 外部の物流サービスの代わりで、EC の API ではない（OpenAPI に書かない）。物流の mock フラグを立てたときだけ出す。
 * 出荷の一覧と、出荷ごとに「配送中」「配達完了」「返送」のボタンを持つ。ボタンを押すと、署名付きの通知を
 * HTTP で自分の通知のエンドポイントに送る。
 */
@Slf4j
@Controller
@ConditionalOnBooleanProperty(name = "ecsite.shipping.mock.enabled")
@RequiredArgsConstructor
class MockCarrierController {

    /**
     * 一覧に出す出荷の件数の上限
     */
    private static final int LIST_LIMIT = 50;

    /**
     * 出荷のリポジトリ
     */
    private final ShipmentRepository shipmentRepository;

    /**
     * 最近の出荷の問い合わせ
     */
    private final MockShipmentQuery shipmentQuery;

    /**
     * mock の物流サービス
     */
    private final MockCarrier mockCarrier;

    /**
     * メッセージ（配送業者の表示の名前）
     */
    private final MessageHelper messageHelper;

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
     * 出荷の配送状態を通知する。署名付きの通知を、この画面を出したサーバーのエンドポイントに HTTP で送り、一覧に戻る。
     *
     * @param orderId 注文 ID
     * @param status  通知する配送状態（配送中、配達完了、返送）
     * @param request 要求（エンドポイントの URL を求めるため）
     * @return 一覧への移動。通知が受け付けられなかったときは、その旨を示した一覧
     */
    @PostMapping("/mock-carrier/orders/{orderId}/notify")
    ResponseEntity<String> notifyStatus(@PathVariable UUID orderId, @RequestParam DeliveryStatus status,
                                        HttpServletRequest request) {
        var shipment = shipmentRepository.findAllByOrderIds(List.of(orderId)).stream().findFirst();
        if (shipment.isEmpty() || !MockCarrierPageHtml.NOTIFIABLE.contains(status)) {
            return html(HttpStatus.NOT_FOUND, MockCarrierPageHtml.notFound());
        }
        if (shipment.get().carrier() != Carrier.YAMATO) {
            return render("この mock は、" + carrierName(Carrier.YAMATO) + "の表示の名前でだけ通知できます。");
        }
        var notification = mockCarrier.notification(shipment.get().carrier(), shipment.get().trackingNumber().value(),
                MockCarrier.yamatoNameOf(status));
        try {
            var result = mockCarrier.send(baseUrl(request), notification);
            if (result / 100 != 2) {
                return render("通知が受け付けられませんでした（HTTP " + result + "）。");
            }
        } catch (RestClientException e) {
            // 例外の文（接続先など）は画面に出さず、ログに残す
            log.warn("配送業者の mock の通知を送れない：orderId={}", orderId, e);
            return render("通知を送れませんでした。バックエンドのログを確認してください。");
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, "/mock-carrier").build();
    }

// ============================== プライベートメソッド ==============================

    /**
     * エンドポイントを持つ、このサーバーの URL（要求と同じ接続先）を求める。
     */
    private static String baseUrl(HttpServletRequest request) {
        return ServletUriComponentsBuilder.fromContextPath(request).build().toUriString();
    }

    /**
     * 出荷の一覧の画面を出す。
     *
     * @param error 示す失敗。なければ null
     */
    private ResponseEntity<String> render(@Nullable String error) {
        return html(HttpStatus.OK,
                MockCarrierPageHtml.list(shipmentQuery.findRecent(LIST_LIMIT), this::carrierName, error));
    }

    /**
     * 配送業者の表示の名前を、メッセージ（通知のメールと同じキー）から取る。
     */
    private String carrierName(Carrier carrier) {
        return messageHelper.getMessage("ecsite.notification.carrier." + carrier.name().toLowerCase(Locale.ROOT),
                Map.of());
    }

    /**
     * HTML の応答を作る。
     */
    private static ResponseEntity<String> html(HttpStatus status, String document) {
        return ResponseEntity.status(status).contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(document);
    }
}
