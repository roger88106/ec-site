package com.ecsite.module.order.domain.order;

import jakarta.annotation.Nullable;
import org.jmolecules.ddd.annotation.ValueObject;

import java.time.Instant;

/**
 * 消費者によるキャンセルの申請（US-18）。1 つの注文に 1 回だけ。
 *
 * @param requestedAt 申請日時
 * @param reason      理由。なければ null
 * @param result      結果
 * @param decidedAt   承認・却下した日時。申請中なら null
 */
@ValueObject
public record CancellationRequest(Instant requestedAt, @Nullable String reason, Result result,
                                  @Nullable Instant decidedAt) {

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
