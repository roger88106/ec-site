package com.ecsite.module.inventory.domain.stock;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.value.BoundedText;
import com.ecsite.common.model.value.Operator;
import com.ecsite.module.inventory.domain.InventoryErrorCode;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.Identity;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 在庫調整
 *
 * <p>在庫数が負にならないことは、この型ではなく、リポジトリの条件付きの更新と DB の CHECK 制約で守る。
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
public final class StockAdjustment {

    /**
     * 在庫調整 ID
     */
    @Identity
    @EqualsAndHashCode.Include
    private final UUID id;

    /**
     * 増減量（0 以外）
     */
    private final int delta;

    /**
     * 調整理由
     */
    private final StockAdjustmentReason reason;

    /**
     * メモ。なければ null
     */
    @Nullable
    private final String memo;

    /**
     * 注文 ID。注文に由来する調整でなければ null
     */
    @Nullable
    private final UUID orderId;

    /**
     * 注文番号（写し）。注文に由来する調整でなければ null
     */
    @Nullable
    private final String orderNumber;

    /**
     * 調整日時
     */
    private final Instant adjustedAt;

    /**
     * 調整した運営者。注文に由来する調整なら null
     */
    @Nullable
    private final Operator operator;

    /**
     * メモの最大の文字数
     */
    static final int MEMO_MAX_LENGTH = 500;

    /**
     * 運営者の 1 回の増減量の上限（絶対値）
     */
    static final int MAX_MANUAL_DELTA = 1_000_000;

    /**
     * 運営者の調整の後の在庫数の上限
     *
     * <p>注文のキャンセルで戻す調整には当てない（引き当てた分を戻すだけなので）。
     */
    public static final int MAX_MANUAL_QUANTITY = 1_000_000;

    /**
     * 全属性コンストラクタ
     *
     * <p>作り方によらない不変条件を検査する。運営者の調整だけの規則（理由の選べる範囲、増減量の上限、メモの必須）は、
     * {@link #create} で検査する。
     *
     * <p>注文 ID と注文番号の一方だけがあるとき、IllegalArgumentException を投げる。
     *
     * @param id          在庫調整 ID
     * @param delta       増減量
     * @param reason      調整理由
     * @param memo        メモ
     * @param orderId     注文 ID
     * @param orderNumber 注文番号
     * @param adjustedAt  調整日時
     * @param operator    調整した運営者
     * @throws BusinessException 増減量が 0（{@code INVALID_STOCK_DELTA}）
     * @throws BusinessException メモが長すぎる（{@code INVALID_STOCK_MEMO}）
     */
    StockAdjustment(@NonNull UUID id, int delta, @NonNull StockAdjustmentReason reason, @Nullable String memo,
                    @Nullable UUID orderId, @Nullable String orderNumber, @NonNull Instant adjustedAt,
                    @Nullable Operator operator) {
        verifyDeltaNotZero(delta);
        if (memo != null && !BoundedText.fitsWithin(memo, MEMO_MAX_LENGTH)) {
            throw BusinessException.of(InventoryErrorCode.INVALID_STOCK_MEMO, List.of(MEMO_MAX_LENGTH));
        }
        if ((orderId == null) != (orderNumber == null)) {
            throw new IllegalArgumentException("注文 ID と注文番号は、両方あるか両方ないかのどちらかです");
        }
        this.id = id;
        this.delta = delta;
        this.reason = reason;
        this.memo = memo;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.adjustedAt = adjustedAt;
        this.operator = operator;
    }

    /**
     * 運営者の在庫調整を作る。
     *
     * <p>空白だけのメモは、メモなしとして扱う。
     *
     * @param delta    増減量（0 以外、±{@value #MAX_MANUAL_DELTA} 以内）
     * @param reason   調整理由
     * @param memo     メモ。理由が「その他」なら必須
     * @param operator 調整した運営者
     * @param now      現在時刻
     * @return 在庫調整
     * @throws BusinessException 増減量が 0 か範囲の外（{@code INVALID_STOCK_DELTA}）
     * @throws BusinessException 理由が注文に由来する（{@code ORDER_REASON_NOT_SELECTABLE}）
     * @throws BusinessException 理由が「その他」でメモがない（{@code STOCK_MEMO_REQUIRED}）
     * @throws BusinessException メモが長すぎる（{@code INVALID_STOCK_MEMO}）
     */
    public static StockAdjustment create(int delta, @NonNull StockAdjustmentReason reason, @Nullable String memo,
                                         @NonNull Operator operator, @NonNull Instant now) {
        // 0 と、絶対値が上限を超える増減量は、他の条件より先に断る（エラーの順を保つ）
        verifyDeltaNotZero(delta);
        if (delta > MAX_MANUAL_DELTA || delta < -MAX_MANUAL_DELTA) {
            throw BusinessException.of(InventoryErrorCode.INVALID_STOCK_DELTA, List.of(MAX_MANUAL_DELTA));
        }
        if (reason.isFromOrder()) {
            throw BusinessException.of(InventoryErrorCode.ORDER_REASON_NOT_SELECTABLE);
        }
        var normalizedMemo = memo == null || memo.isBlank() ? null : memo;
        if (reason == StockAdjustmentReason.OTHER && normalizedMemo == null) {
            throw BusinessException.of(InventoryErrorCode.STOCK_MEMO_REQUIRED);
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
     * @throws BusinessException 増減量が 0（{@code INVALID_STOCK_DELTA}）
     */
    public static StockAdjustment forOrder(int delta, @NonNull StockAdjustmentReason reason, @NonNull UUID orderId,
                                           @NonNull String orderNumber, @NonNull Instant now) {
        verifyDeltaNotZero(delta);
        if (!reason.isFromOrder()) {
            throw new IllegalArgumentException("注文に由来する理由ではありません: " + reason);
        }
        return new StockAdjustment(UUID.randomUUID(), delta, reason, null, orderId, orderNumber, now, null);
    }

    /**
     * 増減量が 0 でないことを検証する。
     *
     * @param delta 増減量
     * @throws BusinessException 増減量が 0（{@code INVALID_STOCK_DELTA}）
     */
    private static void verifyDeltaNotZero(int delta) {
        if (delta == 0) {
            throw BusinessException.of(InventoryErrorCode.INVALID_STOCK_DELTA, List.of(MAX_MANUAL_DELTA));
        }
    }
}
