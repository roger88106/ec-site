/**
 * レポートモジュール
 *
 * <p>注文と出荷のイベントを受けて、注文の写しと状態の履歴を持ち、売上のレポートを返す。
 * 写しは、イベントを受けるたびに注文の API から読み直して上書きする。
 *
 * <p>レベル B（DDD を簡略にした構成）。業務の状態を変える操作がなく、持つのは他のモジュールの写しと受けたイベントの履歴だけで、
 * 守る不変条件がないため。
 */
@org.springframework.modulith.ApplicationModule(id = "reporting",
        allowedDependencies = {"order::api", "order::event", "shipping::event", "catalog::api", "region::api"})
package com.ecsite.module.reporting;
