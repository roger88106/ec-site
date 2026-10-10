/**
 * カートモジュール。<br />
 * 消費者のカートと、その品目（バリアントと数量）を管理する（docs/modules/cart.md）。
 * 価格は持たず、表示のたびに注文モジュールの見積もりで求める。
 */
@org.springframework.modulith.ApplicationModule(id = "cart", allowedDependencies = {"catalog::api", "inventory::api"})
package com.ecsite.module.cart;
