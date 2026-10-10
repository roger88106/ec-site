/**
 * 店舗モジュール。<br />
 * C 側 API。複数のモジュールの結果を組み合わせて返す、参照専用の層（docs/modules/storefront.md）。
 */
@org.springframework.modulith.ApplicationModule(id = "storefront", allowedDependencies = {"catalog::api", "inventory::api", "order::api"})
package com.ecsite.module.storefront;
