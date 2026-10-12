/**
 * 会員モジュール。<br />
 * IdP のアカウントと、EC の会員 ID の対応を管理する（docs/modules/member.md）。
 * メールアドレスと氏名は持たない（IdP が正本で、token から読む）。
 */
@org.springframework.modulith.ApplicationModule(id = "member", allowedDependencies = {})
package com.ecsite.module.member;
