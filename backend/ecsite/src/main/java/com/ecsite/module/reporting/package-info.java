/**
 * レポートモジュール。<br />
 * 注文と出荷のイベントを受けて、注文の写しと状態の履歴を持ち、売上のレポートを返す（docs/backend/modules/reporting.md）。
 * 写しは、イベントを受けるたびに注文の API から読み直して上書きする。他のモジュールはレポートに依存しない。
 */
@org.springframework.modulith.ApplicationModule(id = "reporting",
        allowedDependencies = {"order::api", "order::event", "shipping::event", "catalog::api", "region::api"})
package com.ecsite.module.reporting;
