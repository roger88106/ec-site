/**
 * 商品カタログモジュール
 *
 * <p>商品、バリアント、カテゴリ、公開状態、商品検索を管理する。
 *
 * <p>レベル A（DDD + Clean Architecture + CQRS）。軸の組み合わせの一意性、公開の条件など、不変条件が多いため。
 */
@org.springframework.modulith.ApplicationModule(id = "catalog", allowedDependencies = {"tax::api", "media::api"})
package com.ecsite.module.catalog;
