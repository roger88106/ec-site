package com.ecsite.module.catalog.application.model;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.catalog.application.model.view.ProductDetailView;
import com.ecsite.module.catalog.domain.product.vo.PublicationStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 保存されている商品の詳細
 *
 * <p>DB に保存されている値だけを持つ。税込価格とできる操作は、現在時刻や税の値で決まるので持たない。
 * サービスが、この型に求めた値を加えて {@link ProductDetailView} を作る。
 *
 * @param id            商品 ID
 * @param productCode   商品品番
 * @param name          商品名
 * @param description   説明
 * @param taxCategoryId 税区分 ID
 * @param priceExclTax  税抜価格
 * @param categoryIds   所属カテゴリの ID
 * @param axes          バリアント軸の名前（並び順）
 * @param status        公開状態
 * @param firstShownAt  初回表示日時
 * @param publishStart  公開開始日時
 * @param publishEnd    公開終了日時
 * @param images        画像（表示順。先頭が代表画像）
 * @param variants      バリアント（表示順）
 * @param version       バージョン
 * @param createdAt     登録日時
 * @param updatedAt     更新日時
 */
public record StoredProductDetail(UUID id, String productCode, String name, String description, UUID taxCategoryId,
                                  Money priceExclTax, List<UUID> categoryIds, List<String> axes,
                                  PublicationStatus status, @Nullable Instant firstShownAt,
                                  @Nullable Instant publishStart, @Nullable Instant publishEnd,
                                  List<ProductDetailView.ImageView> images,
                                  List<ProductDetailView.VariantView> variants,
                                  long version, Instant createdAt, Instant updatedAt) {
}
