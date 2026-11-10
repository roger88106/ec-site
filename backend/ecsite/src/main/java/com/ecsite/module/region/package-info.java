/**
 * 地域モジュール
 *
 * <p>都道府県・市区町村・郵便番号のマスターデータを持ち、参照だけを公開する。
 * 業務のモジュールに依存せず、イベントを出さない（別のサービスに切り出せるようにするため）。
 *
 * <p>レベル B（DDD を簡略にした構成）。参照だけのマスターデータで、業務の状態を変える操作がないため。
 */
@org.springframework.modulith.ApplicationModule(id = "region", allowedDependencies = {})
package com.ecsite.module.region;
