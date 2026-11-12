package com.ecsite.module.inventory.infrastructure.persistence;

import com.ecsite.RestoreFailures;
import com.ecsite.common.paging.PageRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.ecsite.RestoreFailures.RECORDS;
import static com.ecsite.RestoreFailures.assertValueIsHidden;
import static com.ecsite.jooq.inventory.Tables.STOCK_ADJUSTMENT;
import static com.ecsite.jooq.inventory.Tables.STOCK_ITEM;

/**
 * 在庫の調整履歴を DB の値から戻すときの失敗の単体テスト
 *
 * <p>例外の出力に DB の値が出ないことを確認する。
 */
class StockAdjustmentRestoreFailureTest {

    @Test
    @DisplayName("調整の理由が列挙にないとき、例外の出力に値が出ない")
    void hidesReason() {
        var value = RestoreFailures.value();
        var adjustment = RECORDS.newRecord(STOCK_ADJUSTMENT);
        adjustment.setDelta(1);
        adjustment.setReason(value);
        var dsl = new RestoreFailures()
                .with("stock_item", RECORDS.newRecord(STOCK_ITEM.ID).values(UUID.randomUUID()))
                .with("stock_adjustment", adjustment)
                .dsl();
        var repository = new JooqStockQueryRepository(dsl);

        assertValueIsHidden(() -> repository.findAdjustments(UUID.randomUUID(), new PageRequest(1, 20)), value);
    }
}
