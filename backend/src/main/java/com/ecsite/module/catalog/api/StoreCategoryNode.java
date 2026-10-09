package com.ecsite.module.catalog.api;

import java.util.List;
import java.util.UUID;

/**
 * C 側のカテゴリの木の節（AC-52 の絞り込み用）
 *
 * @param id       カテゴリ ID
 * @param name     名称
 * @param children 子カテゴリ（運営者が決めた並び順）
 */
public record StoreCategoryNode(UUID id, String name, List<StoreCategoryNode> children) {
}
