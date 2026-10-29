/**
 * 商品カタログモジュール。<br />
 * 商品、バリアント、カテゴリ、公開状態、商品検索を管理する（docs/03-機能仕様/catalog.md）。
 */
@org.springframework.modulith.ApplicationModule(id = "catalog", allowedDependencies = {"tax::api", "media::api"})
package com.ecsite.module.catalog;
