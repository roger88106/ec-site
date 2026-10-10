package com.ecsite.module.catalog.infrastructure;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.catalog.api.StoreProductDetail;
import com.ecsite.module.catalog.api.StoreProductQuery;
import com.ecsite.module.catalog.api.StoreProductSort;
import com.ecsite.module.catalog.api.StoreProductSummary;
import com.ecsite.module.catalog.application.StoreProductQueryRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static com.ecsite.jooq.catalog.Tables.PRODUCT_AXIS;
import static com.ecsite.jooq.catalog.Tables.VARIANT;
import static com.ecsite.jooq.catalog.Tables.VARIANT_AXIS_VALUE;

/**
 * C 側向けの商品照会のクエリリポジトリ（docs/modules/catalog.md）
 */
@Repository
@RequiredArgsConstructor
class JooqStoreProductQueryRepository implements StoreProductQueryRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * C 側に返す商品画像の URL
     */
    private final StoreImageUrls storeImageUrls;

    /**
     * C 側の一覧の 1 ページ分の商品の組み立て
     */
    private final StoreProductSummaries storeProductSummaries;

    /**
     * C 側の商品一覧を取得する。
     *
     * @param query   絞り込みと並び順
     * @param request ページの指定
     * @param now     表示時刻
     * @param rates   税区分 ID → 率（%）
     * @return 商品の一覧
     */
    @Override
    public PageResult<StoreProductSummary> findVisible(StoreProductQuery query, PageRequest request, Instant now,
                                                       Map<UUID, BigDecimal> rates) {
        // 有効な税率がひとつもなければ、どの商品も税込価格を出せない（表示できない）
        if (rates.isEmpty()) {
            return PageResult.of(List.of(), request, 0);
        }
        var priceInclTax = PriceInclTaxField.of(rates);
        var condition = condition(query, now, priceInclTax);

        var rows = dsl.select(PRODUCT.ID, PRODUCT.NAME, priceInclTax, PRODUCT.PRICE_EXCL_TAX)
                .from(PRODUCT)
                .where(condition)
                .orderBy(orderBy(query.sort(), priceInclTax))
                .limit(request.size())
                .offset(request.offset())
                .fetch();

        var items = storeProductSummaries.of(rows);

        return PageResult.of(items, request, dsl.fetchCount(PRODUCT, condition));
    }

    /**
     * C 側の商品の詳細を取得する。
     *
     * @param id    商品 ID
     * @param now   表示時刻
     * @param rates 税区分 ID → 率（%）
     * @return 商品の詳細
     */
    @Override
    public Optional<StoreProductDetail> findVisibleDetail(UUID id, Instant now, Map<UUID, BigDecimal> rates) {
        if (rates.isEmpty()) {
            return Optional.empty();
        }
        var priceInclTax = PriceInclTaxField.of(rates);
        return dsl.select(PRODUCT.ID, PRODUCT.NAME, PRODUCT.DESCRIPTION, priceInclTax, PRODUCT.PRICE_EXCL_TAX)
                .from(PRODUCT)
                .where(PRODUCT.ID.eq(id))
                .and(ProductVisibility.visibleAt(now))
                .and(priceInclTax.isNotNull())
                .fetchOptional()
                .map(row -> {
                    var images = storeImageUrls.ofProduct(id);
                    return new StoreProductDetail(row.value1(), row.value2(), row.value3(), row.value4(), row.value5(),
                            axes(id), variants(id, images), List.copyOf(images.values()));
                });
    }

// ============================== プライベートメソッド ==============================

    /**
     * 一覧の条件：表示される商品（BR-2）で、税込価格を出せること。カテゴリ・価格の範囲で絞り込む。
     */
    private static Condition condition(StoreProductQuery query, Instant now, Field<Long> priceInclTax) {
        var condition = ProductVisibility.visibleAt(now).and(priceInclTax.isNotNull());
        if (query.categoryId() != null) {
            condition = condition.and(CategoryConditions.productInCategory(query.categoryId()));
        }
        if (query.minPrice() != null) {
            condition = condition.and(priceInclTax.ge(query.minPrice()));
        }
        if (query.maxPrice() != null) {
            condition = condition.and(priceInclTax.le(query.maxPrice()));
        }
        return condition;
    }

    /**
     * 並び順（AC-54）。同じ値なら ID の順にし、ページをまたいでも重複・欠落しないようにする（constitution 9）。
     */
    private static List<OrderField<?>> orderBy(StoreProductSort sort, Field<Long> priceInclTax) {
        return switch (sort == null ? StoreProductSort.NEWEST : sort) {
            case NEWEST -> List.of(PRODUCT.FIRST_SHOWN_AT.desc(), PRODUCT.ID);
            case PRICE_ASC -> List.of(priceInclTax.asc(), PRODUCT.ID);
            case PRICE_DESC -> List.of(priceInclTax.desc(), PRODUCT.ID);
        };
    }

    /**
     * バリアント軸の名前を並び順で取得する。
     */
    private List<String> axes(UUID productId) {
        return dsl.select(PRODUCT_AXIS.NAME)
                .from(PRODUCT_AXIS)
                .where(PRODUCT_AXIS.PRODUCT_ID.eq(productId))
                .orderBy(PRODUCT_AXIS.POSITION)
                .fetch(PRODUCT_AXIS.NAME);
    }

    /**
     * すべてのバリアント（販売停止中を含む）を表示順で取得する。バリアントの画像は、指定した画像の URL（なければ null）。
     */
    private List<StoreProductDetail.Variant> variants(UUID productId, Map<UUID, String> imageUrls) {
        Map<UUID, List<String>> axisValuesByVariant = dsl.select(VARIANT_AXIS_VALUE.VARIANT_ID, VARIANT_AXIS_VALUE.VALUE)
                .from(VARIANT_AXIS_VALUE)
                .join(VARIANT).on(VARIANT.ID.eq(VARIANT_AXIS_VALUE.VARIANT_ID))
                .where(VARIANT.PRODUCT_ID.eq(productId))
                .orderBy(VARIANT_AXIS_VALUE.VARIANT_ID, VARIANT_AXIS_VALUE.POSITION)
                .fetchGroups(VARIANT_AXIS_VALUE.VARIANT_ID, VARIANT_AXIS_VALUE.VALUE);
        return dsl.selectFrom(VARIANT)
                .where(VARIANT.PRODUCT_ID.eq(productId))
                .orderBy(VARIANT.DISPLAY_ORDER)
                .fetch(variant -> new StoreProductDetail.Variant(
                        variant.getId(),
                        axisValuesByVariant.getOrDefault(variant.getId(), List.of()),
                        variant.getSalesSuspended(),
                        variant.getImageId() == null ? null : imageUrls.get(variant.getImageId())));
    }
}
