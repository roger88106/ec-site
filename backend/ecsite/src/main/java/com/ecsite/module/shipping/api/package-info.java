/**
 * 出荷モジュールが他のモジュールに公開する API（同期呼び出し）。<br />
 * 利用者：注文（出荷を作る、配送状態を読む、返送の注文 ID を求める）、通知（配送業者の表示名のキーを求める）。
 */
@org.springframework.modulith.NamedInterface("api")
package com.ecsite.module.shipping.api;
