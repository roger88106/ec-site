package com.ecsite.module.shipping.infrastructure.mock;

import com.ecsite.common.message.MessageResolver;
import com.ecsite.common.presentation.MockPageResponses;
import com.ecsite.module.shipping.application.port.ShipmentRepository;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * mock の物流サービスの画面
 *
 * <p>外部の物流サービスの代わりで、EC の API ではないので、OpenAPI に書かない。物流の mock のフラグを立てたときだけ出す。
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
    private final MessageResolver messageResolver;

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
     * 出荷の配送状態を通知する。
     *
     * <p>署名付きの通知を、この画面を出したサーバーのエンドポイントに HTTP で送り、一覧に戻る。
     *
     * @param orderId 注文 ID
     * @param status  通知する配送状態（配送中、配達完了、返送）
     * @param request リクエスト（エンドポイントの URL を求めるため）
     * @return 一覧への移動。通知が受け付けられなかったときは、その旨を示した一覧
     */
    @PostMapping("/mock-carrier/orders/{orderId}/notify")
    ResponseEntity<String> notifyStatus(@PathVariable UUID orderId, @RequestParam DeliveryStatus status,
                                        HttpServletRequest request) {
        var shipment = shipmentRepository.findAllByOrderIds(List.of(orderId)).stream().findFirst();
        if (shipment.isEmpty() || !MockCarrierPageHtml.NOTIFIABLE.contains(status)) {
            return MockPageResponses.html(HttpStatus.NOT_FOUND, MockCarrierPageHtml.notFound());
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
        return MockPageResponses.redirect(URI.create("/mock-carrier"));
    }

// ============================== プライベートメソッド ==============================

    /**
     * エンドポイントを持つ、このサーバーの URL を求める。
     *
     * <p>宛先は、リクエストを受けたローカルのポートの localhost にする。Host ヘッダーから作ると、
     * 書き換えたリクエストで、署名した通知を外のホストに送らせられるため。
     */
    private static String baseUrl(HttpServletRequest request) {
        return "http://localhost:" + request.getLocalPort() + request.getContextPath();
    }

    /**
     * 出荷の一覧の画面を出す。
     *
     * @param error 示す失敗。なければ null
     */
    private ResponseEntity<String> render(@Nullable String error) {
        return MockPageResponses.html(HttpStatus.OK,
                MockCarrierPageHtml.list(shipmentQuery.findRecent(LIST_LIMIT), this::carrierName, error));
    }

    /**
     * 配送業者の表示の名前を、出荷モジュールのメッセージから取る。
     */
    private String carrierName(Carrier carrier) {
        return messageResolver.getMessage(carrier.nameMessageKey(), Map.of());
    }
}
