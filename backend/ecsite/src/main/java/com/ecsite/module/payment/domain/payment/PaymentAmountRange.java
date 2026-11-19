package com.ecsite.module.payment.domain.payment;

import com.ecsite.common.model.value.Money;
import lombok.NonNull;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * 支払 1 回の金額の範囲
 *
 * <p>下限以上、上限以下を範囲の中とする。決済サービスが 1 回の支払で受け付ける金額の範囲である。
 * 現在は骨組みで、範囲の値と判定は未実装（次のタスクで実装する）。
 *
 * @param min 下限（この金額を含む）
 * @param max 上限（この金額を含む）
 */
@ValueObject
public record PaymentAmountRange(@NonNull Money min, @NonNull Money max) {

    /**
     * 決済で受け付ける支払 1 回の金額の範囲（骨組みでは未実装の値）
     */
    public static final PaymentAmountRange STANDARD = new PaymentAmountRange(Money.ZERO, Money.ZERO);

    /**
     * 金額が範囲の中かを返す（骨組みでは常に false）。
     *
     * @param amount 判定する金額
     * @return 範囲の中なら true
     */
    public boolean contains(@NonNull Money amount) {
        return false;
    }
}
