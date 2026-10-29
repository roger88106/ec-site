/**
 * メディアモジュール。<br />
 * 利用者がアップロードするファイル（画像）の受け付け、検証、保存、配信の URL、削除を担う（docs/03-機能仕様/media.md）。
 * ファイルが何に使われるかは知らない。それは使う側のモジュールが持つ。
 */
@org.springframework.modulith.ApplicationModule(id = "media", allowedDependencies = {})
package com.ecsite.module.media;
