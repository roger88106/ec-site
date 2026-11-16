package com.ecsite.module.payment.domain.payment;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.event.PaymentAuthorized;
import com.ecsite.module.payment.event.PaymentExpired;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 支払の単体テスト
 *
 * <p>支払の状態の遷移と、遷移で登録するイベント、与信期限の計算を確認する。
 *
 * <p>準備のデータと確認の期待値は、同じ定数から作る（準備した物から期待値を取り出さない）。<br />
 * ただし、注文 ID と支払 ID は準備で毎回作るので、準備した支払から取り出して比べる。
 */
class PaymentTest {

    // ============================== 定数 ==============================

    /**
     * 注文日時
     *
     * <p>日本時間の 2026-10-08 00:30。
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 支払の期限（注文日時 ＋ 30 分）
     */
    private static final Instant DEADLINE = ORDERED_AT.plus(Duration.ofMinutes(30));

    /**
     * 支払の金額
     */
    private static final Money AMOUNT = Money.of(6940);

    /**
     * 与信の参照
     */
    private static final String AUTHORIZATION_REF = "pi_test_1";

    /**
     * 後から届いた別の与信の参照
     */
    private static final String OTHER_AUTHORIZATION_REF = "pi_test_other";

    // ============================== テスト ==============================

    @Nested
    @DisplayName("支払の作成")
    class Create {

        @Test
        @DisplayName("BR-15 支払は「未払い」で作られ、支払画面はまだない")
        void createsUnpaid() {
            // 準備：注文 ID
            var orderId = UUID.randomUUID();

            // 操作：支払を作る
            var payment = Payment.create(UUID.randomUUID(), orderId, AMOUNT, DEADLINE);

            // 確認：未払いで、渡した値を持ち、画面もイベントもない
            assertCreatedUnpaid(payment, orderId);
        }
    }

    @Nested
    @DisplayName("支払画面")
    class Page {

        @Test
        @DisplayName("未払いで期限の前なら支払える")
        void payableBeforeDeadline() {
            // 操作と確認：期限の 1 秒前は支払える
            assertThat(unpaid().isPayable(DEADLINE.minusSeconds(1))).isTrue();
        }

        @Test
        @DisplayName("開いた支払画面を覚える")
        void opensPage() {
            // 準備：未払いの支払と、開いた画面
            var payment = unpaid();
            var page = new PaymentPage("cs_test_1", "https://pay.example/cs_test_1");

            // 操作：画面を開く
            payment.openPage(page);

            // 確認：画面を覚えている
            assertThat(payment.page()).contains(page);
        }

        @Test
        @DisplayName("期限の時刻を過ぎたら、未払いでも支払えない")
        void notPayableAfterDeadline() {
            // 操作と確認：期限の時刻ちょうどは支払えない
            assertThat(unpaid().isPayable(DEADLINE)).isFalse();
        }

        @Test
        @DisplayName("与信済みの支払は、支払えない")
        void notPayableWhenAuthorized() {
            // 操作と確認：期限の前でも支払えない
            assertThat(authorized().isPayable(ORDERED_AT)).isFalse();
        }
    }

    @Nested
    @DisplayName("与信の成功の通知")
    class Authorize {

        @Test
        @DisplayName("AC-146 未払い → 与信済み。与信の参照を覚え、与信の成功のイベントが登録される")
        void authorizesUnpaid() {
            // 準備：未払いの支払
            var payment = unpaid();

            // 操作：与信の成功を反映する
            var authorized = payment.authorize(AUTHORIZATION_REF, ORDERED_AT);

            // 確認：与信済みになり、与信の成功のイベントが 1 件
            assertThat(authorized).isTrue();
            assertAuthorized(payment);
            assertThat(payment.pullEvents()).singleElement().isInstanceOfSatisfying(PaymentAuthorized.class,
                    event -> assertThat(event.orderId()).isEqualTo(payment.orderId()));
        }

        @Test
        @DisplayName("BR-17 与信済みに重ねて届いても、何も変わらず、イベントも出ない")
        void ignoresDuplicate() {
            // 準備：与信済みの支払
            var payment = authorized();

            // 操作：別の与信の成功を反映する
            var authorized = payment.authorize(OTHER_AUTHORIZATION_REF, ORDERED_AT);

            // 確認：反映せず、最初の与信のまま、イベントもない
            assertThat(authorized).isFalse();
            assertAuthorized(payment);
            assertThat(payment.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("AC-157 支払なし（期限切れ）の後に届いたら、与信の取消待ちになり、取消の依頼が登録される。与信の成功のイベントは出ない")
        void voidsLateAuthorization() {
            // 準備：期限切れで支払なしの支払
            var payment = expired();

            // 操作：与信の成功を反映する
            var authorized = payment.authorize(AUTHORIZATION_REF, ORDERED_AT);

            // 確認：与信済みにせず、届いた与信の取消を依頼する
            assertThat(authorized).isFalse();
            assertThat(payment.status()).isEqualTo(PaymentStatus.VOID_PENDING);
            assertThat(payment.authorizationRef()).isEqualTo(AUTHORIZATION_REF);
            assertThat(payment.pullEvents()).singleElement().isInstanceOf(VoidRequested.class);
        }

        @Test
        @DisplayName("AC-157 支払なしの支払に与信の時刻を付けて反映しても、与信の取消待ちになり、与信の時刻は覚えず、与信の成功のイベントも出ない")
        void voidsLateAuthorizationWithAuthorizedAt() {
            // 準備：期限切れで支払なしの支払
            var payment = expired();

            // 操作：与信の時刻を付けて反映する
            payment.authorize(AUTHORIZATION_REF, ORDERED_AT.plusSeconds(10), ORDERED_AT.plusSeconds(20));

            // 確認：取消待ちで、与信の時刻はなく、取消の依頼だけ
            assertThat(payment.status()).isEqualTo(PaymentStatus.VOID_PENDING);
            assertThat(payment.authorizedAt()).isEmpty();
            assertThat(payment.pullEvents()).singleElement().isInstanceOf(VoidRequested.class);
        }

        @Test
        @DisplayName("AC-409-2 与信の時刻を渡すと、未払い → 与信済みで、与信の時刻は渡した時刻、イベントの発生時刻は現在時刻")
        void authorizesWithAuthorizedAt() {
            // 準備：未払いの支払と、決済サービスの与信の時刻・現在時刻
            var payment = unpaid();
            var authorizedAt = ORDERED_AT.plusSeconds(10);
            var now = ORDERED_AT.plus(Duration.ofHours(1));

            // 操作：与信の時刻を付けて反映する
            payment.authorize(AUTHORIZATION_REF, authorizedAt, now);

            // 確認：与信済みで、与信の時刻は渡した時刻、イベントは現在時刻
            assertAuthorized(payment);
            assertThat(payment.authorizedAt()).contains(authorizedAt);
            assertThat(payment.pullEvents()).singleElement().isInstanceOfSatisfying(PaymentAuthorized.class,
                    event -> assertThat(event.occurredAt()).isEqualTo(now));
        }

        @Test
        @DisplayName("AC-409-4 与信済みの支払に、別の与信を再度反映しても、状態・参照・与信の時刻は変わらず、イベントも出ない")
        void keepsFirstAuthorizationWithAuthorizedAt() {
            // 準備：与信の時刻を付けて与信済みにした支払
            var authorizedAt = ORDERED_AT.plusSeconds(10);
            var payment = unpaid();
            payment.authorize(AUTHORIZATION_REF, authorizedAt, ORDERED_AT.plusSeconds(20));
            payment.pullEvents();

            // 操作：別の与信を、別の時刻で反映する
            payment.authorize(OTHER_AUTHORIZATION_REF, ORDERED_AT.plusSeconds(30), ORDERED_AT.plusSeconds(40));

            // 確認：最初の与信のまま、イベントもない
            assertAuthorized(payment);
            assertThat(payment.authorizedAt()).contains(authorizedAt);
            assertThat(payment.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("BR-17 与信の取消済みに届いても（順序の入れ替わり）、何も変わらない")
        void ignoresAfterVoided() {
            // 準備：与信の取消済みの支払
            var payment = voided();

            // 操作：与信の成功を反映する
            payment.authorize(AUTHORIZATION_REF, ORDERED_AT);

            // 確認：取消済みのまま、イベントもない
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
            // 準備：未払いの支払
            var payment = unpaid();

            // 操作：期限切れにする
            var changed = payment.expire(DEADLINE);

            // 確認：支払なしで、この注文の期限切れのイベントが 1 件
            assertThat(changed).isTrue();
            assertThat(payment.status()).isEqualTo(PaymentStatus.NOT_PAID);
            assertThat(payment.pullEvents()).singleElement().isInstanceOfSatisfying(PaymentExpired.class,
                    event -> assertThat(event.orderId()).isEqualTo(payment.orderId()));
        }

        @Test
        @DisplayName("AC-156 与信済みの支払は、期限切れにしない")
        void doesNotExpireAuthorized() {
            // 準備：与信済みの支払
            var payment = authorized();

            // 操作：期限切れにする
            var changed = payment.expire(DEADLINE);

            // 確認：与信済みのまま、イベントもない
            assertThat(changed).isFalse();
            assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
            assertThat(payment.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("NFR-7 支払なしを重ねて期限切れにしても、イベントは出ない")
        void expiresOnlyOnce() {
            // 準備：期限切れで支払なしの支払
            var payment = expired();

            // 操作：重ねて期限切れにする
            var changed = payment.expire(DEADLINE);

            // 確認：変わらず、イベントもない
            assertThat(changed).isFalse();
            assertThat(payment.pullEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("与信の反映を待つ")
    class AwaitAuthorization {

        /**
         * 期限切れのジョブが、決済サービスで支払済みと知った時刻
         */
        private static final Instant AWAITING_SINCE = DEADLINE.plus(Duration.ofMinutes(1));

        @Test
        @DisplayName("AC-311 未払いの支払を与信の反映を待つ状態にすると、待ち始めた時刻を持ち、状態は未払いのまま")
        void awaitsUnpaid() {
            // 準備：未払いの支払
            var payment = unpaid();

            // 操作：与信の反映を待つ
            var changed = payment.awaitAuthorization(AWAITING_SINCE);

            // 確認：未払いのまま待ち始めた時刻を持ち、イベントはない
            assertThat(changed).isTrue();
            assertThat(payment.status()).isEqualTo(PaymentStatus.UNPAID);
            assertThat(payment.awaitingAuthorizationSince()).contains(AWAITING_SINCE);
            assertThat(payment.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("AC-311 待ち始めた時刻は、重ねて待っても最初の時刻のまま")
        void keepsFirstAwaitingSince() {
            // 準備：与信の反映を待つ支払
            var payment = unpaid();
            payment.awaitAuthorization(AWAITING_SINCE);

            // 操作：1 分後に重ねて待つ
            var changed = payment.awaitAuthorization(AWAITING_SINCE.plus(Duration.ofMinutes(1)));

            // 確認：最初の時刻のまま
            assertThat(changed).isFalse();
            assertThat(payment.awaitingAuthorizationSince()).contains(AWAITING_SINCE);
        }

        @Test
        @DisplayName("AC-311 未払いでない支払（通知が先に届いた）は、待つ状態にしない")
        void doesNotAwaitAuthorized() {
            // 準備：与信済みの支払
            var payment = authorized();

            // 操作：与信の反映を待つ
            var changed = payment.awaitAuthorization(AWAITING_SINCE);

            // 確認：待っていない
            assertThat(changed).isFalse();
            assertThat(payment.awaitingAuthorizationSince()).isEmpty();
        }

        @Test
        @DisplayName("AC-311 与信の反映を待つ支払に与信の通知が届いたら、与信済みにする")
        void authorizesAwaiting() {
            // 準備：与信の反映を待つ支払
            var payment = unpaid();
            payment.awaitAuthorization(AWAITING_SINCE);

            // 操作：与信の成功を反映する
            payment.authorize(AUTHORIZATION_REF, AWAITING_SINCE.plus(Duration.ofMinutes(1)));

            // 確認：与信済みで、与信の成功のイベントが 1 件
            assertAuthorized(payment);
            assertThat(payment.pullEvents()).singleElement().isInstanceOf(PaymentAuthorized.class);
        }

        @Test
        @DisplayName("AC-311 待ち始めた時刻は、スナップショットから復元できる")
        void restoresAwaitingSince() {
            // 準備：与信の反映を待つ支払
            var payment = unpaid();
            payment.awaitAuthorization(AWAITING_SINCE);

            // 操作：スナップショットから復元する
            var restored = Payment.restore(payment.toSnapshot());

            // 確認：待ち始めた時刻を持つ
            assertThat(restored.awaitingAuthorizationSince()).contains(AWAITING_SINCE);
        }

        @Test
        @DisplayName("AC-311 作ったばかりの支払は、与信の反映を待っていない")
        void notAwaitingWhenCreated() {
            // 操作と確認：待ち始めた時刻がない
            assertThat(unpaid().awaitingAuthorizationSince()).isEmpty();
        }
    }

    @Nested
    @DisplayName("取消（注文のキャンセル）")
    class Cancel {

        @Test
        @DisplayName("AC-162 与信済み → 与信の取消待ち。取消の依頼が登録される")
        void requestsVoidOfAuthorized() {
            // 準備：与信済みの支払
            var payment = authorized();

            // 操作：取り消す
            payment.cancel(ORDERED_AT);

            // 確認：取消待ちで、この支払の取消の依頼が 1 件
            assertThat(payment.status()).isEqualTo(PaymentStatus.VOID_PENDING);
            assertThat(payment.pullEvents()).singleElement().isInstanceOfSatisfying(VoidRequested.class,
                    event -> assertThat(event.paymentId()).isEqualTo(payment.id()));
        }

        @Test
        @DisplayName("未払い → 支払なし。取り消す与信はないので、取消の依頼は出ない")
        void cancelsUnpaid() {
            // 準備：未払いの支払
            var payment = unpaid();

            // 操作：取り消す
            payment.cancel(ORDERED_AT);

            // 確認：支払なしで、イベントはない
            assertThat(payment.status()).isEqualTo(PaymentStatus.NOT_PAID);
            assertThat(payment.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("AC-166 与信の取消待ちに重ねて取り消しても、何も変わらない")
        void ignoresRepeatedCancelWhenVoidPending() {
            // 準備：与信の取消待ちの支払
            var payment = authorized();
            payment.cancel(ORDERED_AT);
            payment.pullEvents();

            // 操作：重ねて取り消す
            payment.cancel(ORDERED_AT);

            // 確認：取消待ちのまま、取消の依頼も出ない
            assertThat(payment.status()).isEqualTo(PaymentStatus.VOID_PENDING);
            assertThat(payment.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("AC-166 与信の取消済みに重ねて取り消しても、何も変わらない")
        void ignoresRepeatedCancelWhenVoided() {
            // 準備：与信の取消済みの支払
            var payment = voided();

            // 操作：重ねて取り消す
            payment.cancel(ORDERED_AT);

            // 確認：取消済みのまま、取消の依頼も出ない
            assertThat(payment.status()).isEqualTo(PaymentStatus.VOIDED);
            assertThat(payment.pullEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("取消済みにする")
    class MarkVoided {

        @Test
        @DisplayName("与信の取消待ち → 与信の取消済み")
        void marksVoided() {
            // 準備：与信の取消待ちの支払
            var payment = authorized();
            payment.cancel(ORDERED_AT);

            // 操作：取消済みにする
            payment.markVoided();

            // 確認：取消済み
            assertThat(payment.status()).isEqualTo(PaymentStatus.VOIDED);
        }

        @Test
        @DisplayName("与信の取消待ちでなければ、何も変わらない")
        void ignoresWhenNotPending() {
            // 準備：与信済みの支払
            var payment = authorized();

            // 操作：取消済みにする
            payment.markVoided();

            // 確認：与信済みのまま
            assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        }
    }

    @Nested
    @DisplayName("売上確定")
    class Capture {

        @Test
        @DisplayName("BR-21 与信済み → 売上確定済み")
        void capturesAuthorized() {
            // 準備：与信済みの支払
            var payment = authorized();

            // 操作：売上を確定する
            payment.capture(ORDERED_AT);

            // 確認：売上確定済みで、イベントはない
            assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
            assertThat(payment.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("BR-21 売上確定済みに重ねて確定しても、何も変わらない")
        void ignoresRepeatedCapture() {
            // 準備：売上確定済みの支払
            var payment = captured();

            // 操作：重ねて確定する
            payment.capture(ORDERED_AT);

            // 確認：売上確定済みのまま、イベントもない
            assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
            assertThat(payment.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("BR-21 与信の取消待ち（確定の最中に取り消された）なら、返金待ちにして返金の依頼を登録する")
        void requestsRefundWhenVoidPending() {
            // 準備：与信の取消待ちの支払
            var payment = authorized();
            payment.cancel(ORDERED_AT);
            payment.pullEvents();

            // 操作：売上確定を反映する
            payment.capture(ORDERED_AT);

            // 確認：返金待ちで、この支払の返金の依頼が 1 件
            assertThat(payment.status()).isEqualTo(PaymentStatus.REFUND_PENDING);
            assertThat(payment.pullEvents()).singleElement().isInstanceOfSatisfying(RefundRequested.class,
                    event -> assertThat(event.paymentId()).isEqualTo(payment.id()));
        }

        @Test
        @DisplayName("BR-21 未払いの支払は、売上を確定しない")
        void doesNotCaptureUnpaid() {
            // 準備：未払いの支払
            var payment = unpaid();

            // 操作：売上を確定する
            payment.capture(ORDERED_AT);

            // 確認：未払いのまま
            assertThat(payment.status()).isEqualTo(PaymentStatus.UNPAID);
        }

        @Test
        @DisplayName("BR-21 与信期限切れの支払は、売上を確定しない")
        void doesNotCaptureAuthorizationExpired() {
            // 準備：与信期限切れの支払
            var payment = authorizationExpired();

            // 操作：売上を確定する
            payment.capture(ORDERED_AT);

            // 確認：与信期限切れのまま
            assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZATION_EXPIRED);
        }

        @Test
        @DisplayName("BR-21 売上確定済みの支払に、与信の成功の通知が届いても（順序の入れ替わり）、何も変わらない")
        void ignoresAuthorizationAfterCapture() {
            // 準備：売上確定済みの支払
            var payment = captured();

            // 操作：別の与信の成功を反映する
            payment.authorize(OTHER_AUTHORIZATION_REF, ORDERED_AT);

            // 確認：売上確定済みで最初の与信のまま、イベントもない
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
            // 準備：与信済みの支払
            var payment = authorized();

            // 操作：与信期限切れにする
            payment.expireAuthorization();

            // 確認：与信期限切れで、イベントはない
            assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZATION_EXPIRED);
            assertThat(payment.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("BR-21 重ねて与信期限切れにしても、何も変わらない")
        void ignoresRepeated() {
            // 準備：与信期限切れの支払
            var payment = authorizationExpired();

            // 操作：重ねて与信期限切れにする
            payment.expireAuthorization();

            // 確認：与信期限切れのまま
            assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZATION_EXPIRED);
        }

        @Test
        @DisplayName("BR-21 売上確定済みの支払は、与信期限切れにしない")
        void doesNotExpireCaptured() {
            // 準備：売上確定済みの支払
            var payment = captured();

            // 操作：与信期限切れにする
            payment.expireAuthorization();

            // 確認：売上確定済みのまま
            assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
        }
    }

    @Nested
    @DisplayName("返金")
    class Refund {

        @Test
        @DisplayName("BR-21 売上確定済み → 返金待ち。返金の依頼が登録される")
        void requestsRefundOfCaptured() {
            // 準備：売上確定済みの支払
            var payment = captured();

            // 操作：取り消す
            payment.cancel(ORDERED_AT);

            // 確認：返金待ちで、この支払の返金の依頼が 1 件
            assertThat(payment.status()).isEqualTo(PaymentStatus.REFUND_PENDING);
            assertThat(payment.pullEvents()).singleElement().isInstanceOfSatisfying(RefundRequested.class,
                    event -> assertThat(event.paymentId()).isEqualTo(payment.id()));
        }

        @Test
        @DisplayName("BR-21 返金待ち → 返金済み")
        void marksRefunded() {
            // 準備：返金待ちの支払
            var payment = refundPending();

            // 操作：返金済みにする
            payment.markRefunded();

            // 確認：返金済み
            assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
        }

        @Test
        @DisplayName("BR-21 返金待ちでなければ、返金済みにしない")
        void ignoresMarkRefundedWhenNotPending() {
            // 準備：売上確定済みの支払
            var payment = captured();

            // 操作：返金済みにする
            payment.markRefunded();

            // 確認：売上確定済みのまま
            assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
        }

        @Test
        @DisplayName("BR-21 返金待ちに重ねて取り消しても、何も変わらず、返金の依頼も出ない")
        void ignoresRepeatedCancelWhenRefundPending() {
            // 準備：返金待ちの支払
            var payment = refundPending();

            // 操作：重ねて取り消す
            payment.cancel(ORDERED_AT);

            // 確認：返金待ちのまま、返金の依頼も出ない
            assertThat(payment.status()).isEqualTo(PaymentStatus.REFUND_PENDING);
            assertThat(payment.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("BR-21 返金済みに重ねて取り消しても、何も変わらず、返金の依頼も出ない")
        void ignoresRepeatedCancelWhenRefunded() {
            // 準備：返金済みの支払
            var payment = refundPending();
            payment.markRefunded();

            // 操作：重ねて取り消し、重ねて返金済みにする
            payment.cancel(ORDERED_AT);
            payment.markRefunded();

            // 確認：返金済みのまま、返金の依頼も出ない
            assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(payment.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("BR-21 与信期限切れの支払を取り消しても、何もしない（取消も返金の依頼も出ない）")
        void doesNothingForAuthorizationExpired() {
            // 準備：与信期限切れの支払
            var payment = authorizationExpired();

            // 操作：取り消す
            payment.cancel(ORDERED_AT);

            // 確認：与信期限切れのまま、イベントもない
            assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZATION_EXPIRED);
            assertThat(payment.pullEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("与信期限")
    class AuthorizationDeadline {

        /**
         * 与信の時刻
         *
         * <p>日本時間の 2026-10-08 09:00。
         */
        private static final Instant AUTHORIZED_AT = Instant.parse("2026-10-08T00:00:00Z");

        /**
         * 与信期限（与信の時刻 ＋ 7 日）
         */
        private static final Instant AUTHORIZATION_DEADLINE = AUTHORIZED_AT.plus(Duration.ofDays(7));

        /**
         * 期限が近いとし始める時刻（与信期限の 2 日前）
         */
        private static final Instant WARNING_START = AUTHORIZATION_DEADLINE.minus(Duration.ofDays(2));

        /**
         * 与信の有効期間 7 日、期限が近いとする長さ 2 日
         */
        private final AuthorizationTerm term = new AuthorizationTerm(Duration.ofDays(7), Duration.ofDays(2));

        @Test
        @DisplayName("BR-20 与信の成功を受けたときの時刻を、与信の時刻として覚える")
        void recordsAuthorizedAt() {
            // 準備：未払いの支払
            var payment = unpaid();

            // 操作：与信の成功を反映する
            payment.authorize(AUTHORIZATION_REF, AUTHORIZED_AT);

            // 確認：与信の時刻
            assertThat(payment.authorizedAt()).contains(AUTHORIZED_AT);
        }

        @Test
        @DisplayName("BR-20 与信期限は、与信の時刻の 7 日後")
        void deadlineIsSevenDaysAfterAuthorization() {
            // 準備：与信済みの支払
            var payment = authorizedAt(AUTHORIZED_AT);

            // 操作と確認：与信の時刻の 7 日後
            assertThat(payment.authorizationDeadline(term)).contains(AUTHORIZATION_DEADLINE);
        }

        @Test
        @DisplayName("BR-20 与信がなければ、与信の時刻も与信期限もない")
        void noDeadlineWithoutAuthorization() {
            // 準備：未払いの支払
            var payment = unpaid();

            // 操作と確認：与信の時刻と与信期限がなく、期限が近いとしない
            assertThat(payment.authorizedAt()).isEmpty();
            assertThat(payment.authorizationDeadline(term)).isEmpty();
            assertThat(payment.isAuthorizationExpiring(term, AUTHORIZATION_DEADLINE)).isFalse();
        }

        @Test
        @DisplayName("BR-20 重ねて届いた与信の成功の通知で、与信の時刻は変わらない")
        void keepsAuthorizedAtOnDuplicate() {
            // 準備：与信済みの支払
            var payment = authorizedAt(AUTHORIZED_AT);

            // 操作：1 分後に重ねて反映する
            payment.authorize(AUTHORIZATION_REF, AUTHORIZED_AT.plusSeconds(60));

            // 確認：最初の時刻のまま
            assertThat(payment.authorizedAt()).contains(AUTHORIZED_AT);
        }

        @Test
        @DisplayName("BR-20 与信期限の 2 日前より前は、期限が近くない")
        void notExpiringBeforeWarningStart() {
            // 操作と確認：2 日前の 1 秒前は近くない
            assertThat(authorizedAt(AUTHORIZED_AT).isAuthorizationExpiring(term, WARNING_START.minusSeconds(1))).isFalse();
        }

        @Test
        @DisplayName("BR-20 与信期限の 2 日前の時刻から、期限が近い")
        void expiringFromWarningStart() {
            // 操作と確認：2 日前の時刻ちょうどは近い
            assertThat(authorizedAt(AUTHORIZED_AT).isAuthorizationExpiring(term, WARNING_START)).isTrue();
        }

        @Test
        @DisplayName("BR-20 与信期限を過ぎたものも、期限が近いに含む")
        void expiringIncludesPastDeadline() {
            // 準備：与信済みの支払
            var payment = authorizedAt(AUTHORIZED_AT);

            // 操作と確認：与信期限の時刻と、その 3 日後も近い
            assertThat(payment.isAuthorizationExpiring(term, AUTHORIZATION_DEADLINE)).isTrue();
            assertThat(payment.isAuthorizationExpiring(term, AUTHORIZATION_DEADLINE.plus(Duration.ofDays(3)))).isTrue();
        }

        @Test
        @DisplayName("BR-20 与信期限切れの支払も、期限が近いに含む")
        void expiringIncludesAuthorizationExpired() {
            // 準備：与信期限切れの支払
            var payment = authorizedAt(AUTHORIZED_AT);
            payment.expireAuthorization();

            // 操作と確認：与信期限の後も近い
            assertThat(payment.isAuthorizationExpiring(term, AUTHORIZATION_DEADLINE.plusSeconds(1))).isTrue();
        }

        @Test
        @DisplayName("BR-20 売上確定済みの支払は、期限が近くない")
        void capturedIsNotExpiring() {
            // 準備：売上確定済みの支払
            var payment = authorizedAt(AUTHORIZED_AT);
            payment.capture(ORDERED_AT);

            // 操作と確認：与信期限の後も近くない
            assertThat(payment.isAuthorizationExpiring(term, AUTHORIZATION_DEADLINE.plusSeconds(1))).isFalse();
        }

        @Test
        @DisplayName("BR-20 期限が近い与信の、与信の時刻の上限は、現在時刻から（有効期間 − 近いとする長さ）を引いた時刻")
        void latestExpiringAuthorizedAt() {
            // 準備：現在時刻
            var now = Instant.parse("2026-10-20T00:00:00Z");

            // 操作と確認：現在時刻の 5 日前
            assertThat(term.latestExpiringAuthorizedAt(now)).isEqualTo(now.minus(Duration.ofDays(5)));
        }
    }

    @Nested
    @DisplayName("遷移できない操作（状態 × 操作の表の残り）")
    class NoTransition {

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"UNPAID", "NOT_PAID"})
        @DisplayName("BR-17 未払い・支払なし以外に与信の成功が届いても（重複・順序の入れ替わり）、何も変わらず、イベントも出ない")
        void authorizeChangesNothing(PaymentStatus status) {
            // 準備：その状態の支払
            var payment = inState(status);
            var before = payment.toSnapshot();

            // 操作：別の与信を、別の時刻で反映する
            var authorized = payment.authorize(OTHER_AUTHORIZATION_REF, DEADLINE, DEADLINE);

            // 確認：反映せず、状態・与信の参照・与信の時刻が元のまま、イベントもない
            assertThat(authorized).isFalse();
            assertUnchanged(payment, before);
        }

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "UNPAID")
        @DisplayName("BR-17 未払い以外は、期限切れにせず、イベントも出ない")
        void expireChangesNothing(PaymentStatus status) {
            // 準備：その状態の支払
            var payment = inState(status);
            var before = payment.toSnapshot();

            // 操作：期限切れにする
            var changed = payment.expire(DEADLINE);

            // 確認：変わらず、イベントもない
            assertThat(changed).isFalse();
            assertUnchanged(payment, before);
        }

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "UNPAID")
        @DisplayName("AC-311 未払い以外は、与信の反映を待つ支払にしない")
        void awaitAuthorizationChangesNothing(PaymentStatus status) {
            // 準備：その状態の支払
            var payment = inState(status);
            var before = payment.toSnapshot();

            // 操作：与信の反映を待つ
            var changed = payment.awaitAuthorization(DEADLINE);

            // 確認：待ち始めた時刻を持たず、変わらない
            assertThat(changed).isFalse();
            assertUnchanged(payment, before);
        }

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"AUTHORIZED", "VOID_PENDING"})
        @DisplayName("BR-21 与信済み・与信の取消待ち以外は、売上確定を反映しても何も変わらず、イベントも出ない")
        void captureChangesNothing(PaymentStatus status) {
            // 準備：その状態の支払
            var payment = inState(status);
            var before = payment.toSnapshot();

            // 操作：売上確定を反映する
            payment.capture(DEADLINE);

            // 確認：変わらず、返金の依頼も出ない
            assertUnchanged(payment, before);
        }

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "AUTHORIZED")
        @DisplayName("BR-21 与信済み以外は、与信期限切れにしない（確定の最中に取り消された与信の取消待ちを含む）")
        void expireAuthorizationChangesNothing(PaymentStatus status) {
            // 準備：その状態の支払
            var payment = inState(status);
            var before = payment.toSnapshot();

            // 操作：与信期限切れにする
            payment.expireAuthorization();

            // 確認：変わらない
            assertUnchanged(payment, before);
        }

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"UNPAID", "AUTHORIZED", "CAPTURED"})
        @DisplayName("BR-21 未払い・与信済み・売上確定済み以外は、取り消しても何も変わらず、取消も返金の依頼も出ない")
        void cancelChangesNothing(PaymentStatus status) {
            // 準備：その状態の支払
            var payment = inState(status);
            var before = payment.toSnapshot();

            // 操作：取り消す
            payment.cancel(DEADLINE);

            // 確認：変わらず、依頼も出ない
            assertUnchanged(payment, before);
        }

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "VOID_PENDING")
        @DisplayName("BR-21 与信の取消待ち以外は、取消済みにしない")
        void markVoidedChangesNothing(PaymentStatus status) {
            // 準備：その状態の支払
            var payment = inState(status);
            var before = payment.toSnapshot();

            // 操作：取消済みにする
            payment.markVoided();

            // 確認：変わらない
            assertUnchanged(payment, before);
        }

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "REFUND_PENDING")
        @DisplayName("BR-21 返金待ち以外は、返金済みにしない")
        void markRefundedChangesNothing(PaymentStatus status) {
            // 準備：その状態の支払
            var payment = inState(status);
            var before = payment.toSnapshot();

            // 操作：返金済みにする
            payment.markRefunded();

            // 確認：変わらない
            assertUnchanged(payment, before);
        }

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "UNPAID")
        @DisplayName("未払い以外は、支払画面を開けず、画面も変わらない")
        void openPageRejected(PaymentStatus status) {
            // 準備：その状態の支払と、開こうとする画面
            var payment = inState(status);
            var before = payment.toSnapshot();
            var page = new PaymentPage("cs_test_1", "https://pay.example/cs_test_1");

            // 操作と確認：例外になる
            assertThatThrownBy(() -> payment.openPage(page)).isInstanceOf(IllegalStateException.class);

            // 確認：画面を覚えず、変わらない
            assertThat(payment.page()).isEmpty();
            assertUnchanged(payment, before);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 操作の前から何も変わっていないことを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>スナップショット（状態、支払画面、与信の参照、与信の時刻、待ち始めた時刻）が操作の前と同じ</li>
     *   <li>イベントがない</li>
     * </ul>
     *
     * @param payment 確認する支払
     * @param before  操作の前のスナップショット（状態ごとに作るので、定数にできない）
     */
    private static void assertUnchanged(Payment payment, Payment.Snapshot before) {
        assertThat(payment.toSnapshot()).isEqualTo(before);
        assertThat(payment.pullEvents()).isEmpty();
    }

    /**
     * 作ったばかりの、未払いの支払であることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態が「未払い」</li>
     *   <li>注文 ID が渡した値</li>
     *   <li>金額が {@link #AMOUNT}</li>
     *   <li>支払の期限が {@link #DEADLINE}</li>
     *   <li>支払画面がない</li>
     *   <li>イベントがない</li>
     * </ul>
     *
     * @param payment 確認する支払
     * @param orderId 渡した注文 ID（実行ごとに作るので、定数にできない）
     */
    private static void assertCreatedUnpaid(Payment payment, UUID orderId) {
        assertThat(payment.status()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(payment.orderId()).isEqualTo(orderId);
        assertThat(payment.amount()).isEqualTo(AMOUNT);
        assertThat(payment.deadline()).isEqualTo(DEADLINE);
        assertThat(payment.page()).isEmpty();
        assertThat(payment.pullEvents()).isEmpty();
    }

    /**
     * 最初の与信（{@link #AUTHORIZATION_REF}）で与信済みであることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態が「与信済み」</li>
     *   <li>与信の参照が {@link #AUTHORIZATION_REF}</li>
     * </ul>
     *
     * @param payment 確認する支払
     */
    private static void assertAuthorized(Payment payment) {
        assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(payment.authorizationRef()).isEqualTo(AUTHORIZATION_REF);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 未払いの支払を作る。
     *
     * @return 支払
     */
    private static Payment unpaid() {
        return Payment.create(UUID.randomUUID(), UUID.randomUUID(), AMOUNT, DEADLINE);
    }

    /**
     * 注文日時に与信済みにした支払を作る（イベントは取り出し済み）。
     *
     * @return 支払
     */
    private static Payment authorized() {
        return authorizedAt(ORDERED_AT);
    }

    /**
     * 期限切れで支払なしの支払を作る（イベントは取り出し済み）。
     *
     * @return 支払
     */
    private static Payment expired() {
        var payment = unpaid();
        payment.expire(DEADLINE);
        payment.pullEvents();
        return payment;
    }

    /**
     * 指定の時刻に与信済みにした支払を作る（イベントは取り出し済み）。
     *
     * @param authorizedAt 与信の時刻
     * @return 支払
     */
    private static Payment authorizedAt(Instant authorizedAt) {
        var payment = unpaid();
        payment.authorize(AUTHORIZATION_REF, authorizedAt);
        payment.pullEvents();
        return payment;
    }

    /**
     * 売上確定済みの支払を作る（イベントは取り出し済み）。
     *
     * @return 支払
     */
    private static Payment captured() {
        var payment = authorized();
        payment.capture(ORDERED_AT);
        payment.pullEvents();
        return payment;
    }

    /**
     * 与信期限切れの支払を作る（イベントは取り出し済み）。
     *
     * @return 支払
     */
    private static Payment authorizationExpired() {
        var payment = authorized();
        payment.expireAuthorization();
        payment.pullEvents();
        return payment;
    }

    /**
     * 返金待ちの支払を作る（イベントは取り出し済み）。
     *
     * @return 支払
     */
    private static Payment refundPending() {
        var payment = captured();
        payment.cancel(ORDERED_AT);
        payment.pullEvents();
        return payment;
    }

    /**
     * 与信の取消済みの支払を作る（イベントは取り出し済み）。
     *
     * @return 支払
     */
    private static Payment voided() {
        var payment = voidPending();
        payment.markVoided();
        return payment;
    }

    /**
     * 与信の取消待ちの支払を作る（イベントは取り出し済み）。
     *
     * @return 支払
     */
    private static Payment voidPending() {
        var payment = authorized();
        payment.cancel(ORDERED_AT);
        payment.pullEvents();
        return payment;
    }

    /**
     * 返金済みの支払を作る（イベントは取り出し済み）。
     *
     * @return 支払
     */
    private static Payment refunded() {
        var payment = refundPending();
        payment.markRefunded();
        return payment;
    }

    /**
     * 指定の状態の支払を、図の遷移をたどって作る（イベントは取り出し済み）。
     *
     * @param status 状態
     * @return 支払
     */
    private static Payment inState(PaymentStatus status) {
        return switch (status) {
            case UNPAID -> unpaid();
            case AUTHORIZED -> authorized();
            case VOID_PENDING -> voidPending();
            case VOIDED -> voided();
            case NOT_PAID -> expired();
            case CAPTURED -> captured();
            case AUTHORIZATION_EXPIRED -> authorizationExpired();
            case REFUND_PENDING -> refundPending();
            case REFUNDED -> refunded();
        };
    }
}
