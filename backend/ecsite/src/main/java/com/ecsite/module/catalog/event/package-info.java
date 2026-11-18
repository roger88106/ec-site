/**
 * 商品カタログモジュールの公開のイベント
 *
 * <p>他のモジュールが非同期で受け取る。バリアント登録だけは、在庫モジュールが商品カタログのトランザクションの中で同期で受け取る。
 */
@org.springframework.modulith.NamedInterface("event")
package com.ecsite.module.catalog.event;
