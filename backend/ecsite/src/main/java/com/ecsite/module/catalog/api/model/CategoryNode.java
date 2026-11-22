package com.ecsite.module.catalog.api.model;

import com.ecsite.common.model.id.CategoryId;

import java.util.List;

/**
 * カテゴリの木の節
 *
 * @param id       カテゴリ ID
 * @param name     名称
 * @param children 子カテゴリ（運営者が決めた並び順）
 */
public record CategoryNode(CategoryId id, String name, List<CategoryNode> children) {
}
