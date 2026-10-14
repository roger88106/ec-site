package com.ecsite.module.payment.domain.payment;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.event.PaymentAuthorized;
import com.ecsite.module.payment.event.PaymentExpired;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 支払の単体テスト（T7202、BR-15、17、AC-157）
 */
class PaymentTest {

    /**
     * 注文日時
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 支払の期限（注文日時 ＋ 30 分）
     */
    private static final Instant DEADLINE = ORDERED_AT.plus(Duration.ofMinutes(30));

    /**
     * 与信の参照
     */
    private static final String AUTHORIZATION_REF = "pi_test_1";

    @Test
    @DisplayName("BR-15 支払は「未払い」で作られ、支払の画面はまだない")
    void createsUnpaid() {
        var orderId = UUID.randomUUID();

        var payment = Payment.create(UUID.randomUUID(), orderId, Money.of(6940), DEADLINE);

        assertThat(payment.status()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(payment.orderId()).isEqualTo(orderId);
        assertThat(payment.amount()).isEqualTo(Money.of(6940));
        assertThat(payment.deadline()).isEqualTo(DEADLINE);
        assertThat(payment.page()).isEmpty();
        assertThat(payment.pullEvents()).isEmpty();
    }

    @Nested
    @DisplayName("支払の画面")
    class Page {

        @Test
        @DisplayName("未払いで期限の前なら支払える。開いた画面を覚える")
        void opensPageWhilePayable() {
            var payment = unpaid();
            var page = new PaymentPage("cs_test_1", "https://pay.example/cs_test_1");

            assertThat(payment.isPayable(DEADLINE.minusSeconds(1))).isTrue();
            payment.openPage(page);

            assertThat(payment.page()).contains(page);
        }

        @Test
        @DisplayName("期限の時刻を過ぎたら、未払いでも支払えない")
        void notPayableAfterDeadline() {
            assertThat(unpaid().isPayable(DEADLINE)).isFalse();
        }

        @Test
        @DisplayName("与信済みの支払は、支払えない")
        void notPayableWhenAuthorized() {
            assertThat(authorized().isPayable(ORDERED_AT)).isFalse();
        }
    }

    @Nested
    @DisplayName("与信の成功の通知")
    class Authorize {

        @Test
        @DisplayName("AC-146 未払い → 与信済み。与信の参照を覚え、与信の成功のイベントが登録される")
        void authorizesUnpaid() {
            var payment = unpaid();

            payment.authorize(AUTHORIZATION_REF, ORDERED_AT);

            assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
            assertThat(payment.authorizationRef()).isEqualTo(AUTHORIZATION_REF);
            assertThat(payment.pullEvents()).singleElement().isInstanceOfSatisfying(PaymentAuthorized.class,
                    event -> assertThat(event.orderId()).isEqualTo(payment.orderId()));
        }

        @Test
        @DisplayName("BR-17 与信済みに重ねて届いても、何も変わらず、イベントも出ない")
        void ignoresDuplicate() {
            var payment = authorized();

            payment.authorize("pi_test_other", ORDERED_AT);

            assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
            assertThat(payment.authorizationRef()).isEqualTo(AUTHORIZATION_REF);
            assertThat(payment.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("AC-157 支払なし（期限切れ）の後に届いたら、与信の取消待ちになり、取消の依頼が登録される。与信の成功のイベントは出ない")
        void voidsLateAuthorization() {
            var payment = expired();

            payment.authorize(AUTHORIZATION_REF, ORDERED_AT);

            assertThat(payment.status()).isEqualTo(PaymentStatus.VOID_PENDING);
            assertThat(payment.authorizationRef()).isEqualTo(AUTHORIZATION_REF);
            assertThat(payment.pullEvents()).singleElement().isInstanceOf(VoidRequested.class);
        }

        @Test
        @DisplayName("BR-17 与信の取消済みに届いても（順序の入れ替わり）、何も変わらない")
        void ignoresAfterVoided() {
            var payment = voided();

            payment.authorize(AUTHORIZATION_REF, ORDERED_AT);

            assertThat(payment.status()).isEqualTo(PaymentStatus.VOIDED);
            assertThat(payment.pullEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("期限切れ")
    class Expire {

        @Test
        @DisplayName("AC-153 未払い → 支払なし。支払の期限切れのイベントが登録される")
        void expiresUnpaid() {
            var payment = unpaid();

            var changed = payment.expire(DEADLINE);

            assertThat(changed).isTrue();
            assertThat(payment.status()).isEqualTo(PaymentStatus.NOT_PAID);
            assertThat(payment.pullEvents()).singleElement().isInstanceOfSatisfying(PaymentExpired.class,
                    event -> assertThat(event.orderId()).isEqualTo(payment.orderId()));
        }

        @Test
        @DisplayName("AC-156 与信済みの支払は、期限切れにしない")
        void doesNotExpireAuthorized() {
            var payment = authorized();

            var changed = payment.expire(DEADLINE);

            assertThat(changed).isFalse();
            assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
            assertThat(payment.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("NFR-7 支払なしを重ねて期限切れにしても、イベントは出ない")
        void expiresOnlyOnce() {
            var payment = expired();

            var changed = payment.expire(DEADLINE);

            assertThat(changed).isFalse();
            assertThat(payment.pullEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("取消（注文のキャンセル）")
    class Cancel {

        @Test
        @DisplayName("AC-162 与信済み → 与信の取消待ち。取消の依頼が登録される")
        void requestsVoidOfAuthorized() {
            var payment = authorized();

            payment.cancel(ORDERED_AT);

            assertThat(payment.status()).isEqualTo(PaymentStatus.VOID_PENDING);
            assertThat(payment.pullEvents()).singleElement().isInstanceOfSatisfying(VoidRequested.class,
                    event -> assertThat(event.paymentId()).isEqualTo(payment.id()));
        }

        @Test
        @DisplayName("未払い → 支払なし。取り消す与信はないので、取消の依頼は出ない")
        void cancelsUnpaid() {
            var payment = unpaid();

            payment.cancel(ORDERED_AT);

            assertThat(payment.status()).isEqualTo(PaymentStatus.NOT_PAID);
            assertThat(payment.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("AC-166 与信の取消待ち・取消済みに重ねても、何も変わらない")
        void ignoresRepeatedCancel() {
            var pending = authorized();
            pending.cancel(ORDERED_AT);
            pending.pullEvents();
            var voided = voided();

            pending.cancel(ORDERED_AT);
            voided.cancel(ORDERED_AT);

            assertThat(pending.status()).isEqualTo(PaymentStatus.VOID_PENDING);
            assertThat(pending.pullEvents()).isEmpty();
            assertThat(voided.status()).isEqualTo(PaymentStatus.VOIDED);
            assertThat(voided.pullEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("取消済みにする")
    class MarkVoided {

        @Test
        @DisplayName("与信の取消待ち → 与信の取消済み")
        void marksVoided() {
            assertThat(voided().status()).isEqualTo(PaymentStatus.VOIDED);
        }

        @Test
        @DisplayName("与信の取消待ちでなければ、何も変わらない")
        void ignoresWhenNotPending() {
            var payment = authorized();

            payment.markVoided();

            assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        }
    }

    @Nested
    @DisplayName("売上確定")
    class Capture {

        @Test
        @DisplayName("BR-21 与信済み → 売上確定済み")
        void capturesAuthorized() {
            var payment = authorized();

            payment.capture();

            assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
            assertThat(payment.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("BR-21 売上確定済みに重ねて確定しても、何も変わらない")
        void ignoresRepeatedCapture() {
            var payment = captured();

            payment.capture();

            assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
            assertThat(payment.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("BR-21 与信済みでない支払（未払い・与信期限切れ）は、売上を確定しない")
        void doesNotCaptureOthers() {
            var unpaid = unpaid();
            var authorizationExpired = authorizationExpired();

            unpaid.capture();
            authorizationExpired.capture();

            assertThat(unpaid.status()).isEqualTo(PaymentStatus.UNPAID);
            assertThat(authorizationExpired.status()).isEqualTo(PaymentStatus.AUTHORIZATION_EXPIRED);
        }

        @Test
        @DisplayName("BR-21 売上確定済みの支払に、与信の成功の通知が届いても（順序の入れ替わり）、何も変わらない")
        void ignoresAuthorizationAfterCapture() {
            var payment = captured();

            payment.authorize("pi_test_other", ORDERED_AT);

            assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
            assertThat(payment.authorizationRef()).isEqualTo(AUTHORIZATION_REF);
            assertThat(payment.pullEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("与信期限切れにする")
    class ExpireAuthorization {

        @Test
        @DisplayName("BR-21 与信済み → 与信期限切れ")
        void expiresAuthorized() {
            var payment = authorized();

            payment.expireAuthorization();

            assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZATION_EXPIRED);
            assertThat(payment.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("BR-21 重ねて与信期限切れにしても、何も変わらない")
        void ignoresRepeated() {
            var payment = authorizationExpired();

            payment.expireAuthorization();

            assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZATION_EXPIRED);
        }

        @Test
        @DisplayName("BR-21 売上確定済みの支払は、与信期限切れにしない")
        void doesNotExpireCaptured() {
            var payment = captured();

            payment.expireAuthorization();

            assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
        }
    }

    @Nested
    @DisplayName("返金")
    class Refund {

        @Test
        @DisplayName("BR-21 売上確定済み → 返金待ち。返金の依頼が登録される")
        void requestsRefundOfCaptured() {
            var payment = captured();

            payment.cancel(ORDERED_AT);

            assertThat(payment.status()).isEqualTo(PaymentStatus.REFUND_PENDING);
            assertThat(payment.pullEvents()).singleElement().isInstanceOfSatisfying(RefundRequested.class,
                    event -> assertThat(event.paymentId()).isEqualTo(payment.id()));
        }

        @Test
        @DisplayName("BR-21 返金待ち → 返金済み")
        void marksRefunded() {
            var payment = refundPending();

            payment.markRefunded();

            assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
        }

        @Test
        @DisplayName("BR-21 返金待ちでなければ、返金済みにしない")
        void ignoresMarkRefundedWhenNotPending() {
            var payment = captured();

            payment.markRefunded();

            assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
        }

        @Test
        @DisplayName("BR-21 返金待ち・返金済みに重ねて取り消しても、何も変わらず、返金の依頼も出ない")
        void ignoresRepeatedCancel() {
            var pending = refundPending();
            var refunded = refundPending();
            refunded.markRefunded();

            pending.cancel(ORDERED_AT);
            refunded.cancel(ORDERED_AT);
            refunded.markRefunded();

            assertThat(pending.status()).isEqualTo(PaymentStatus.REFUND_PENDING);
            assertThat(pending.pullEvents()).isEmpty();
            assertThat(refunded.status()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(refunded.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("BR-21 与信期限切れの支払を取り消しても、何もしない（取消も返金の依頼も出ない）")
        void doesNothingForAuthorizationExpired() {
            var payment = authorizationExpired();

            payment.cancel(ORDERED_AT);

            assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZATION_EXPIRED);
            assertThat(payment.pullEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("与信期限")
    class AuthorizationDeadline {

        /**
         * 与信の時刻
         */
        private static final Instant AUTHORIZED_AT = Instant.parse("2026-10-08T00:00:00Z");

        /**
         * 与信期限（与信の時刻 ＋ 7 日）
         */
        private static final Instant AUTHORIZATION_DEADLINE = AUTHORIZED_AT.plus(Duration.ofDays(7));

        /**
         * 与信の有効期間 7 日、期限が近いとする長さ 2 日
         */
        private final AuthorizationTerm term = new AuthorizationTerm(Duration.ofDays(7), Duration.ofDays(2));

        @Test
        @DisplayName("BR-20 与信の成功を受けたときの時刻を覚え、与信期限は与信の時刻の 7 日後")
        void recordsAuthorizedAt() {
            var payment = unpaid();

            payment.authorize(AUTHORIZATION_REF, AUTHORIZED_AT);

            assertThat(payment.authorizedAt()).contains(AUTHORIZED_AT);
            assertThat(payment.authorizationDeadline(term)).contains(AUTHORIZATION_DEADLINE);
        }

        @Test
        @DisplayName("BR-20 与信がなければ、与信の時刻も与信期限もない")
        void noDeadlineWithoutAuthorization() {
            var payment = unpaid();

            assertThat(payment.authorizedAt()).isEmpty();
            assertThat(payment.authorizationDeadline(term)).isEmpty();
            assertThat(payment.isAuthorizationExpiring(term, AUTHORIZATION_DEADLINE)).isFalse();
        }

        @Test
        @DisplayName("BR-20 重ねて届いた与信の成功の通知で、与信の時刻は変わらない")
        void keepsAuthorizedAtOnDuplicate() {
            var payment = unpaid();
            payment.authorize(AUTHORIZATION_REF, AUTHORIZED_AT);

            payment.authorize(AUTHORIZATION_REF, AUTHORIZED_AT.plusSeconds(60));

            assertThat(payment.authorizedAt()).contains(AUTHORIZED_AT);
        }

        @Test
        @DisplayName("BR-20 与信期限の 2 日前より前は、期限が近くない。2 日前の時刻から近い")
        void expiringFromWarningStart() {
            var payment = authorizedAt(AUTHORIZED_AT);
            var warningStart = AUTHORIZATION_DEADLINE.minus(Duration.ofDays(2));

            assertThat(payment.isAuthorizationExpiring(term, warningStart.minusSeconds(1))).isFalse();
            assertThat(payment.isAuthorizationExpiring(term, warningStart)).isTrue();
        }

        @Test
        @DisplayName("BR-20 与信期限を過ぎたものも、期限が近いに含む")
        void expiringIncludesPastDeadline() {
            var payment = authorizedAt(AUTHORIZED_AT);

            assertThat(payment.isAuthorizationExpiring(term, AUTHORIZATION_DEADLINE)).isTrue();
            assertThat(payment.isAuthorizationExpiring(term, AUTHORIZATION_DEADLINE.plus(Duration.ofDays(3)))).isTrue();
        }

        @Test
        @DisplayName("BR-20 与信期限切れの支払も、期限が近いに含む")
        void expiringIncludesAuthorizationExpired() {
            var payment = authorizedAt(AUTHORIZED_AT);
            payment.expireAuthorization();

            assertThat(payment.isAuthorizationExpiring(term, AUTHORIZATION_DEADLINE.plusSeconds(1))).isTrue();
        }

        @Test
        @DisplayName("BR-20 売上確定済みの支払は、期限が近くない")
        void capturedIsNotExpiring() {
            var payment = authorizedAt(AUTHORIZED_AT);
            payment.capture();

            assertThat(payment.isAuthorizationExpiring(term, AUTHORIZATION_DEADLINE.plusSeconds(1))).isFalse();
        }

        @Test
        @DisplayName("BR-20 期限が近い与信の、与信の時刻の上限は、現在時刻から（有効期間 − 近いとする長さ）を引いた時刻")
        void latestExpiringAuthorizedAt() {
            var now = Instant.parse("2026-10-20T00:00:00Z");

            assertThat(term.latestExpiringAuthorizedAt(now)).isEqualTo(now.minus(Duration.ofDays(5)));
        }
    }

    // ============================== 補助 ==============================

    private static Payment unpaid() {
        return Payment.create(UUID.randomUUID(), UUID.randomUUID(), Money.of(6940), DEADLINE);
    }

    private static Payment authorized() {
        var payment = unpaid();
        payment.authorize(AUTHORIZATION_REF, ORDERED_AT);
        payment.pullEvents();
        return payment;
    }

    private static Payment expired() {
        var payment = unpaid();
        payment.expire(DEADLINE);
        payment.pullEvents();
        return payment;
    }

    private static Payment authorizedAt(Instant authorizedAt) {
        var payment = unpaid();
        payment.authorize(AUTHORIZATION_REF, authorizedAt);
        payment.pullEvents();
        return payment;
    }

    private static Payment captured() {
        var payment = authorized();
        payment.capture();
        payment.pullEvents();
        return payment;
    }

    private static Payment authorizationExpired() {
        var payment = authorized();
        payment.expireAuthorization();
        payment.pullEvents();
        return payment;
    }

    private static Payment refundPending() {
        var payment = captured();
        payment.cancel(ORDERED_AT);
        payment.pullEvents();
        return payment;
    }

    private static Payment voided() {
        var payment = authorized();
        payment.cancel(ORDERED_AT);
        payment.markVoided();
        payment.pullEvents();
        return payment;
    }
}
