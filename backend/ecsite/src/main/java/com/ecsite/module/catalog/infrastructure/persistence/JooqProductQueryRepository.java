package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.common.model.base.RestoreGuard;
import com.ecsite.common.model.value.Money;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.jooq.catalog.tables.records.ProductImageRecord;
import com.ecsite.jooq.catalog.tables.records.ProductRecord;
import com.ecsite.module.catalog.application.model.query.ProductListQuery;
import com.ecsite.module.catalog.application.model.view.ProductDetailView;
import com.ecsite.module.catalog.application.model.view.ProductSummaryView;
import com.ecsite.module.catalog.application.port.MediaDirectory;
import com.ecsite.module.catalog.application.port.ProductQueryRepository;
import com.ecsite.module.catalog.domain.product.PublicationStatus;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static com.ecsite.jooq.catalog.Tables.PRODUCT_IMAGE;
import static com.ecsite.jooq.catalog.Tables.VARIANT;

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
        var axes = ProductParts.axes(PRODUCT.ID);
        var categoryIds = ProductParts.categoryIds(PRODUCT.ID);
        var found = dsl.select(PRODUCT.asterisk(), axes, categoryIds)
                .from(PRODUCT)
                .where(PRODUCT.ID.eq(id))
                .fetchOptional();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        var row = found.get();
        return Optional.of(toDetail(row.into(PRODUCT), List.of(row.get(axes)), List.of(row.get(categoryIds))));
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
        // 商品ごとのバリアントの数（行ごとに数える副問い合わせ）
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
                        record.get(PRODUCT.ID), record.get(PRODUCT.PRODUCT_CODE), record.get(PRODUCT.NAME),
                        RestoreGuard.restore("product", () -> PublicationStatus.valueOf(record.get(PRODUCT.STATUS))),
                        record.get(variantCount)));

        return PageResult.of(items, request, dsl.fetchCount(PRODUCT, condition));
    }

// ============================== プライベートメソッド ==============================

    /**
     * B 側の商品一覧の絞り込みの条件を作る。
     *
     * @param query 絞り込みの条件
     * @return 条件
     */
    private static Condition summaryCondition(ProductListQuery query) {
        var condition = DSL.noCondition();
        if (query.code() != null && !query.code().isBlank()) {
            // 英小文字は大文字として扱う。品番は英大文字・数字・ハイフンだけなので、大文字にそろえて比べる
            var code = query.code().toUpperCase(Locale.ROOT);
            condition = condition.and(PRODUCT.PRODUCT_CODE.startsWith(code)
                    .or(DSL.exists(DSL.selectOne()
                            .from(VARIANT)
                            .where(VARIANT.PRODUCT_ID.eq(PRODUCT.ID))
                            .and(VARIANT.VARIANT_CODE.startsWith(code)))));
        }
        if (query.name() != null && !query.name().isBlank()) {
            condition = condition.and(PRODUCT.NAME.likeIgnoreCase(LikePatterns.contains(query.name()), LikePatterns.ESCAPE));
        }
        if (query.status() != null) {
            condition = condition.and(PRODUCT.STATUS.eq(query.status().name()));
        }
        if (query.categoryId() != null) {
            condition = condition.and(CategoryConditions.productInCategory(query.categoryId()));
        }
        return condition;
    }

    /**
     * レコードから商品の詳細に変換する。
     *
     * <p>バリアントと画像は別に取得する。
     *
     * @param record      商品のレコード
     * @param axes        軸の名前（並び順）
     * @param categoryIds 所属カテゴリ ID（ID の順）
     * @return 商品の詳細（税込価格は空）
     */
    private ProductDetailView toDetail(ProductRecord record, List<String> axes, List<UUID> categoryIds) {
        var id = record.getId();

        var axisValues = ProductParts.axisValues(VARIANT.ID);
        var variants = dsl.select(VARIANT.asterisk(), axisValues)
                .from(VARIANT)
                .where(VARIANT.PRODUCT_ID.eq(id))
                .orderBy(VARIANT.DISPLAY_ORDER, VARIANT.ID)
                .fetch(row -> new ProductDetailView.VariantView(
                        row.get(VARIANT.ID),
                        row.get(VARIANT.VARIANT_CODE),
                        List.of(row.get(axisValues)),
                        row.get(VARIANT.SALES_SUSPENDED),
                        row.get(VARIANT.IMAGE_ID)));

        var imageRecords = dsl.selectFrom(PRODUCT_IMAGE)
                .where(PRODUCT_IMAGE.PRODUCT_ID.eq(id))
                .orderBy(PRODUCT_IMAGE.DISPLAY_ORDER, PRODUCT_IMAGE.ID)
                .fetch();
        var urls = mediaDirectory.urls(imageRecords.map(ProductImageRecord::getAssetId));
        var images = imageRecords.map(image -> new ProductDetailView.ImageView(image.getId(), urls.get(image.getAssetId())));

        return new ProductDetailView(
                id,
                record.getProductCode(),
                record.getName(),
                record.getDescription(),
                record.getTaxCategoryId(),
                Money.of(record.getPriceExclTax()),
                null,
                categoryIds,
                axes,
                RestoreGuard.restore("product", () -> PublicationStatus.valueOf(record.getStatus())),
                record.getFirstShownAt(),
                record.getPublishStart(),
                record.getPublishEnd(),
                images,
                variants,
                // できる操作は現在時刻で決まるので、ここでは決めない
                null,
                record.getVersion(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}
