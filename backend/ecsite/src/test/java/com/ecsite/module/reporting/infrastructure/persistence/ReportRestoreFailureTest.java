package com.ecsite.module.reporting.infrastructure.persistence;

import com.ecsite.common.model.id.OrderId;
import com.ecsite.jooq.reporting.tables.records.StatusHistoryRecord;
import com.ecsite.module.reporting.domain.sales.Basis;
import com.ecsite.module.reporting.domain.sales.ReportPeriod;
import com.ecsite.module.reporting.domain.sales.SalesScope;
import com.ecsite.support.RestoreFailures;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Consumer;

import static com.ecsite.jooq.reporting.Tables.SALES_ORDER;
import static com.ecsite.jooq.reporting.Tables.STATUS_HISTORY;
import static com.ecsite.support.RestoreFailures.RECORDS;
import static com.ecsite.support.RestoreFailures.assertValueIsHidden;

/**
 * 集計の写しを DB の値から戻すときの失敗の単体テスト
 *
 * <p>例外の出力に DB の値が出ないことを確認する。
 */
class ReportRestoreFailureTest {

    /**
     * 例外のメッセージに入ると分かる値
     */
    private final String value = RestoreFailures.value();

    // ============================== テスト ==============================

    @Nested
    @DisplayName("組ごとの売上")
    class SegmentSales {

        @Test
        @DisplayName("性別が列挙にないとき、例外の出力に値が出ない")
        void hidesBuyerGender() {
            // 準備：性別の列が列挙にない値の、組ごとの売上の行
            var row = RECORDS.newRecord(SALES_ORDER.PREFECTURE_CODE, SALES_ORDER.BUYER_AGE, SALES_ORDER.BUYER_GENDER,
                            DSL.field("amount", BigDecimal.class), DSL.field("orders", Integer.class))
                    .values("13", (short) 30, value, BigDecimal.ONE, 1);
            var repository = new JooqReportQueryRepository(new RestoreFailures().with("sales_order", row).dsl());
            var period = new ReportPeriod(LocalDate.EPOCH, LocalDate.EPOCH);

            // 操作と確認：読むと失敗し、例外の出力に値が出ない
            assertValueIsHidden(() -> repository.segmentSales(period, Basis.ORDER, new SalesScope(null, false, false)), value, IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("状態の履歴")
    class History {

        @Test
        @DisplayName("出来事が列挙にないとき、例外の出力に値が出ない")
        void hidesHistoryKind() {
            // 操作と確認：出来事を壊した行を読むと失敗し、例外の出力に値が出ない
            assertHistoryHides(history -> history.setKind(value));
        }

        @Test
        @DisplayName("状態が列挙にないとき、例外の出力に値が出ない")
        void hidesHistoryStatus() {
            // 操作と確認：後の状態を壊した行を読むと失敗し、例外の出力に値が出ない
            assertHistoryHides(history -> history.setToStatus(value));
        }

        @Test
        @DisplayName("キャンセルの種類が列挙にないとき、例外の出力に値が出ない")
        void hidesHistoryCancellationKind() {
            // 操作と確認：キャンセルの種類を壊した行を読むと失敗し、例外の出力に値が出ない
            assertHistoryHides(history -> history.setCancellationKind(value));
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 行の一部を壊して状態の履歴を読み、例外の出力に値が出ないことを確認する。
     *
     * <p>壊す前の行は、注文の状態が受付になった変化。
     *
     * @param breaking 行を壊す処理
     */
    private void assertHistoryHides(Consumer<StatusHistoryRecord> breaking) {
        var history = RECORDS.newRecord(STATUS_HISTORY);
        history.setOrderId(UUID.randomUUID());
        history.setKind("ORDER_STATUS");
        history.setToStatus("ACCEPTED");
        history.setOccurredAt(Instant.EPOCH);
        breaking.accept(history);
        var dsl = new RestoreFailures().with("status_history", history).dsl();
        var repository = new JooqReportOrderRepository(dsl, JsonMapper.builder().build(), Clock.systemUTC());

        assertValueIsHidden(() -> repository.history(new OrderId(UUID.randomUUID())), value, IllegalArgumentException.class);
    }
}
