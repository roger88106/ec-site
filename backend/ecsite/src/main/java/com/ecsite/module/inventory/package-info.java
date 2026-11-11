/**
 * 在庫モジュール
 *
 * <p>バリアントごとの在庫数と調整履歴、注文手続き中の引当を管理する。
 *
 * <p>レベル A（DDD + Clean Architecture + CQRS）。並行して調整されても在庫数の変更を失わないことと、引当と減算の判定が必要なため。
 * 在庫数は集約を読んで書き戻さず、DB の差分の加算と更新の条件で守るので、集約のクラスを持たない。
 */
@org.springframework.modulith.ApplicationModule(id = "inventory", allowedDependencies = {"catalog::event"})
package com.ecsite.module.inventory;
