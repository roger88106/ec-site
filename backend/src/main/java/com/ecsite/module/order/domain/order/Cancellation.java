package com.ecsite.module.order.domain.order;

import com.ecsite.common.model.Operator;
import jakarta.annotation.Nullable;
import org.jmolecules.ddd.annotation.ValueObject;

import java.time.Instant;

/**
 * キャンセル（US-21）
 *
 * @param cancelledAt キャンセル日時
 * @param byOperator  運営者の判断によるキャンセルか（申請の承認でない。AC-103）
 * @param reason      運営者の判断によるキャンセルの理由。申請の承認なら null
 * @param cancelledBy キャンセルした運営者（AC-142）。申請の承認なら承認した運営者。記録を始める前のキャンセルは null
 */
@ValueObject
public record Cancellation(Instant cancelledAt, boolean byOperator, @Nullable String reason,
                           @Nullable Operator cancelledBy) {
}
