/**
 * 通知モジュール。<br />
 * 消費者へのメールを送る（docs/design/modules/notification.md）。今は出荷通知メールだけ。
 * 注文のイベントだけを受け、他のモジュールの API は呼ばない。
 */
@org.springframework.modulith.ApplicationModule(id = "notification", allowedDependencies = {"order::event"})
package com.ecsite.module.notification;
