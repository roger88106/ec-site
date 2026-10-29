/**
 * 通知モジュール。<br />
 * 消費者へのメールを送る（docs/03-機能仕様/notification.md）。今は出荷通知メールだけ。
 * 注文のイベントを受け、配送業者の表示名のキーだけ出荷モジュールの API から得る（infrastructure/directory）。
 */
@org.springframework.modulith.ApplicationModule(id = "notification",
        allowedDependencies = {"order::event", "shipping::api"})
package com.ecsite.module.notification;
