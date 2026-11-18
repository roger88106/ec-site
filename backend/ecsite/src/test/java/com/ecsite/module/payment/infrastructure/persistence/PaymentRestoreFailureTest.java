package com.ecsite.module.payment.infrastructure.persistence;

import com.ecsite.jooq.payment.tables.records.PaymentRecord;
import com.ecsite.module.payment.domain.payment.PaymentStatus;
import com.ecsite.support.RestoreFailures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.jooq.payment.Tables.PAYMENT_;
import static com.ecsite.support.RestoreFailures.RECORDS;
import static com.ecsite.support.RestoreFailures.assertValueIsHidden;
import static org.mockito.Mockito.mock;

/**
 * 支払を DB の値から戻すときの失敗の単体テスト
 *
 * <p>列挙にない値と、状態と項目が整合しない行で復元の失敗になり、例外の出力に DB の値が出ないことを確認する。
 */
class PaymentRestoreFailureTest {

    // ============================== テスト ==============================

    @Test
    @DisplayName("支払の状態が列挙にないとき、例外の出力に値が出ない")
    void hidesStatus() {
        // 準備：状態が列挙にない支払の行
        var value = RestoreFailures.value();
        var payment = RECORDS.newRecord(PAYMENT_);
        payment.setId(UUID.randomUUID());
        payment.setAmount(0L);
        payment.setStatus(value);
        var dsl = new RestoreFailures().with("payment", payment).dsl();
        var repository = new JooqPaymentRepository(dsl, mock(ApplicationEventPublisher.class), Clock.systemUTC());

        // 操作と確認：支払を読むと失敗し、出力に値がない
        assertValueIsHidden(() -> repository.findById(UUID.randomUUID()), value);
    }

    @Test
    @DisplayName("与信済みの支払に与信の参照がないとき、復元の失敗になり、例外の出力に値が出ない")
    void hidesAuthorizedWithoutRef() {
        // 準備：与信の参照がない与信済みの支払の行。値は支払画面に入れる
        var value = RestoreFailures.value();
        var payment = paymentRow(PaymentStatus.AUTHORIZED);
        payment.setPageId(value);
        payment.setPageUrl(value);
        payment.setAuthorizedAt(Instant.EPOCH);
        var repository = repositoryReturning(payment);

        // 操作と確認：支払を読むと失敗し、出力に値がない
        assertValueIsHidden(() -> repository.findById(UUID.randomUUID()), value);
    }

    @Test
    @DisplayName("未払いの支払に与信の参照があるとき、復元の失敗になり、例外の出力に値が出ない")
    void hidesUnpaidWithRef() {
        // 準備：与信の参照を持つ未払いの支払の行。値は与信の参照に入れる
        var value = RestoreFailures.value();
        var payment = paymentRow(PaymentStatus.UNPAID);
        payment.setAuthorizationRef(value);
        var repository = repositoryReturning(payment);

        // 操作と確認：支払を読むと失敗し、出力に値がない
        assertValueIsHidden(() -> repository.findById(UUID.randomUUID()), value);
    }

    @Test
    @DisplayName("支払画面の ID があり URL がないとき、復元の失敗になり、例外の出力に値が出ない")
    void hidesPageWithoutUrl() {
        // 準備：URL のない支払画面を持つ未払いの支払の行。値は支払画面の ID に入れる
        var value = RestoreFailures.value();
        var payment = paymentRow(PaymentStatus.UNPAID);
        payment.setPageId(value);
        var repository = repositoryReturning(payment);

        // 操作と確認：支払を読むと失敗し、出力に値がない
        assertValueIsHidden(() -> repository.findById(UUID.randomUUID()), value);
    }

    @Test
    @DisplayName("支払の概要で状態が列挙にないとき、例外の出力に値が出ない")
    void hidesStatusInSummary() {
        // 準備：状態が列挙にない概要の行
        var value = RestoreFailures.value();
        var row = RECORDS.newRecord(PAYMENT_.ORDER_ID, PAYMENT_.STATUS, PAYMENT_.DEADLINE).values(UUID.randomUUID(), value, Instant.EPOCH);
        var dsl = new RestoreFailures().with("payment", row).dsl();

        // 操作と確認：概要を読むと失敗し、出力に値がない
        assertValueIsHidden(() -> new JooqPaymentQueryRepository(dsl).findSummaries(List.of(UUID.randomUUID())), value);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 必須の列を埋めた支払の行を作る。
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
