/**
 * 会員モジュール
 *
 * <p>IdP のアカウントと、EC の会員 ID の対応を管理する。
 * メールアドレスと氏名は持たない（IdP が正本で、token から読む）。
 *
 * <p>レベル B（DDD を簡略にした構成）。不変条件が「1 つのアカウントに 1 人の会員」だけであるため。
 */
@org.springframework.modulith.ApplicationModule(id = "member", allowedDependencies = {})
package com.ecsite.module.member;
