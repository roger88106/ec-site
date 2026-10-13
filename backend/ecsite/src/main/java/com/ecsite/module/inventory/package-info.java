/**
 * 在庫モジュール。<br />
 * バリアントごとの在庫数と、その調整履歴を管理する（docs/backend/modules/inventory.md）。
 */
@org.springframework.modulith.ApplicationModule(id = "inventory", allowedDependencies = {"catalog::event"})
package com.ecsite.module.inventory;
