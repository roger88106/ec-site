package com.ecsite.module.storefront.application.model;

import java.util.List;
import java.util.UUID;

/**
 * 商品カタログから受け取った、カテゴリの木の節
 *
 * @param id       カテゴリ ID
 * @param name     名称
 * @param children 子カテゴリ（並び順）
 */
public record CatalogCategory(UUID id, String name, List<CatalogCategory> children) {
}
