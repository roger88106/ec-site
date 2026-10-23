package com.ecsite.module.catalog.application.model.command;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 兄弟カテゴリの並び順の変更コマンド
 *
 * @param parentId    親カテゴリ ID。最上位のカテゴリを並べ替えるなら null
 * @param categoryIds 並べたい順のカテゴリ ID（兄弟のすべて）
 */
public record ReorderCategoriesCommand(@Nullable UUID parentId, List<UUID> categoryIds) {
}
