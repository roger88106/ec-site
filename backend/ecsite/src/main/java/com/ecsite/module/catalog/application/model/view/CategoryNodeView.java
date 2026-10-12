package com.ecsite.module.catalog.application.model.view;

import java.util.List;
import java.util.UUID;

/**
 * カテゴリの木の節のビュー
 *
 * @param id           カテゴリ ID
 * @param name         名称
 * @param depth        階層の深さ（最上位が 1）
 * @param displayOrder 兄弟の中の表示順
 * @param version      バージョン
 * @param children     子カテゴリ（並び順）
 */
public record CategoryNodeView(UUID id, String name, int depth, int displayOrder, long version,
                               List<CategoryNodeView> children) {
}
