package com.ecsite.module.payment.infrastructure.stripe;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.payment.application.port.PaymentNotificationReceiver;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.time.Clock;

/**
 * Stripe の通知の受け取り（docs/03-機能仕様/payment.md「Stripe」）。<br />
 * 署名を確認してから、通知を決済サービスの型から直して、port へ渡す。
 * 扱う種類は、与信の成功（Checkout Session の完了）と、支払画面の期限切れ（Checkout Session の期限切れ）だけ。
 */
@Slf4j
class StripeNotificationHandler {

    /**
     * 署名を受け付ける時間（秒）。これより前に署名した通知は、盗んだ通知の再送とみなして拒否する（Stripe の既定）
     */
    private static final long TOLERANCE_SECONDS = Webhook.DEFAULT_TOLERANCE;

    /**
     * 与信の成功の通知の種類
     */
    private static final String SESSION_COMPLETED = "checkout.session.completed";

    /**
     * 支払画面の期限切れの通知の種類
     */
    private static final String SESSION_EXPIRED = "checkout.session.expired";

    /**
     * 通知の port
     */
    private final PaymentNotificationReceiver receiver;

    /**
     * 通知の署名の鍵
     */
    private final String webhookSecret;

    /**
     * 時計（署名の古さを判定する）
     */
    private final Clock clock;

    /**
     * @param receiver      通知の port
     * @param webhookSecret 通知の署名の鍵
     * @param clock         時計
     */
    StripeNotificationHandler(PaymentNotificationReceiver receiver, String webhookSecret, Clock clock) {
        this.receiver = receiver;
        this.webhookSecret = webhookSecret;
        this.clock = clock;
    }

    /**
     * 通知を受け取る。扱わない種類は、何もせずに受け付ける。
     *
     * @param payload         通知の本文（受け取ったまま。署名は本文のバイト列に対して付いている）
     * @param signatureHeader ヘッダー {@code Stripe-Signature}
     * @throws InvalidStripeNotificationException 署名がない・合わない・古いとき
     */
    void handle(String payload, @Nullable String signatureHeader) {
        var event = verify(payload, signatureHeader);
        switch (event.getType()) {
            case SESSION_COMPLETED -> {
                var session = session(event);
                receiver.authorized(session.getId(), session.getPaymentIntent());
            }
            case SESSION_EXPIRED -> receiver.pageExpired(session(event).getId());
            default -> log.debug("扱わない Stripe の通知を無視する: id={}, type={}", event.getId(), event.getType());
        }
    }

// ============================== プライベート ==============================

    /**
     * 署名を確認して、通知を読む。
     */
    private Event verify(String payload, @Nullable String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new InvalidStripeNotificationException("署名のない Stripe の通知", null);
        }
        try {
            return Webhook.constructEvent(payload, signatureHeader, webhookSecret, TOLERANCE_SECONDS, clock);
        } catch (SignatureVerificationException e) {
            throw new InvalidStripeNotificationException("署名を確認できない Stripe の通知: " + e.getMessage(), e);
        }
    }

    /**
     * 通知の Checkout Session を読む。<br />
     * 通知の API のバージョンがライブラリと違うと安全な読み方では読めないので、そのときは項目の名前で読む
     * （使う項目は ID と PaymentIntent の ID だけで、バージョンで変わらない）。
     */
    private static Session session(Event event) {
        var deserializer = event.getDataObjectDeserializer();
        try {
            StripeObject object = deserializer.getObject().isPresent()
                    ? deserializer.getObject().get()
                    : deserializer.deserializeUnsafe();
            return (Session) object;
        } catch (EventDataObjectDeserializationException | ClassCastException e) {
            // 再送でも直らないが、例外で返して Stripe の通知の記録に残す
            throw SystemException.of("Stripe の通知の Checkout Session を読めない: id=" + event.getId(), e);
        }
    }
}
