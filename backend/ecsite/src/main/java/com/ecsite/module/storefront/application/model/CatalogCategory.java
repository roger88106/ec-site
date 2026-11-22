package com.ecsite.module.storefront.application.model;

import com.ecsite.common.model.id.CategoryId;

import java.util.List;

/**
 * 商品カタログのカテゴリの木の節
 *
 * @param id       カテゴリ ID
 * @param name     名称
 * @param children 子カテゴリ（並び順）
 */
public record CatalogCategory(CategoryId id, String name, List<CatalogCategory> children) {
}
