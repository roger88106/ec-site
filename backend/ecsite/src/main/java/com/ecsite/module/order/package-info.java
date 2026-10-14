/**
 * 注文モジュール。<br />
 * 見積もり（金額と購入できるか）、注文手続き（引当の持ち主）、注文とキャンセルを管理する（docs/backend/modules/order.md）。
 * 注文の成立とキャンセルは、在庫モジュールと決済モジュールの API を同じトランザクションで呼ぶ。支払の結果は決済のイベントで受ける。
 * 出荷の登録は、決済の API で売上を確定してから、出荷モジュールの API で出荷を作る。
 */
@org.springframework.modulith.ApplicationModule(id = "order",
        allowedDependencies = {"cart::api", "cart::event", "catalog::api", "inventory::api", "tax::api", "member::api",
                "payment::api", "payment::event", "shipping::api"})
package com.ecsite.module.order;
