/**
 * 地域モジュール。<br />
 * 都道府県・市区町村・郵便番号のマスターデータを持ち、参照だけを公開する（docs/design/modules/region.md）。
 * 業務のモジュールに依存せず、イベントを出さない（別のサービスに切り出せるようにするため）。
 */
@org.springframework.modulith.ApplicationModule(id = "region", allowedDependencies = {})
package com.ecsite.module.region;
