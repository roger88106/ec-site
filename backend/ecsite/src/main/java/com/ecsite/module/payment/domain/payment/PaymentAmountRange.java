package com.ecsite.module.payment.domain.payment;

import com.ecsite.common.model.value.Money;
import lombok.NonNull;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * 支払 1 回の金額の範囲
 *
 * <p>下限以上、上限以下を範囲の中とする。決済サービスが 1 回の支払で受け付ける金額の範囲である。
 *
 * @param min 下限（この金額を含む）
 * @param max 上限（この金額を含む）
 */
@ValueObject
public record PaymentAmountRange(@NonNull Money min, @NonNull Money max) {

    /**
     * 決済で受け付ける支払 1 回の金額の範囲（50 円以上 99,999,999 円以下）
     */
    public static final PaymentAmountRange STANDARD = new PaymentAmountRange(Money.of(50), Money.of(99_999_999));

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
