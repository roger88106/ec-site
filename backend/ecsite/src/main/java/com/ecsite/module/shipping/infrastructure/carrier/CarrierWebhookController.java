package com.ecsite.module.shipping.infrastructure.carrier;

import com.ecsite.module.shipping.application.port.ShipmentNotificationReceiver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 物流サービスの通知のエンドポイント
 *
 * <p>EC の API ではないので、OpenAPI に書かず、presentation に置かない。認証は付けず、署名で確認する。
 */
@Slf4j
@Controller
@RequiredArgsConstructor
class CarrierWebhookController {

    /**
     * 署名
     */
    private final CarrierSignature signature;

    /**
     * 状態の名前の対応の表
     */
    private final CarrierStatusMapping statusMapping;

    /**
     * 配送状態の通知の受け取り
     */
    private final ShipmentNotificationReceiver receiver;

    /**
     * JSON の読み取り
     */
    private final JsonMapper jsonMapper;

    /**
     * 通知を受け取る。
     *
     * @param payload         本文（署名を確認するため、受け取ったままのバイト列で受け取る）
     * @param signatureHeader ヘッダー {@code X-Carrier-Signature}
     * @return 受け付けたら 200（合う出荷がない通知と、対応の表にない状態の名前を含む）、署名を確認できなければ 401、
     *         署名が合っても本文が読めなければ 400。処理に失敗したら 5xx
     */
    @PostMapping(CarrierNotification.WEBHOOK_PATH)
    ResponseEntity<Void> receive(@RequestBody(required = false) byte @Nullable [] payload,
                                 @RequestHeader(name = CarrierSignature.HEADER, required = false) @Nullable String signatureHeader) {
        var body = payload == null ? new byte[0] : payload;
        if (!signature.verify(body, signatureHeader)) {
            log.warn("署名を確認できない物流サービスの通知を拒否した");
            return ResponseEntity.status(401).build();
        }
        var notification = read(body);
        if (notification == null) {
            return ResponseEntity.badRequest().build();
        }
        // 対応の表にない状態の名前と、合う出荷がない通知は、再送されても合うようにならないので、記録して 200 を返す。
        // 処理の失敗は 5xx にし、物流サービスの再送で処理し直す
        var status = statusMapping.resolve(notification.carrier(), notification.status());
        if (status.isEmpty()) {
            log.warn("対応の表にない状態の名前の通知を無視する: carrier={}, status={}", notification.carrier(), notification.status());
            return ResponseEntity.ok().build();
        }
        var result = receiver.receive(notification.carrier(), notification.trackingNumber(), status.get());
        log.info("物流サービスの通知を受けた: carrier={}, status={}, occurredAt={}, result={}",
                notification.carrier(), notification.status(), notification.occurredAt(), result);
        return ResponseEntity.ok().build();
    }

    /**
     * 本文を読む。
     *
     * @return 通知。読めない、または項目が足りなければ null
     */
    private @Nullable CarrierNotification read(byte[] body) {
        try {
            var notification = jsonMapper.readValue(body, CarrierNotification.class);
            if (notification == null || !notification.isComplete()) {
                log.warn("項目が足りない物流サービスの通知を拒否した");
                return null;
            }
            return notification;
        } catch (JacksonException e) {
            log.warn("読めない物流サービスの通知を拒否した: {}", e.getOriginalMessage());
            return null;
        }
    }
}
