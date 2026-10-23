package com.ecsite.module.order.domain.order.vo;

import com.ecsite.common.model.value.Operator;
import com.ecsite.module.order.domain.order.CancellationKind;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * キャンセル
 *
 * @param cancelledAt キャンセル日時
 * @param kind        キャンセルの種類
 * @param reason      運営者の判断によるキャンセルの理由。申請の承認なら null
 * @param cancelledBy キャンセルした運営者。申請の承認なら承認した運営者。支払期限切れと、記録を始める前のキャンセルは null
 * @param restocked   在庫に戻したか。出荷済みの注文のキャンセルで、運営者が戻さないと選んだときだけ false
 */
@ValueObject
public record Cancellation(Instant cancelledAt, CancellationKind kind, @Nullable String reason,
                           @Nullable Operator cancelledBy, boolean restocked) {

    /**
     * 在庫に戻したキャンセル（出荷済みでない注文のキャンセル）
     *
     * @param cancelledAt キャンセル日時
     * @param kind        キャンセルの種類
     * @param reason      運営者の判断によるキャンセルの理由
     * @param cancelledBy キャンセルした運営者
     */
    public Cancellation(Instant cancelledAt, CancellationKind kind, @Nullable String reason, @Nullable Operator cancelledBy) {
        this(cancelledAt, kind, reason, cancelledBy, true);
    }
}
