package com.ecsite.module.catalog.application.model.command;

import java.util.List;
import java.util.UUID;

/**
 * バリアントの登録コマンド
 *
 * @param productId   商品 ID
 * @param variantCode バリアント品番
 * @param axisValues  軸の値（商品の軸と同じ並び）
 * @param version     取得時の商品のバージョン
 */
public record AddVariantCommand(UUID productId, String variantCode, List<String> axisValues, long version) {
}
