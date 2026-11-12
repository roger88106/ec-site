package com.ecsite.module.payment.infrastructure.persistence;

import com.ecsite.RestoreFailures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.RestoreFailures.RECORDS;
import static com.ecsite.RestoreFailures.assertValueIsHidden;
import static com.ecsite.jooq.payment.Tables.PAYMENT_;
import static org.mockito.Mockito.mock;

/**
 * 支払を DB の値から戻すときの失敗の単体テスト
 *
 * <p>例外の出力に DB の値が出ないことを確認する。
 */
class PaymentRestoreFailureTest {

    @Test
    @DisplayName("支払の状態が列挙にないとき、例外の出力に値が出ない")
    void hidesStatus() {
        var value = RestoreFailures.value();
        var payment = RECORDS.newRecord(PAYMENT_);
        payment.setId(UUID.randomUUID());
        payment.setAmount(0L);
        payment.setStatus(value);
        var dsl = new RestoreFailures().with("payment", payment).dsl();
        var repository = new JooqPaymentRepository(dsl, mock(ApplicationEventPublisher.class), Clock.systemUTC());

        assertValueIsHidden(() -> repository.findById(UUID.randomUUID()), value);
    }

    @Test
    @DisplayName("支払の概要で状態が列挙にないとき、例外の出力に値が出ない")
    void hidesStatusInSummary() {
        var value = RestoreFailures.value();
        var row = RECORDS.newRecord(PAYMENT_.ORDER_ID, PAYMENT_.STATUS, PAYMENT_.DEADLINE).values(UUID.randomUUID(), value, Instant.EPOCH);
        var dsl = new RestoreFailures().with("payment", row).dsl();

        assertValueIsHidden(() -> new JooqPaymentQueryRepository(dsl).findSummaries(List.of(UUID.randomUUID())), value);
    }
}
