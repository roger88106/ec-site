package com.ecsite.architecture.money.module.sample.application.model.view;

/**
 * 金額の検査の例：view のパッケージにあるので、名前が View・Command で終わらなくても整数の金額は対象になる。<br />
 * 構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 *
 * @param amount 整数の金額（規則に合わない）
 */
public record PricedReport(long amount) {
}
