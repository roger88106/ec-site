/**
 * 決済モジュール
 *
 * <p>注文の代金の支払（カードの与信）と、外部の決済サービスとのやり取りを管理する。
 * 注文に依存しない（注文が決済の API を呼ぶので、依存が循環しないようにするため）。
 *
 * <p>レベル A（DDD + Clean Architecture + CQRS）。支払の状態遷移に、決済サービスの通知の重複と順序の入れ替わり、
 * 期限切れとの競合が重なるため。
 */
@org.springframework.modulith.ApplicationModule(id = "payment", allowedDependencies = {})
package com.ecsite.module.payment;
