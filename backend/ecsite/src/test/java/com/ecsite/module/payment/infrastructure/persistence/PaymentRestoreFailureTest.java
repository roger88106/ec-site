package com.ecsite.module.payment.infrastructure.persistence;

import com.ecsite.jooq.payment.tables.records.PaymentRecord;
import com.ecsite.module.payment.domain.payment.PaymentStatus;
import com.ecsite.support.RestoreFailures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.jooq.payment.Tables.PAYMENT_;
import static com.ecsite.support.RestoreFailures.RECORDS;
import static com.ecsite.support.RestoreFailures.assertValueIsHidden;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 支払を DB の値から戻すときの失敗の単体テスト
 *
 * <p>列挙にない値と、状態と項目が整合しない行で復元の失敗になり、例外の出力に DB の値が出ないことを確認する。
 * 壊した行が狙った条件で失敗したことは、原因の例外の種類と、壊していない行が戻せることで確認する。
 */
class PaymentRestoreFailureTest {

    // ============================== 定数 ==============================

    /**
     * 与信の時刻
     *
     * <p>例外の出力に入ると分かるよう、マイクロ秒まで持つ。
     */
    private static final Instant AUTHORIZED_AT = Instant.parse("2026-11-01T00:00:00.123456Z");

    // ============================== テスト ==============================

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(PaymentStatus.class)
    @DisplayName("壊していない支払の行は、戻せる")
    void restoresValidRow(PaymentStatus status) {
        // 準備：状態に合う与信の参照と与信の時刻を持つ行（与信のない状態は、どちらも持たない）
        var payment = paymentRow(status);
        if (status != PaymentStatus.UNPAID && status != PaymentStatus.NOT_PAID) {
            payment.setAuthorizationRef("pi_test_1");
            payment.setAuthorizedAt(AUTHORIZED_AT);
        }

        // 操作と確認：支払を読むと、その状態で戻る（他のテストの失敗が、準備の行のせいでないことの前提）
        assertThat(repositoryReturning(payment).findById(UUID.randomUUID()))
                .hasValueSatisfying(restored -> assertThat(restored.status()).isEqualTo(status));
    }

    @Test
    @DisplayName("支払の状態が列挙にないとき、例外の出力に値が出ない")
    void hidesStatus() {
        // 準備：状態が列挙にない支払の行
        var value = RestoreFailures.value();
        var payment = paymentRow(PaymentStatus.UNPAID);
        payment.setStatus(value);

        // 操作と確認：支払を読むと失敗し、出力に値がない
        assertValueIsHidden(() -> repositoryReturning(payment).findById(UUID.randomUUID()), value, IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(value = PaymentStatus.class, names = {"UNPAID", "NOT_PAID"})
    @DisplayName("与信のない状態の支払に与信の参照があるとき、復元の失敗になり、例外の出力に値が出ない")
    void hidesUnauthorizedWithRef(PaymentStatus status) {
        // 準備：与信の参照を持つ、与信のない状態の支払の行。値は与信の参照に入れる
        var value = RestoreFailures.value();
        var payment = paymentRow(status);
        payment.setAuthorizationRef(value);

        // 操作と確認：支払を読むと失敗し、出力に値がない
        assertValueIsHidden(() -> repositoryReturning(payment).findById(UUID.randomUUID()), value, IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(value = PaymentStatus.class, names = {"UNPAID", "NOT_PAID"})
    @DisplayName("与信のない状態の支払に与信の時刻があるとき、復元の失敗になり、例外の出力に時刻が出ない")
    void hidesUnauthorizedWithAuthorizedAt(PaymentStatus status) {
        // 準備：与信の時刻を持つ、与信のない状態の支払の行
        var payment = paymentRow(status);
        payment.setAuthorizedAt(AUTHORIZED_AT);

        // 操作と確認：支払を読むと失敗し、出力に時刻がない
        assertValueIsHidden(() -> repositoryReturning(payment).findById(UUID.randomUUID()), AUTHORIZED_AT.toString(),
                IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(value = PaymentStatus.class, names = {"UNPAID", "NOT_PAID"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("与信のある状態の支払に与信の参照がないとき、復元の失敗になり、例外の出力に値が出ない")
    void hidesAuthorizedWithoutRef(PaymentStatus status) {
        // 準備：与信の参照がない支払の行。与信の時刻を持たせ、時刻の有無でなく参照の有無で拒否させる。値は支払画面に入れる
        var value = RestoreFailures.value();
        var payment = paymentRow(status);
        payment.setPageId(value);
        payment.setPageUrl(value);
        payment.setAuthorizedAt(AUTHORIZED_AT);

        // 操作と確認：支払を読むと失敗し、出力に値がない
        assertValueIsHidden(() -> repositoryReturning(payment).findById(UUID.randomUUID()), value, IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(value = PaymentStatus.class, names = {"AUTHORIZED", "CAPTURED", "AUTHORIZATION_EXPIRED"})
    @DisplayName("与信済みから進んだ支払に与信の時刻がないとき、復元の失敗になり、例外の出力に値が出ない")
    void hidesAuthorizedWithoutAuthorizedAt(PaymentStatus status) {
        // 準備：与信の参照があり、与信の時刻がない支払の行。値は与信の参照に入れる
        var value = RestoreFailures.value();
        var payment = paymentRow(status);
        payment.setAuthorizationRef(value);

        // 操作と確認：支払を読むと失敗し、出力に値がない
        assertValueIsHidden(() -> repositoryReturning(payment).findById(UUID.randomUUID()), value, IllegalArgumentException.class);
    }

    @Test
    @DisplayName("支払画面の ID があり URL がないとき、復元の失敗になり、例外の出力に値が出ない")
    void hidesPageWithoutUrl() {
        // 準備：URL のない支払画面を持つ未払いの支払の行。値は支払画面の ID に入れる
        var value = RestoreFailures.value();
        var payment = paymentRow(PaymentStatus.UNPAID);
        payment.setPageId(value);

        // 操作と確認：支払を読むと失敗し、出力に値がない
        assertValueIsHidden(() -> repositoryReturning(payment).findById(UUID.randomUUID()), value, NullPointerException.class);
    }

    @Test
    @DisplayName("支払の概要で状態が列挙にないとき、例外の出力に値が出ない")
    void hidesStatusInSummary() {
        // 準備：状態が列挙にない概要の行
        var value = RestoreFailures.value();
        var row = RECORDS.newRecord(PAYMENT_.ORDER_ID, PAYMENT_.STATUS, PAYMENT_.DEADLINE).values(UUID.randomUUID(), value, Instant.EPOCH);
        var dsl = new RestoreFailures().with("payment", row).dsl();

        // 操作と確認：概要を読むと失敗し、出力に値がない
        assertValueIsHidden(() -> new JooqPaymentQueryRepository(dsl).findSummaries(List.of(UUID.randomUUID())), value,
                IllegalArgumentException.class);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 必須の列を埋めた支払の行を作る。
     *
     * <p>与信の参照、与信の時刻、支払画面、待ち始めた時刻は持たない。
     *
     * @param status 状態
     * @return 支払の行
     */
    private static PaymentRecord paymentRow(PaymentStatus status) {
        var payment = RECORDS.newRecord(PAYMENT_);
        payment.setId(UUID.randomUUID());
        payment.setOrderId(UUID.randomUUID());
        payment.setAmount(0L);
        payment.setDeadline(Instant.EPOCH);
        payment.setStatus(status.name());
        payment.setVersion(0L);
        return payment;
    }

    /**
     * 支払の行を返す Repository を作る。
     *
     * @param payment 返す支払の行
     * @return Repository
     */
    private static JooqPaymentRepository repositoryReturning(PaymentRecord payment) {
        var dsl = new RestoreFailures().with("payment", payment).dsl();
        return new JooqPaymentRepository(dsl, mock(ApplicationEventPublisher.class), Clock.systemUTC());
    }
}
