package com.ecsite.module.payment.infrastructure.stripe;

import com.ecsite.module.payment.application.port.PaymentNotificationReceiver;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stripe の通知の受け取りの単体テスト（T7403、BR-17）。<br />
 * 通知の中身は、sandbox で記録したもの（{@code src/test/resources/stripe/}）を使い、テストの鍵で署名する。
 */
class StripeNotificationHandlerTest {

    /**
     * テストの署名の鍵
     */
    private static final String WEBHOOK_SECRET = "whsec_test_secret";

    /**
     * 今の時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-14T01:00:00Z");

    /**
     * 記録した与信の成功の通知の、支払の画面 ID（Checkout Session の ID）
     */
    private static final String SESSION_ID = "cs_test_a1l1Todxlu6jrshMOHxtmijZ8KZp5kS23b1vuEz7n2yUXPUxhfh3irtv9o";

    /**
     * 記録した与信の成功の通知の、与信の参照（PaymentIntent の ID）
     */
    private static final String PAYMENT_INTENT_ID = "pi_3UQ8lrCajA2Qja1L0sDl4sX9";

    /**
     * 記録した期限切れの通知の、支払の画面 ID
     */
    private static final String EXPIRED_SESSION_ID = "cs_test_a1zewRjUghE0UY9BsbiwwxGmd9XVfiSll2NRfTbJcHbzBjHVBYFqfbZO2N";

    /**
     * 受け取った通知
     */
    private final RecordingReceiver receiver = new RecordingReceiver();

    /**
     * テストの対象
     */
    private final StripeNotificationHandler handler =
            new StripeNotificationHandler(receiver, WEBHOOK_SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("BR-17 署名の合う与信の成功の通知を、支払の画面 ID と与信の参照に直して渡す")
    void passesAuthorization() {
        var payload = recorded("checkout-session-completed.json");

        handler.handle(payload, sign(payload, WEBHOOK_SECRET, NOW));

        assertThat(receiver.received).containsExactly("authorized " + SESSION_ID + " " + PAYMENT_INTENT_ID);
    }

    @Test
    @DisplayName("BR-17 署名の合う期限切れの通知を、支払の画面の期限切れとして渡す")
    void passesPageExpiry() {
        var payload = recorded("checkout-session-expired.json");

        handler.handle(payload, sign(payload, WEBHOOK_SECRET, NOW));

        assertThat(receiver.received).containsExactly("pageExpired " + EXPIRED_SESSION_ID);
    }

    @Test
    @DisplayName("BR-17 扱わない種類の通知は、受け付けて何もしない")
    void ignoresOtherTypes() {
        var payload = recorded("payment-intent-canceled.json");

        handler.handle(payload, sign(payload, WEBHOOK_SECRET, NOW));

        assertThat(receiver.received).isEmpty();
    }

    @Test
    @DisplayName("BR-17 別の鍵で署名した通知は拒否し、何も渡さない")
    void rejectsWrongSecret() {
        var payload = recorded("checkout-session-completed.json");

        assertThatThrownBy(() -> handler.handle(payload, sign(payload, "whsec_other", NOW)))
                .isInstanceOf(InvalidStripeNotificationException.class);
        assertThat(receiver.received).isEmpty();
    }

    @Test
    @DisplayName("BR-17 署名の後に書き換えた通知は拒否する")
    void rejectsTamperedPayload() {
        var payload = recorded("checkout-session-completed.json");
        var signature = sign(payload, WEBHOOK_SECRET, NOW);
        var tampered = payload.replace(PAYMENT_INTENT_ID, "pi_attacker");

        assertThatThrownBy(() -> handler.handle(tampered, signature))
                .isInstanceOf(InvalidStripeNotificationException.class);
        assertThat(receiver.received).isEmpty();
    }

    @Test
    @DisplayName("BR-17 署名のない通知は拒否する")
    void rejectsMissingSignature() {
        var payload = recorded("checkout-session-completed.json");

        assertThatThrownBy(() -> handler.handle(payload, null))
                .isInstanceOf(InvalidStripeNotificationException.class);
        assertThatThrownBy(() -> handler.handle(payload, ""))
                .isInstanceOf(InvalidStripeNotificationException.class);
        assertThat(receiver.received).isEmpty();
    }

    @Test
    @DisplayName("BR-17 5 分より前に署名した古い通知は拒否する（盗んだ通知の再送を防ぐ）")
    void rejectsStaleSignature() {
        var payload = recorded("checkout-session-completed.json");
        var signedAt = NOW.minus(Duration.ofMinutes(5)).minusSeconds(1);

        assertThatThrownBy(() -> handler.handle(payload, sign(payload, WEBHOOK_SECRET, signedAt)))
                .isInstanceOf(InvalidStripeNotificationException.class);
        assertThat(receiver.received).isEmpty();
    }

    @Test
    @DisplayName("BR-17 5 分以内に署名した通知は受け付ける（Stripe の再送も署名し直される）")
    void acceptsRecentSignature() {
        var payload = recorded("checkout-session-expired.json");
        var signedAt = NOW.minus(Duration.ofMinutes(5)).plusSeconds(1);

        handler.handle(payload, sign(payload, WEBHOOK_SECRET, signedAt));

        assertThat(receiver.received).containsExactly("pageExpired " + EXPIRED_SESSION_ID);
    }

// ============================== プライベート ==============================

    /**
     * 記録した通知の本文を読む。
     */
    private static String recorded(String name) {
        try (var in = StripeNotificationHandlerTest.class.getResourceAsStream("/stripe/" + name)) {
            return new String(Objects.requireNonNull(in, name).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Stripe と同じ形式の署名のヘッダー（{@code t=<秒>,v1=<HMAC-SHA256>}）を作る。
     */
    private static String sign(String payload, String secret, Instant signedAt) {
        try {
            var timestamp = signedAt.getEpochSecond();
            var signature = Webhook.Util.computeHmacSha256(secret, timestamp + "." + payload);
            return "t=" + timestamp + ",v1=" + signature;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 受け取った通知を記録する受け口
     */
    private static final class RecordingReceiver implements PaymentNotificationReceiver {

        /**
         * 受け取った通知（受け取った順）
         */
        private final List<String> received = new ArrayList<>();

        @Override
        public void authorized(String pageId, String authorizationRef) {
            received.add("authorized " + pageId + " " + authorizationRef);
        }

        @Override
        public void pageExpired(String pageId) {
            received.add("pageExpired " + pageId);
        }
    }
}
