package com.ecsite.module.catalog.api.model;

import java.util.List;
import java.util.UUID;

/**
 * カテゴリの木の節
 *
 * @param id       カテゴリ ID
 * @param name     名称
 * @param children 子カテゴリ（運営者が決めた並び順）
 */
public record CategoryNode(UUID id, String name, List<CategoryNode> children) {
}
