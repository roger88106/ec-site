package com.ecsite.module.reporting.infrastructure.persistence;

import com.ecsite.RestoreFailures;
import com.ecsite.jooq.reporting.tables.records.StatusHistoryRecord;
import com.ecsite.module.reporting.domain.sales.Basis;
import com.ecsite.module.reporting.domain.sales.ReportPeriod;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Consumer;

import static com.ecsite.RestoreFailures.RECORDS;
import static com.ecsite.RestoreFailures.assertValueIsHidden;
import static com.ecsite.jooq.reporting.Tables.SALES_ORDER;
import static com.ecsite.jooq.reporting.Tables.STATUS_HISTORY;

/**
 * 集計の写しを DB の値から戻すときの失敗の単体テスト。例外の出力に DB の値が出ないことを確認する。
 */
class ReportRestoreFailureTest {

    /**
     * 例外のメッセージに入ると分かる値
     */
    private final String value = RestoreFailures.value();

    @Test
    @DisplayName("組ごとの売上で性別が列挙にないとき、例外の出力に値が出ない")
    void hidesBuyerGender() {
        var row = RECORDS.newRecord(SALES_ORDER.PREFECTURE_CODE, SALES_ORDER.BUYER_AGE, SALES_ORDER.BUYER_GENDER,
                        DSL.field("amount", BigDecimal.class), DSL.field("orders", Integer.class))
                .values("13", (short) 30, value, BigDecimal.ONE, 1);
        var repository = new JooqReportQueryRepository(new RestoreFailures().with("sales_order", row).dsl());
        var period = ReportPeriod.of(LocalDate.EPOCH, LocalDate.EPOCH);

        assertValueIsHidden(() -> repository.segmentSales(period, Basis.ORDER, null, false, false), value);
    }

    @Test
    @DisplayName("状態の履歴の出来事が列挙にないとき、例外の出力に値が出ない")
    void hidesHistoryKind() {
        assertHistoryHides(history -> history.setKind(value));
    }

    @Test
    @DisplayName("状態の履歴の状態が列挙にないとき、例外の出力に値が出ない")
    void hidesHistoryStatus() {
        assertHistoryHides(history -> history.setToStatus(value));
    }

    @Test
    @DisplayName("状態の履歴のキャンセルの種類が列挙にないとき、例外の出力に値が出ない")
    void hidesHistoryCancellationKind() {
        assertHistoryHides(history -> history.setCancellationKind(value));
    }

    /**
     * 行の一部を壊して状態の履歴を読み、例外の出力に値が出ないことを確認する。
     *
     * @param breaking 行を壊す処理
     */
    private void assertHistoryHides(Consumer<StatusHistoryRecord> breaking) {
        var history = RECORDS.newRecord(STATUS_HISTORY);
        history.setKind("ORDER_STATUS");
        history.setToStatus("ACCEPTED");
        history.setOccurredAt(Instant.EPOCH);
        breaking.accept(history);
        var dsl = new RestoreFailures().with("status_history", history).dsl();
        var repository = new JooqReportOrderRepository(dsl, JsonMapper.builder().build(), Clock.systemUTC());

        assertValueIsHidden(() -> repository.history(UUID.randomUUID()), value);
    }
}
