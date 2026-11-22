package com.ecsite.module.catalog.application.model.command;

import com.ecsite.common.model.id.CategoryId;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * カテゴリの登録コマンド
 *
 * @param name     名称
 * @param parentId 親カテゴリ ID。最上位なら null
 */
public record CreateCategoryCommand(@NonNull String name, @Nullable CategoryId parentId) {
}
