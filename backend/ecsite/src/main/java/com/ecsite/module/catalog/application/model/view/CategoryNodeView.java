package com.ecsite.module.catalog.application.model.view;

import com.ecsite.common.model.id.CategoryId;

import java.util.List;

/**
 * カテゴリの木の節のビュー
 *
 * @param id           カテゴリ ID
 * @param name         名称
 * @param depth        階層の深さ（最上位が 1）
 * @param displayOrder 兄弟の中の表示順
 * @param version      バージョン
 * @param children     子カテゴリ（並び順）
 * @param addChild     子のカテゴリを追加できる
 */
public record CategoryNodeView(CategoryId id, String name, int depth, int displayOrder, long version,
                               List<CategoryNodeView> children, boolean addChild) {
}
