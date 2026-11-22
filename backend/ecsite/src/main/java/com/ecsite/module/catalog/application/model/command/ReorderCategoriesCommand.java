package com.ecsite.module.catalog.application.model.command;

import com.ecsite.common.model.id.CategoryId;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 兄弟カテゴリの並び順の変更コマンド
 *
 * @param parentId    親カテゴリ ID。最上位のカテゴリを並べ替えるなら null
 * @param categoryIds 並べたい順のカテゴリ ID（兄弟の全て）
 */
public record ReorderCategoriesCommand(@Nullable CategoryId parentId, @NonNull List<CategoryId> categoryIds) {
}
