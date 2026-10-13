package com.ecsite.module.payment.infrastructure.stripe;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Stripe の通知の受け口（docs/backend/modules/payment.md「Stripe」）。<br />
 * Stripe が呼ぶ口で、EC の API ではない（OpenAPI に書かず、presentation にも置かない）。認証は付けず、署名で確かめる。
 * 処理に失敗したら 5xx を返し、Stripe の再送で処理し直す。
 */
@Slf4j
@Controller
@ConditionalOnProperty(name = "ecsite.payment.gateway", havingValue = "stripe")
@RequiredArgsConstructor
class StripeWebhookController {

    /**
     * 通知の受け取り
     */
    private final StripeNotificationHandler handler;

    /**
     * 通知を受け取る。
     *
     * @param payload   本文（署名を確かめるため、文字列のまま受け取る）
     * @param signature ヘッダー {@code Stripe-Signature}
     * @return 受け付けたら 200、署名を確かめられなければ 400
     */
    @PostMapping("/api/webhooks/stripe")
    ResponseEntity<Void> receive(@RequestBody String payload,
                                 @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        try {
            handler.handle(payload, signature);
            return ResponseEntity.ok().build();
        } catch (InvalidStripeNotificationException e) {
            log.warn("Stripe の通知を拒否した: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
