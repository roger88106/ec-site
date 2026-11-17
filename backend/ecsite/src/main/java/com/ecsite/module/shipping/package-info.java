/**
 * 出荷モジュール
 *
 * <p>注文の出荷と配送状態を管理し、外部の物流サービスとのやり取りを包む。
 * 注文に依存しない（注文が出荷の API を呼ぶので、依存が循環しないようにするため）。
 *
 * <p>レベル B（DDD を簡略にした構成）。不変条件が配送状態の順位だけで、状態の履歴に業務の価値がないため。
 */
@org.springframework.modulith.ApplicationModule(id = "shipping", allowedDependencies = {})
package com.ecsite.module.shipping;
