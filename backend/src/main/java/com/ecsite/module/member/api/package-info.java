/**
 * 会員モジュールが他のモジュールに公開する API（同期呼び出し）。<br />
 * 利用者：カート、注文、店舗の presentation 層（token の会員の会員 ID を求める）。
 */
@org.springframework.modulith.NamedInterface("api")
package com.ecsite.module.member.api;
