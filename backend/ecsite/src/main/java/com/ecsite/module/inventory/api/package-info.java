/**
 * 在庫モジュールが他のモジュールに公開する API（同期呼び出し）。<br />
 * 利用者：店舗（複数のバリアントについての在庫の有無）、カート（販売可能数）、注文（販売可能数と引当、注文による増減）。
 */
@org.springframework.modulith.NamedInterface("api")
package com.ecsite.module.inventory.api;
