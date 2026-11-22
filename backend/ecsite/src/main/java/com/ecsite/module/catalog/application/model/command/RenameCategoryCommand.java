package com.ecsite.module.catalog.application.model.command;

import com.ecsite.common.model.id.CategoryId;
import lombok.NonNull;

/**
 * カテゴリ名の変更コマンド
 *
 * @param categoryId カテゴリ ID
 * @param name       新しい名称
 * @param version    取得時のバージョン
 */
public record RenameCategoryCommand(@NonNull CategoryId categoryId, @NonNull String name, long version) {
}
