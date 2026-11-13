package com.ecsite.common.persistence;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * アドバイザリロックの用途
 *
 * <p>用途ごとに別の番号を持ち、同じ ID でも用途が違えばロックが重ならないようにする。
 * 番号は、動いているインスタンスとの間でロックがずれないよう、一度決めたら変えない。
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum AdvisoryLockSpace {

    /**
     * 会員ごとの注文（注文の時点の判定を、会員 ID で直列にする）
     */
    ORDER(1),

    /**
     * 注文ごとのレポートの写し（写しと状態の履歴の書き込みを、注文 ID で直列にする）
     */
    REPORTING(2);

    /**
     * 用途の番号（ロックのキーの 1 つ目）
     */
    private final int key;
}
