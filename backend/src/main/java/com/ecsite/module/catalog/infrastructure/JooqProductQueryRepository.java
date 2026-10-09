package com.ecsite.module.catalog.infrastructure;

import com.ecsite.jooq.catalog.tables.records.ProductRecord;
import com.ecsite.module.catalog.application.ProductQueryRepository;
import com.ecsite.module.catalog.application.model.view.ProductDetailView;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static com.ecsite.jooq.catalog.Tables.PRODUCT_AXIS;
import static com.ecsite.jooq.catalog.Tables.PRODUCT_CATEGORY;
import static com.ecsite.jooq.catalog.Tables.VARIANT;
import static com.ecsite.jooq.catalog.Tables.VARIANT_AXIS_VALUE;

/**
 * 商品照会のクエリリポジトリ
 */
@Repository
@RequiredArgsConstructor
class JooqProductQueryRepository implements ProductQueryRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * 商品の詳細を取得する。
     *
     * @param id 商品 ID
     * @return 商品の詳細
     */
    @Override
    public Optional<ProductDetailView> findDetailById(UUID id) {
        return dsl.selectFrom(PRODUCT)
                .where(PRODUCT.ID.eq(id))
                .fetchOptional()
                .map(this::toDetail);
    }

// ============================== プライベートメソッド ==============================

    /**
     * レコードから商品の詳細に変換する。商品の一部は別に取得する。
     *
     * @param record 商品のレコード
     * @return 商品の詳細（税込価格は空）
     */
    private ProductDetailView toDetail(ProductRecord record) {
        var id = record.getId();

        var axes = dsl.select(PRODUCT_AXIS.NAME)
                .from(PRODUCT_AXIS)
                .where(PRODUCT_AXIS.PRODUCT_ID.eq(id))
                .orderBy(PRODUCT_AXIS.POSITION)
                .fetch(PRODUCT_AXIS.NAME);

        var categoryIds = dsl.select(PRODUCT_CATEGORY.CATEGORY_ID)
                .from(PRODUCT_CATEGORY)
                .where(PRODUCT_CATEGORY.PRODUCT_ID.eq(id))
                .orderBy(PRODUCT_CATEGORY.CATEGORY_ID)
                .fetch(PRODUCT_CATEGORY.CATEGORY_ID);

        // バリアント ID → 軸の値（軸の並び）
        Map<UUID, List<String>> axisValuesByVariant = dsl.select(VARIANT_AXIS_VALUE.VARIANT_ID, VARIANT_AXIS_VALUE.VALUE)
                .from(VARIANT_AXIS_VALUE)
                .join(VARIANT).on(VARIANT.ID.eq(VARIANT_AXIS_VALUE.VARIANT_ID))
                .where(VARIANT.PRODUCT_ID.eq(id))
                .orderBy(VARIANT_AXIS_VALUE.VARIANT_ID, VARIANT_AXIS_VALUE.POSITION)
                .fetchGroups(VARIANT_AXIS_VALUE.VARIANT_ID, VARIANT_AXIS_VALUE.VALUE);

        var variants = dsl.selectFrom(VARIANT)
                .where(VARIANT.PRODUCT_ID.eq(id))
                .orderBy(VARIANT.DISPLAY_ORDER)
                .fetch(variant -> new ProductDetailView.VariantView(
                        variant.getId(),
                        variant.getVariantCode(),
                        axisValuesByVariant.getOrDefault(variant.getId(), List.of()),
                        variant.getSalesSuspended()));

        return new ProductDetailView(
                id,
                record.getProductCode(),
                record.getName(),
                record.getDescription(),
                record.getTaxCategoryId(),
                record.getPriceExclTax(),
                null,
                categoryIds,
                axes,
                record.getStatus(),
                record.getFirstShownAt(),
                record.getPublishStart(),
                record.getPublishEnd(),
                variants,
                record.getVersion(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}
