/**
 * 決済モジュール。<br />
 * 注文の代金の支払（カードの与信）と、外部の決済サービスとのやり取りを管理する（docs/03-機能仕様/payment.md）。
 * 注文から API で呼ばれ、結果をイベントで伝える。注文には依存しない（依存が循環しないようにするため）。
 */
@org.springframework.modulith.ApplicationModule(id = "payment", allowedDependencies = {})
package com.ecsite.module.payment;
