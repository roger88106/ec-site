package com.ecsite.architecture.money.module.sample.application.model;

/**
 * 金額の検査の例：名前が View・Command で終わらないので、整数の価格があっても対象でない。<br />
 * 構成の検査の対象の絞り込みを確認するための例であり、本番のコードではない。
 *
 * @param price 整数の価格
 */
public record PricedRow(long price) {
}
