/**
 * 注文モジュール。<br />
 * 見積もり（金額と購入できるか）、注文手続き（引当の持ち主）、注文とキャンセルを管理する（docs/modules/order.md）。
 * 注文の成立とキャンセルは、在庫モジュールの API を同じトランザクションで呼ぶ（plan 002 第 2 節）。
 */
@org.springframework.modulith.ApplicationModule(id = "order",
        allowedDependencies = {"cart::api", "cart::event", "catalog::api", "inventory::api", "tax::api", "member::api"})
package com.ecsite.module.order;
