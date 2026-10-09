package com.ecsite.module.catalog.application.model.command;

import java.util.UUID;

/**
 * カテゴリ名の変更コマンド
 *
 * @param categoryId カテゴリ ID
 * @param name       新しい名称
 * @param version    取得時のバージョン
 */
public record RenameCategoryCommand(UUID categoryId, String name, long version) {
}
