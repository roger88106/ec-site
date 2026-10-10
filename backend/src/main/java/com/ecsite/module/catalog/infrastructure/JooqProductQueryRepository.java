package com.ecsite.module.catalog.infrastructure;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.common.persistence.LikePatterns;
import com.ecsite.jooq.catalog.tables.records.ProductRecord;
import com.ecsite.jooq.catalog.tables.records.ProductImageRecord;
import com.ecsite.module.catalog.application.ProductQueryRepository;
import com.ecsite.module.catalog.application.model.query.ProductListQuery;
import com.ecsite.module.catalog.application.model.view.ProductDetailView;
import com.ecsite.module.catalog.application.model.view.ProductSummaryView;
import com.ecsite.module.catalog.domain.product.port.MediaDirectory;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static com.ecsite.jooq.catalog.Tables.PRODUCT_AXIS;
import static com.ecsite.jooq.catalog.Tables.PRODUCT_CATEGORY;
import static com.ecsite.jooq.catalog.Tables.PRODUCT_IMAGE;
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
     * メディアの腐敗防止層（画像の配信の URL）
     */
    private final MediaDirectory mediaDirectory;

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

    /**
     * B 側の商品一覧を取得する。
     *
     * @param query   絞り込みの条件
     * @param request ページの指定
     * @return 商品の一覧
     */
    @Override
    public PageResult<ProductSummaryView> findSummaries(ProductListQuery query, PageRequest request) {
        var condition = summaryCondition(query);
        var variantCount = DSL.selectCount()
                .from(VARIANT)
                .where(VARIANT.PRODUCT_ID.eq(PRODUCT.ID))
                .<Integer>asField("variant_count");

        var items = dsl.select(PRODUCT.ID, PRODUCT.PRODUCT_CODE, PRODUCT.NAME, PRODUCT.STATUS, variantCount)
                .from(PRODUCT)
                .where(condition)
                .orderBy(PRODUCT.CREATED_AT.desc(), PRODUCT.ID)
                .limit(request.size())
                .offset(request.offset())
                .fetch(record -> new ProductSummaryView(
                        record.value1(), record.value2(), record.value3(), record.value4(), record.value5()));

        return PageResult.of(items, request, dsl.fetchCount(PRODUCT, condition));
    }

// ============================== プライベートメソッド ==============================

    /**
     * B 側の商品一覧の絞り込みの条件を作る（docs/modules/catalog.md）。
     *
     * @param query 絞り込みの条件
     * @return 条件
     */
    private static Condition summaryCondition(ProductListQuery query) {
        var condition = DSL.noCondition();
        if (query.code() != null && !query.code().isBlank()) {
            // 英小文字は大文字として扱う。品番は英大文字・数字・ハイフンだけなので、大文字にそろえて比べる
            var pattern = LikePatterns.startsWith(query.code().toUpperCase(Locale.ROOT));
            condition = condition.and(PRODUCT.PRODUCT_CODE.like(pattern, LikePatterns.ESCAPE)
                    .or(DSL.exists(DSL.selectOne()
                            .from(VARIANT)
                            .where(VARIANT.PRODUCT_ID.eq(PRODUCT.ID))
                            .and(VARIANT.VARIANT_CODE.like(pattern, LikePatterns.ESCAPE)))));
        }
        if (query.name() != null && !query.name().isBlank()) {
            condition = condition.and(PRODUCT.NAME.likeIgnoreCase(LikePatterns.contains(query.name()), LikePatterns.ESCAPE));
        }
        if (query.status() != null) {
            condition = condition.and(PRODUCT.STATUS.eq(query.status()));
        }
        if (query.categoryId() != null) {
            condition = condition.and(CategoryConditions.productInCategory(query.categoryId()));
        }
        return condition;
    }

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
                        variant.getSalesSuspended(),
                        variant.getImageId()));

        var imageRecords = dsl.selectFrom(PRODUCT_IMAGE)
                .where(PRODUCT_IMAGE.PRODUCT_ID.eq(id))
                .orderBy(PRODUCT_IMAGE.DISPLAY_ORDER)
                .fetch();
        var urls = mediaDirectory.urls(imageRecords.map(ProductImageRecord::getAssetId));
        var images = imageRecords.map(image -> new ProductDetailView.ImageView(image.getId(), urls.get(image.getAssetId())));

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
                images,
                variants,
                record.getVersion(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}
