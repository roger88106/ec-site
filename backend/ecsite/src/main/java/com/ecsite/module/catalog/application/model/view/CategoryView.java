package com.ecsite.module.catalog.application.model.view;

import com.ecsite.common.model.id.CategoryId;
import org.jspecify.annotations.Nullable;

/**
 * カテゴリのビュー
 *
 * <p>子カテゴリを含まない。
 *
 * @param id           カテゴリ ID
 * @param parentId     親カテゴリ ID。最上位なら null
 * @param name         名称
 * @param depth        階層の深さ（最上位が 1）
 * @param displayOrder 兄弟の中の表示順
 * @param version      バージョン
 */
public record CategoryView(CategoryId id, @Nullable CategoryId parentId, String name, int depth, int displayOrder, long version) {
}
