package com.ecsite.module.order.domain.order;

import com.ecsite.common.model.value.Operator;
import jakarta.annotation.Nullable;
import org.jmolecules.ddd.annotation.ValueObject;

import java.time.Instant;

/**
 * キャンセル
 *
 * @param cancelledAt キャンセル日時
 * @param kind        キャンセルの種類
 * @param reason      運営者の判断によるキャンセルの理由。申請の承認なら null
 * @param cancelledBy キャンセルした運営者。申請の承認なら承認した運営者。支払期限切れと、記録を始める前のキャンセルは null
 */
@ValueObject
public record Cancellation(Instant cancelledAt, CancellationKind kind, @Nullable String reason,
                           @Nullable Operator cancelledBy) {
}
