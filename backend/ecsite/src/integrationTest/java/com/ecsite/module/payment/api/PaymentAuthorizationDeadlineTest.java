package com.ecsite.module.payment.api;

import com.ecsite.common.model.id.OrderId;
import com.ecsite.module.payment.api.model.AuthorizationDeadline;
import com.ecsite.module.payment.domain.PaymentErrorCode;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.PaymentTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 与信期限の統合テスト
 *
 * <p>与信の有効期間は 7 日、期限が近いとする長さは 2 日（既定）。支払は保存されて他のテストと共有されるので、
 * 注文 ID の一覧は、自分の注文を含むか・含まないかで確認する。
 */
@IntegrationTest
class PaymentAuthorizationDeadlineTest {

    // ============================== 定数 ==============================

    /**
     * 与信の時刻
     *
     * <p>注文と同時に支払う。日本時間の 2026-10-08 00:30。
     */
    private static final Instant AUTHORIZED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 与信期限（与信の時刻 ＋ 7 日）
     */
    private static final Instant DEADLINE = Instant.parse("2026-10-14T15:30:00Z");

    /**
     * 期限が近くなる時刻（与信期限の 2 日前）
     */
    private static final Instant EXPIRING_FROM = Instant.parse("2026-10-12T15:30:00Z");

    /**
     * 与信期限を過ぎた時刻（与信期限の 3 日後）
     */
    private static final Instant PAST_DEADLINE = DEADLINE.plus(Duration.ofDays(3));

    // ============================== Bean ==============================

    /**
     * 確認する決済の API
     */
    @Autowired
    PaymentApi paymentApi;

    /**
     * テスト用の決済サービス
     */
    @Autowired
    TestPaymentGateway gateway;

    /**
     * テストの時計
     */
    @Autowired
    MutableClock clock;

    /**
     * 支払を進める補助
     */
    PaymentTestData payments;

    /**
     * 支払の操作を、呼んだ側のトランザクションに加えるためのテンプレート
     */
    @Autowired
    TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        payments = new PaymentTestData(paymentApi, gateway, transactionTemplate);
        clock.setInstant(AUTHORIZED_AT);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("与信期限")
    class Deadline {

        @Test
        @DisplayName("BR-20 与信を受けた注文の与信期限は、与信の時刻の 7 日後")
        void isSevenDaysAfterAuthorization() {
            // 準備：与信済みの支払
            var authorized = payments.authorized(AUTHORIZED_AT);

            // 操作：与信期限を読む
            var deadlines = paymentApi.findAuthorizationDeadlines(List.of(new OrderId(authorized)));

            // 確認：期限は 7 日後で、まだ近くない
            assertThat(deadlines).containsExactly(new AuthorizationDeadline(new OrderId(authorized), DEADLINE, false));
        }

        @Test
        @DisplayName("BR-20 与信を受けていない注文と支払のない注文は、与信期限の一覧に含まない")
        void excludesOrdersWithoutAuthorization() {
            // 準備：与信済みと未払いの支払
            var authorized = payments.authorized(AUTHORIZED_AT);
            var unpaid = payments.created(AUTHORIZED_AT);

            // 操作：支払のない注文を加えて読む
            var deadlines = paymentApi.findAuthorizationDeadlines(List.of(new OrderId(authorized), new OrderId(unpaid), new OrderId(UUID.randomUUID())));

            // 確認：与信済みの注文だけ
            assertThat(deadlines).extracting(AuthorizationDeadline::orderId).containsExactly(new OrderId(authorized));
        }
    }

    @Nested
    @DisplayName("期限が近い")
    class Expiring {

        @Test
        @DisplayName("AC-186 与信期限の 2 日前（5 日後）の直前は、期限が近くない")
        void notExpiringJustBeforeTwoDays() {
            // 準備：期限が近くなる 1 秒前
            var orderId = payments.authorized(AUTHORIZED_AT);
            clock.setInstant(EXPIRING_FROM.minusSeconds(1));

            // 操作と確認：期限が近くない
            assertThat(expiringOf(orderId)).isFalse();
        }

        @Test
        @DisplayName("AC-186 与信期限の 2 日前（5 日後）から、期限が近い")
        void expiringFromTwoDaysBefore() {
            // 準備：期限が近くなる時刻
            var orderId = payments.authorized(AUTHORIZED_AT);
            clock.setInstant(EXPIRING_FROM);

            // 操作と確認：期限が近い
            assertThat(expiringOf(orderId)).isTrue();
        }

        @Test
        @DisplayName("AC-186 与信期限を過ぎた注文も、期限が近い")
        void expiringAfterDeadline() {
            // 準備：期限の 3 日後
            var orderId = payments.authorized(AUTHORIZED_AT);
            clock.setInstant(PAST_DEADLINE);

            // 操作と確認：期限が近い
            assertThat(expiringOf(orderId)).isTrue();
        }

        @Test
        @DisplayName("AC-188 売上確定の与信期限切れで与信期限切れになった注文も、期限が近い")
        void expiringWhenAuthorizationExpired() {
            // 準備：与信期限切れの支払
            var orderId = authorizationExpired();

            // 操作と確認：期限が近い
            assertThat(expiringOf(orderId)).isTrue();
        }

        @Test
        @DisplayName("BR-20 売上確定済みの注文は、期限を過ぎても、期限が近くない")
        void notExpiringWhenCaptured() {
            // 準備：売上を確定して、期限の 3 日後
            var orderId = payments.authorized(AUTHORIZED_AT);
            paymentApi.capture(new OrderId(orderId));
            clock.setInstant(PAST_DEADLINE);

            // 操作と確認：期限が近くない
            assertThat(expiringOf(orderId)).isFalse();
        }
    }

    @Nested
    @DisplayName("期限が近い注文の一覧")
    class ExpiringOrderIds {

        @Test
        @DisplayName("AC-186、187 与信期限の 2 日前（5 日後）の直前は、期限が近い注文の一覧に出ない")
        void notListedJustBeforeTwoDays() {
            // 準備：期限が近くなる 1 秒前
            var orderId = payments.authorized(AUTHORIZED_AT);
            clock.setInstant(EXPIRING_FROM.minusSeconds(1));

            // 操作と確認：期限が近い注文の一覧に出ない
            assertThat(paymentApi.findExpiringOrderIds()).doesNotContain(new OrderId(orderId));
        }

        @Test
        @DisplayName("AC-186、187 与信期限の 2 日前（5 日後）から、期限が近い注文の一覧に出る")
        void listedFromTwoDaysBefore() {
            // 準備：期限が近くなる時刻
            var orderId = payments.authorized(AUTHORIZED_AT);
            clock.setInstant(EXPIRING_FROM);

            // 操作と確認：期限が近い注文の一覧に出る
            assertThat(paymentApi.findExpiringOrderIds()).contains(new OrderId(orderId));
        }

        @Test
        @DisplayName("AC-187 与信期限を過ぎた注文も、期限が近い注文の一覧に出る")
        void listedAfterDeadline() {
            // 準備：期限の 3 日後
            var orderId = payments.authorized(AUTHORIZED_AT);
            clock.setInstant(PAST_DEADLINE);

            // 操作と確認：期限が近い注文の一覧に出る
            assertThat(paymentApi.findExpiringOrderIds()).contains(new OrderId(orderId));
        }

        @Test
        @DisplayName("AC-188 売上確定の与信期限切れで与信期限切れになった注文も、期限が近い注文の一覧に出る")
        void listedWhenAuthorizationExpired() {
            // 準備：与信期限切れの支払
            var orderId = authorizationExpired();

            // 操作と確認：期限が近い注文の一覧に出る
            assertThat(paymentApi.findExpiringOrderIds()).contains(new OrderId(orderId));
        }

        @Test
        @DisplayName("BR-20 売上確定済みの注文は、期限を過ぎても、期限が近い注文の一覧に出ない")
        void notListedWhenCaptured() {
            // 準備：売上を確定して、期限の 3 日後
            var orderId = payments.authorized(AUTHORIZED_AT);
            paymentApi.capture(new OrderId(orderId));
            clock.setInstant(PAST_DEADLINE);

            // 操作と確認：期限が近い注文の一覧に出ない
            assertThat(paymentApi.findExpiringOrderIds()).doesNotContain(new OrderId(orderId));
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 注文の与信期限が近いかを読む。
     *
     * @param orderId 注文 ID
     * @return 期限が近ければ true
     */
    private boolean expiringOf(UUID orderId) {
        return paymentApi.findAuthorizationDeadlines(List.of(new OrderId(orderId))).getFirst().expiring();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 決済サービスで与信が切れ、売上確定で与信期限切れになった支払を作る。<br />
     * 時計は与信期限の 1 秒後になる。
     *
     * @return 注文 ID
     */
    private UUID authorizationExpired() {
        var orderId = payments.authorized(AUTHORIZED_AT);
        gateway.expireAuthorization(gateway.authorizationRefOf(payments.pageIdOf(orderId)));
        clock.setInstant(DEADLINE.plusSeconds(1));
        assertBusinessError(() -> paymentApi.capture(new OrderId(orderId)), PaymentErrorCode.AUTHORIZATION_EXPIRED);
        return orderId;
    }
}
