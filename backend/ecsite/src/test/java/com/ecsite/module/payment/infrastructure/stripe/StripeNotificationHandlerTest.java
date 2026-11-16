package com.ecsite.module.payment.infrastructure.stripe;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.module.payment.application.port.PaymentNotificationReceiver;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

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

import static com.ecsite.support.BusinessErrorAssertions.assertSystemError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stripe の通知の受け取りの単体テスト
 *
 * <p>通知の中身は、sandbox で記録したもの（{@code src/test/resources/stripe/}）を使い、テストの鍵で署名する。
 */
class StripeNotificationHandlerTest {

    // ============================== 定数 ==============================

    /**
     * テストの署名の鍵
     */
    private static final String WEBHOOK_SECRET = "whsec_test_secret";

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-14 10:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-14T01:00:00Z");

    /**
     * 記録した与信の成功の通知の、支払画面 ID（Checkout Session の ID）
     */
    private static final String SESSION_ID = "cs_test_a1l1Todxlu6jrshMOHxtmijZ8KZp5kS23b1vuEz7n2yUXPUxhfh3irtv9o";

    /**
     * 記録した与信の成功の通知の、与信の参照（PaymentIntent の ID）
     */
    private static final String PAYMENT_INTENT_ID = "pi_3UQ8lrCajA2Qja1L0sDl4sX9";

    /**
     * 記録した期限切れの通知の、支払画面 ID
     */
    private static final String EXPIRED_SESSION_ID = "cs_test_a1zewRjUghE0UY9BsbiwwxGmd9XVfiSll2NRfTbJcHbzBjHVBYFqfbZO2N";

    // ============================== 対象 ==============================

    /**
     * 受け取った通知
     */
    private final RecordingReceiver receiver = new RecordingReceiver();

    /**
     * テストの対象
     */
    private final StripeNotificationHandler handler =
            new StripeNotificationHandler(receiver, WEBHOOK_SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

    // ============================== テスト ==============================

    @Nested
    @DisplayName("受け付ける通知")
    class Accepted {

        @Test
        @DisplayName("BR-17 署名の合う与信の成功の通知を、支払画面 ID と与信の参照に直して渡す")
        void passesAuthorization() {
            // 準備：記録した与信の成功の通知
            var payload = recorded("checkout-session-completed.json");

            // 操作：正しい鍵の署名で受け取る
            handler.handle(payload, sign(payload, WEBHOOK_SECRET, NOW));

            // 確認：支払画面 ID と与信の参照を渡した
            assertThat(receiver.received).containsExactly("authorized " + SESSION_ID + " " + PAYMENT_INTENT_ID);
        }

        @Test
        @DisplayName("BR-17 署名の合う期限切れの通知を、支払画面の期限切れとして渡す")
        void passesPageExpiry() {
            // 準備：記録した期限切れの通知
            var payload = recorded("checkout-session-expired.json");

            // 操作：正しい鍵の署名で受け取る
            handler.handle(payload, sign(payload, WEBHOOK_SECRET, NOW));

            // 確認：支払画面の期限切れを渡した
            assertThat(receiver.received).containsExactly("pageExpired " + EXPIRED_SESSION_ID);
        }

        @Test
        @DisplayName("BR-17 扱わない種類の通知は、受け付けて何もしない")
        void ignoresOtherTypes() {
            // 準備：記録した PaymentIntent の取消の通知（扱わない種類）
            var payload = recorded("payment-intent-canceled.json");

            // 操作：正しい鍵の署名で受け取る
            handler.handle(payload, sign(payload, WEBHOOK_SECRET, NOW));

            // 確認：何も渡さない
            assertThat(receiver.received).isEmpty();
        }

        @Test
        @DisplayName("BR-17 5 分以内に署名した通知は受け付ける（Stripe の再送も署名し直される）")
        void acceptsRecentSignature() {
            // 準備：5 分より 1 秒新しい署名の通知
            var payload = recorded("checkout-session-expired.json");
            var signedAt = NOW.minus(Duration.ofMinutes(5)).plusSeconds(1);

            // 操作：受け取る
            handler.handle(payload, sign(payload, WEBHOOK_SECRET, signedAt));

            // 確認：渡した
            assertThat(receiver.received).containsExactly("pageExpired " + EXPIRED_SESSION_ID);
        }
    }

    @Nested
    @DisplayName("通知の中身の読み方")
    class Payload {

        /**
         * 記録した通知の API のバージョン
         */
        private static final String RECORDED_API_VERSION = "\"api_version\": \"2026-09-30.endive\"";

        /**
         * ライブラリと違う、古い API のバージョン
         */
        private static final String OLD_API_VERSION = "\"api_version\": \"2020-08-27\"";

        @Test
        @DisplayName("BR-17 ライブラリと違う API のバージョンの通知からも、支払画面 ID と与信の参照を読んで渡す")
        void readsOtherApiVersion() {
            // 準備：記録した与信の成功の通知を、古い API のバージョンにしたもの
            var payload = recorded("checkout-session-completed.json").replace(RECORDED_API_VERSION, OLD_API_VERSION);

            // 操作：正しい鍵の署名で受け取る
            handler.handle(payload, sign(payload, WEBHOOK_SECRET, NOW));

            // 確認：支払画面 ID と与信の参照を渡した
            assertThat(payload).contains(OLD_API_VERSION);
            assertThat(receiver.received).containsExactly("authorized " + SESSION_ID + " " + PAYMENT_INTENT_ID);
        }

        @Test
        @DisplayName("BR-17 与信の成功の種類なのに Checkout Session でない通知は、システムのエラーにし、何も渡さない")
        void rejectsNonSessionObject() {
            // 準備：PaymentIntent の通知の種類だけを、与信の成功（Checkout Session の完了）にしたもの
            var payload = recorded("payment-intent-canceled.json")
                    .replace("\"type\": \"payment_intent.canceled\"", "\"type\": \"checkout.session.completed\"");
            var signature = sign(payload, WEBHOOK_SECRET, NOW);

            // 操作と確認：システムのエラーになり、何も渡さない
            assertThat(payload).contains("checkout.session.completed");
            assertSystemError(() -> handler.handle(payload, signature), CommonErrorCode.SYSTEM_ERROR);
            assertThat(receiver.received).isEmpty();
        }
    }

    @Nested
    @DisplayName("拒否する通知")
    class Rejected {

        @Test
        @DisplayName("BR-17 別の鍵で署名した通知は拒否し、何も渡さない")
        void rejectsWrongSecret() {
            // 準備：別の鍵で署名した通知
            var payload = recorded("checkout-session-completed.json");
            var signature = sign(payload, "whsec_other", NOW);

            // 操作と確認：拒否し、何も渡さない
            assertRejected(payload, signature);
        }

        @Test
        @DisplayName("BR-17 署名の後に書き換えた通知は拒否する")
        void rejectsTamperedPayload() {
            // 準備：署名の後に与信の参照を書き換えた通知
            var payload = recorded("checkout-session-completed.json");
            var signature = sign(payload, WEBHOOK_SECRET, NOW);
            var tampered = payload.replace(PAYMENT_INTENT_ID, "pi_attacker");

            // 操作と確認：拒否し、何も渡さない
            assertRejected(tampered, signature);
        }

        @ParameterizedTest(name = "「{0}」")
        @NullAndEmptySource
        @DisplayName("BR-17 署名のない通知は拒否する")
        void rejectsMissingSignature(String signature) {
            // 準備：記録した与信の成功の通知
            var payload = recorded("checkout-session-completed.json");

            // 操作と確認：拒否し、何も渡さない
            assertRejected(payload, signature);
        }

        @Test
        @DisplayName("BR-17 5 分より前に署名した古い通知は拒否する（盗んだ通知の再送を防ぐ）")
        void rejectsStaleSignature() {
            // 準備：5 分より 1 秒古い署名の通知
            var payload = recorded("checkout-session-completed.json");
            var signature = sign(payload, WEBHOOK_SECRET, NOW.minus(Duration.ofMinutes(5)).minusSeconds(1));

            // 操作と確認：拒否し、何も渡さない
            assertRejected(payload, signature);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 通知を拒否し、何も渡さないことを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>不正な通知の例外になる</li>
     *   <li>port に何も渡さない</li>
     * </ul>
     *
     * @param payload   通知の本文
     * @param signature 署名のヘッダーの値
     */
    private void assertRejected(String payload, String signature) {
        assertThatThrownBy(() -> handler.handle(payload, signature))
                .isInstanceOf(InvalidStripeNotificationException.class);
        assertThat(receiver.received).isEmpty();
    }

    // ============================== 準備の補助 ==============================

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
     * 受け取った通知を記録する port
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
