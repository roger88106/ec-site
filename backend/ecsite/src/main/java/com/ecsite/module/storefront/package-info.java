/**
 * 店舗モジュール。<br />
 * C 側 API。複数のモジュールの結果を組み合わせて返す、参照専用の層（docs/03-機能仕様/storefront.md）。
 */
@org.springframework.modulith.ApplicationModule(id = "storefront", allowedDependencies = {"catalog::api", "inventory::api", "order::api", "member::api"})
package com.ecsite.module.storefront;
