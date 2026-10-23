package com.ecsite.module.catalog.application.model.command;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * カテゴリの登録コマンド
 *
 * @param name     名称
 * @param parentId 親カテゴリ ID。最上位なら null
 */
public record CreateCategoryCommand(String name, @Nullable UUID parentId) {
}
