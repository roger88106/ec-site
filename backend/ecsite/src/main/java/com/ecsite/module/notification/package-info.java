/**
 * 通知モジュール
 *
 * <p>消費者へのメールを送る。今は出荷通知メールだけ。
 * 注文のイベントを受け、配送業者の表示名のキーだけ出荷モジュールの API から得る（infrastructure/directory）。
 *
 * <p>レベル B（DDD を簡略にした構成）。業務の規則が「同じ注文に同じ種類のメールは 1 通」だけで、
 * 集約に守らせる不変条件がないため。
 */
@org.springframework.modulith.ApplicationModule(id = "notification",
        allowedDependencies = {"order::event", "shipping::api"})
package com.ecsite.module.notification;
