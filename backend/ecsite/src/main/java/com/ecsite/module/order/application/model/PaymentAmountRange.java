package com.ecsite.module.order.application.model;

import com.ecsite.common.model.value.Money;
import lombok.NonNull;

/**
 * 決済が 1 回の支払で受け付ける金額の範囲（注文の側の型）
 *
 * <p>下限以上、上限以下を範囲の中とする。
 *
 * @param min 下限（この金額を含む）
 * @param max 上限（この金額を含む）
 */
public record PaymentAmountRange(@NonNull Money min, @NonNull Money max) {

    /**
     * コンストラクタ
     *
     * <p>下限が上限以下であることを検査する。
     */
    public PaymentAmountRange {
        if (min.yen() > max.yen()) {
            throw new IllegalArgumentException("支払 1 回の金額の下限は、上限以下にする");
        }
    }

    /**
     * 金額が範囲の中かを返す。
     *
     * @param amount 判定する金額
     * @return 下限以上で上限以下なら true
     */
    public boolean contains(@NonNull Money amount) {
        return amount.yen() >= min.yen() && amount.yen() <= max.yen();
    }
}
