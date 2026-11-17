package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.common.model.value.Money;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.catalog.application.model.query.StoreProductListQuery;
import com.ecsite.module.catalog.application.model.view.StoreProductDetailView;
import com.ecsite.module.catalog.application.model.view.StoreProductSummaryView;
import com.ecsite.module.catalog.application.port.StoreProductQueryRepository;
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
import static com.ecsite.jooq.catalog.Tables.VARIANT;

/**
 * C 側の商品照会のクエリリポジトリ
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
    public PageResult<StoreProductSummaryView> findVisible(StoreProductListQuery query, PageRequest request, Instant now,
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

        var items = storeProductSummaries.of(rows, priceInclTax);

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
    public Optional<StoreProductDetailView> findVisibleDetail(UUID id, Instant now, Map<UUID, BigDecimal> rates) {
        if (rates.isEmpty()) {
            return Optional.empty();
        }
        var priceInclTax = PriceInclTaxField.of(rates);
        var axes = ProductParts.axes(PRODUCT.ID);
        var found = dsl.select(PRODUCT.ID, PRODUCT.NAME, PRODUCT.DESCRIPTION, priceInclTax, PRODUCT.PRICE_EXCL_TAX, axes)
                .from(PRODUCT)
                .where(PRODUCT.ID.eq(id))
                .and(ProductVisibility.visibleAt(now))
                .and(priceInclTax.isNotNull())
                .fetchOptional();
        return found.map(row -> {
            var images = storeImageUrls.ofProduct(id);
            return new StoreProductDetailView(row.get(PRODUCT.ID), row.get(PRODUCT.NAME), row.get(PRODUCT.DESCRIPTION),
                    Money.of(row.get(priceInclTax)), Money.of(row.get(PRODUCT.PRICE_EXCL_TAX)),
                    List.of(row.get(axes)), variants(id, images), List.copyOf(images.values()));
        });
    }

// ============================== プライベートメソッド ==============================

    /**
     * 一覧の条件：表示される商品で、税込価格を出せること。
     *
     * <p>カテゴリ・価格の範囲で絞り込む。
     */
    private static Condition condition(StoreProductListQuery query, Instant now, Field<Long> priceInclTax) {
        var condition = ProductVisibility.visibleAt(now).and(priceInclTax.isNotNull());
        if (query.categoryId() != null) {
            condition = condition.and(CategoryConditions.productInCategory(query.categoryId()));
        }
        if (query.minPrice() != null) {
            condition = condition.and(priceInclTax.ge(query.minPrice().yen()));
        }
        if (query.maxPrice() != null) {
            condition = condition.and(priceInclTax.le(query.maxPrice().yen()));
        }
        return condition;
    }

    /**
     * 並び順を返す。
     *
     * <p>同じ値なら ID の順。
     */
    private static List<OrderField<?>> orderBy(StoreProductListQuery.Sort sort, Field<Long> priceInclTax) {
        // 同じ値を ID の順にして、ページをまたいでも重複・欠落しないようにする（API の規約）
        return switch (sort) {
            case NEWEST -> List.of(PRODUCT.FIRST_SHOWN_AT.desc(), PRODUCT.ID);
            case PRICE_ASC -> List.of(priceInclTax.asc(), PRODUCT.ID);
            case PRICE_DESC -> List.of(priceInclTax.desc(), PRODUCT.ID);
        };
    }

    /**
     * 全てのバリアント（販売停止中を含む）を表示順で取得する。
     *
     * <p>バリアントの画像は、指定した画像の URL（なければ null）。
     */
    private List<StoreProductDetailView.Variant> variants(UUID productId, Map<UUID, String> imageUrls) {
        var axisValues = ProductParts.axisValues(VARIANT.ID);
        return dsl.select(VARIANT.ID, VARIANT.SALES_SUSPENDED, VARIANT.IMAGE_ID, axisValues)
                .from(VARIANT)
                .where(VARIANT.PRODUCT_ID.eq(productId))
                .orderBy(VARIANT.DISPLAY_ORDER, VARIANT.ID)
                .fetch(row -> {
                    var imageId = row.get(VARIANT.IMAGE_ID);
                    return new StoreProductDetailView.Variant(
                            row.get(VARIANT.ID),
                            List.of(row.get(axisValues)),
                            row.get(VARIANT.SALES_SUSPENDED),
                            imageId == null ? null : imageUrls.get(imageId));
                });
    }
}
