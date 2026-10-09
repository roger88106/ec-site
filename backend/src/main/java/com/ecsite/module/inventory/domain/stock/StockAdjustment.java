package com.ecsite.module.inventory.domain.stock;

import com.ecsite.common.error.exception.BusinessException;
import lombok.NonNull;
import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.Identity;

import java.time.Instant;
import java.util.UUID;

/**
 * 在庫調整（AC-42、46）。増減量、調整理由、メモを持つ。<br />
 * 在庫数が負にならないことは、ここではなく、リポジトリの条件付きの更新と DB の CHECK 制約で守る（docs/modules/inventory.md）。
 *
 * @param id         在庫調整 ID
 * @param delta      増減量（0 以外）
 * @param reason     調整理由
 * @param memo       メモ。なければ null
 * @param adjustedAt 調整日時
 */
@Entity
public record StockAdjustment(@Identity UUID id, int delta, StockAdjustmentReason reason, String memo, Instant adjustedAt) {

    /**
     * メモの最大の文字数
     */
    static final int MEMO_MAX_LENGTH = 500;

    /**
     * 在庫調整を作る。空白だけのメモは、メモなしとして扱う。
     *
     * @param delta  増減量（0 以外）
     * @param reason 調整理由
     * @param memo   メモ。理由が「その他」なら必須
     * @param now    現在時刻
     * @return 在庫調整
     */
    public static StockAdjustment create(int delta, @NonNull StockAdjustmentReason reason, String memo, @NonNull Instant now) {
        if (delta == 0) {
            throw BusinessException.of(InventoryErrorCode.INVALID_STOCK_DELTA);
        }
        var normalizedMemo = memo == null || memo.isBlank() ? null : memo;
        if (reason == StockAdjustmentReason.OTHER && normalizedMemo == null) {
            throw BusinessException.of(InventoryErrorCode.STOCK_MEMO_REQUIRED);
        }
        if (normalizedMemo != null && normalizedMemo.codePointCount(0, normalizedMemo.length()) > MEMO_MAX_LENGTH) {
            throw BusinessException.of(InventoryErrorCode.INVALID_STOCK_MEMO);
        }
        return new StockAdjustment(UUID.randomUUID(), delta, reason, normalizedMemo, now);
    }
}
