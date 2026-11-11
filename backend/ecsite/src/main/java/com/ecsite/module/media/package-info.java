/**
 * メディアモジュール
 *
 * <p>利用者がアップロードするファイル（画像）の受け付け、検証、保存、配信の URL、削除を担う。
 * ファイルが何に使われるかは知らない。それは使う側のモジュールが持つ。
 *
 * <p>レベル B（DDD を簡略にした構成）。業務の不変条件がなく、ストレージとの連携が中心であるため。
 */
@org.springframework.modulith.ApplicationModule(id = "media", allowedDependencies = {})
package com.ecsite.module.media;
