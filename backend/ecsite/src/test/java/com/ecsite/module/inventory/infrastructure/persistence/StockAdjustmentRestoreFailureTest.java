package com.ecsite.module.inventory.infrastructure.persistence;

import com.ecsite.common.model.id.VariantId;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.support.RestoreFailures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.ecsite.jooq.inventory.Tables.STOCK_ADJUSTMENT;
import static com.ecsite.jooq.inventory.Tables.STOCK_ITEM;
import static com.ecsite.support.RestoreFailures.RECORDS;
import static com.ecsite.support.RestoreFailures.assertValueIsHidden;

/**
 * 在庫の調整履歴を DB の値から戻すときの失敗の単体テスト
 *
 * <p>例外の出力に DB の値が出ないことを確認する。
 */
class StockAdjustmentRestoreFailureTest {

    @Test
    @DisplayName("調整の理由が列挙にないとき、例外の出力に値が出ない")
    void hidesReason() {
        // 準備：列挙にない理由の調整履歴を返す DB
        var value = RestoreFailures.value();
        var adjustment = RECORDS.newRecord(STOCK_ADJUSTMENT);
        adjustment.setId(UUID.randomUUID());
        adjustment.setDelta(1);
        adjustment.setReason(value);
        var dsl = new RestoreFailures()
                .with("stock_item", RECORDS.newRecord(STOCK_ITEM.ID).values(UUID.randomUUID()))
                .with("stock_adjustment", adjustment)
                .dsl();
        var repository = new JooqStockQueryRepository(dsl);

        // 操作と確認：履歴の取得が失敗し、例外の出力に理由の値が出ない
        assertValueIsHidden(() -> repository.findAdjustments(new VariantId(UUID.randomUUID()), new PageRequest(1, 20)), value,
                IllegalArgumentException.class);
    }
}
