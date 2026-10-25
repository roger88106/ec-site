package com.ecsite.architecture.money.module.sample.application.model;

import java.util.List;

/**
 * 金額の検査の例：名前が Command で終わるクラスの入れ子の record に、整数の価格がある。<br />
 * 構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 *
 * @param lines 明細
 */
public record PricedCommand(List<Line> lines) {

    /**
     * 入れ子の明細
     *
     * @param unitPrice 整数の単価（規則に合わない）
     */
    public record Line(long unitPrice) {
    }
}
