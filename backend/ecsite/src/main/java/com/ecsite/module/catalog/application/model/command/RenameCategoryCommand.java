package com.ecsite.module.catalog.application.model.command;

import lombok.NonNull;

import java.util.UUID;

/**
 * カテゴリ名の変更コマンド
 *
 * @param categoryId カテゴリ ID
 * @param name       新しい名称
 * @param version    取得時のバージョン
 */
public record RenameCategoryCommand(@NonNull UUID categoryId, @NonNull String name, long version) {
}
