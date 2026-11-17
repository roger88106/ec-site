/**
 * カートモジュール
 *
 * <p>消費者のカートと、その品目（バリアントと数量）を管理する。価格は持たない。
 *
 * <p>レベル B（DDD を簡略にした構成）。業務の規則が品目の上限、数量の合算、期限、持ち主の確認で、状態遷移を持たないため。
 */
@org.springframework.modulith.ApplicationModule(id = "cart", allowedDependencies = {"catalog::api", "inventory::api", "member::api"})
package com.ecsite.module.cart;
