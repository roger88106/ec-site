package com.ecsite.module.catalog.application.model.view;

import com.ecsite.module.catalog.domain.product.PublicationStatus;

import java.util.UUID;

/**
 * B 側の商品一覧の 1 件のビュー
 *
 * @param id           商品 ID
 * @param productCode  商品品番
 * @param name         商品名
 * @param status       公開状態
 * @param variantCount バリアント数
 */
public record ProductSummaryView(UUID id, String productCode, String name, PublicationStatus status,
                                 int variantCount) {
}
