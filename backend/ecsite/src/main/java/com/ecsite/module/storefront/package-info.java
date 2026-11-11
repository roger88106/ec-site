/**
 * 店舗モジュール
 *
 * <p>C 側の参照用の API。複数のモジュールの結果を組み合わせて返し、自分のデータは持たない。
 *
 * <p>レベル B（DDD を簡略にした構成）。参照だけで、業務の状態を変える操作がないため。
 */
@org.springframework.modulith.ApplicationModule(id = "storefront", allowedDependencies = {"catalog::api", "inventory::api", "order::api", "member::api"})
package com.ecsite.module.storefront;
