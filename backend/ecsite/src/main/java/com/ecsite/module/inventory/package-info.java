/**
 * 在庫モジュール。<br />
 * バリアントごとの在庫数と、その調整履歴を管理する（docs/03-機能仕様/inventory.md）。
 */
@org.springframework.modulith.ApplicationModule(id = "inventory", allowedDependencies = {"catalog::event"})
package com.ecsite.module.inventory;
