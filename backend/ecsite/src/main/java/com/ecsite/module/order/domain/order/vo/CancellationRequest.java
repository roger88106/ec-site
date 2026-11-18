package com.ecsite.module.order.domain.order.vo;

import com.ecsite.common.model.value.Operator;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 消費者によるキャンセルの申請
 *
 * <p>1 つの注文に 1 回だけ。
 *
 * @param requestedAt 申請日時
 * @param reason      理由。なければ null
 * @param result      結果
 * @param decidedAt   承認・却下した日時。申請中なら null
 * @param decidedBy   承認・却下した運営者。申請中と、記録を始める前の判断は null
 */
@ValueObject
public record CancellationRequest(Instant requestedAt, @Nullable String reason, Result result,
                                  @Nullable Instant decidedAt, @Nullable Operator decidedBy) {

    /**
     * コンストラクタ。
     *
     * <p>申請中なら決定の日時と決定者がなく、承認・却下なら決定の日時があることを確認する。
     * 決定者の記録を始める前に決定した申請は、決定者がないので、承認・却下で決定者がないことは許す。
     *
     * @param requestedAt 申請日時
     * @param reason      理由
     * @param result      結果
     * @param decidedAt   承認・却下した日時
     * @param decidedBy   承認・却下した運営者。申請中と、記録を始める前の判断は null
     */
    public CancellationRequest {
        if (result == Result.PENDING) {
            if (decidedAt != null || decidedBy != null) {
                throw new IllegalArgumentException("申請中の申請に、決定の日時か決定者がある");
            }
        } else if (decidedAt == null) {
            throw new IllegalArgumentException("承認・却下した申請に、決定の日時がない");
        }
    }

    /**
     * 申請の結果
     */
    public enum Result {

        /**
         * 申請中
         */
        PENDING,

        /**
         * 承認
         */
        APPROVED,

        /**
         * 却下
         */
        REJECTED,
    }
}
