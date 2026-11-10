/**
 * 税モジュール
 *
 * <p>税区分と、適用期間付きの税率を管理し、税込価格と税率ごとの消費税額を計算する。
 *
 * <p>レベル A（DDD + Clean Architecture + CQRS）。期間の重複・途切れの禁止など、不変条件が多いため。
 */
@org.springframework.modulith.ApplicationModule(id = "tax", allowedDependencies = {})
package com.ecsite.module.tax;
