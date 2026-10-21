/**
 * 出荷モジュールが発行するイベント。<br />
 * 利用者：レポート（配送状態の変化）。出荷は注文に依存しないので、レポートが受けても循環しない。
 */
@org.springframework.modulith.NamedInterface("event")
package com.ecsite.module.shipping.event;
