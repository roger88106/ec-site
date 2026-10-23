package com.ecsite.module.inventory.domain.stock;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.value.BoundedText;
import com.ecsite.common.model.value.Operator;
import com.ecsite.module.inventory.domain.InventoryErrorCode;
import lombok.NonNull;
import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.Identity;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 在庫調整。増減量、調整理由、メモを持つ。注文に由来する調整は、注文 ID と注文番号の写しを持つ。<br />
 * 在庫数が負にならないことは、ここではなく、リポジトリの条件付きの更新と DB の CHECK 制約で守る（docs/design/modules/inventory.md）。
 *
 * @param id         在庫調整 ID
 * @param delta      増減量（0 以外）
 * @param reason     調整理由
 * @param memo        メモ。なければ null
 * @param orderId     注文 ID。注文に由来する調整でなければ null
 * @param orderNumber 注文番号（写し）。注文に由来する調整でなければ null
 * @param adjustedAt  調整日時
 * @param operator    調整した運営者。注文に由来する調整なら null
 */
@Entity
public record StockAdjustment(@Identity UUID id, int delta, StockAdjustmentReason reason, @Nullable String memo,
                              @Nullable UUID orderId, @Nullable String orderNumber, Instant adjustedAt,
                              @Nullable Operator operator) {

    /**
     * メモの最大の文字数
     */
    static final int MEMO_MAX_LENGTH = 500;

    /**
     * 運営者の 1 回の増減量の上限（絶対値）
     */
    static final int MAX_MANUAL_DELTA = 1_000_000;

    /**
     * 運営者の調整の後の在庫数の上限。注文のキャンセルで戻す調整には当てない（引き当てた分を戻すだけなので）
     */
    public static final int MAX_MANUAL_QUANTITY = 1_000_000;

    /**
     * 運営者の在庫調整を作る。空白だけのメモは、メモなしとして扱う。注文に由来する理由は選べない。
     *
     * @param delta  増減量（0 以外、±1,000,000 以内）
     * @param reason 調整理由
     * @param memo     メモ。理由が「その他」なら必須
     * @param operator 調整した運営者
     * @param now      現在時刻
     * @return 在庫調整
     */
    public static StockAdjustment create(int delta, @NonNull StockAdjustmentReason reason, @Nullable String memo,
                                         @NonNull Operator operator, @NonNull Instant now) {
        if (delta == 0 || delta > MAX_MANUAL_DELTA || delta < -MAX_MANUAL_DELTA) {
            throw BusinessException.of(InventoryErrorCode.INVALID_STOCK_DELTA, List.of(MAX_MANUAL_DELTA));
        }
        if (reason.isFromOrder()) {
            throw BusinessException.of(InventoryErrorCode.ORDER_REASON_NOT_SELECTABLE);
        }
        var normalizedMemo = memo == null || memo.isBlank() ? null : memo;
        if (reason == StockAdjustmentReason.OTHER && normalizedMemo == null) {
            throw BusinessException.of(InventoryErrorCode.STOCK_MEMO_REQUIRED);
        }
        if (normalizedMemo != null && !BoundedText.fitsWithin(normalizedMemo, MEMO_MAX_LENGTH)) {
            throw BusinessException.of(InventoryErrorCode.INVALID_STOCK_MEMO, List.of(MEMO_MAX_LENGTH));
        }
        return new StockAdjustment(UUID.randomUUID(), delta, reason, normalizedMemo, null, null, now, operator);
    }

    /**
     * 注文による在庫調整を作る。
     *
     * @param delta       増減量（注文は負、キャンセルは正）
     * @param reason      調整理由（注文・注文のキャンセル）
     * @param orderId     注文 ID
     * @param orderNumber 注文番号
     * @param now         現在時刻
     * @return 在庫調整
     */
    public static StockAdjustment forOrder(int delta, @NonNull StockAdjustmentReason reason, @NonNull UUID orderId,
                                           @NonNull String orderNumber, @NonNull Instant now) {
        if (delta == 0) {
            throw BusinessException.of(InventoryErrorCode.INVALID_STOCK_DELTA, List.of(MAX_MANUAL_DELTA));
        }
        if (!reason.isFromOrder()) {
            throw new IllegalArgumentException("注文に由来する理由ではありません: " + reason);
        }
        return new StockAdjustment(UUID.randomUUID(), delta, reason, null, orderId, orderNumber, now, null);
    }
}
