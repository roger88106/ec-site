package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.catalog.application.model.view.OrderableVariantView;
import com.ecsite.module.catalog.application.model.view.OrderableVariantView.AxisValue;
import com.ecsite.module.catalog.application.port.OrderableVariantQueryRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static com.ecsite.jooq.catalog.Tables.PRODUCT_AXIS;
import static com.ecsite.jooq.catalog.Tables.VARIANT;
import static com.ecsite.jooq.catalog.Tables.VARIANT_AXIS_VALUE;

/**
 * 注文に使うバリアントの情報のクエリリポジトリ
 */
@Repository
@RequiredArgsConstructor
class JooqOrderableVariantQueryRepository implements OrderableVariantQueryRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * C 側に返す商品画像の URL
     */
    private final StoreImageUrls storeImageUrls;

    /**
     * 複数のバリアントについて、注文に使う情報を取得する。
     *
     * @param variantIds バリアント ID
     * @param now        表示されるかを判定する時刻
     * @return バリアント ID → 情報
     */
    @Override
    public Map<UUID, OrderableVariantView> findVariants(Collection<UUID> variantIds, Instant now) {
        // 表示条件は、一覧・検索と同じ部品で判定する
        var visible = DSL.field(ProductVisibility.visibleAt(now));
        var rows = dsl.select(VARIANT.ID, VARIANT.PRODUCT_ID, PRODUCT.PRODUCT_CODE, VARIANT.VARIANT_CODE, PRODUCT.NAME,
                        PRODUCT.PRICE_EXCL_TAX, PRODUCT.TAX_CATEGORY_ID, VARIANT.IMAGE_ID, visible, VARIANT.SALES_SUSPENDED)
                .from(VARIANT)
                .join(PRODUCT).on(PRODUCT.ID.eq(VARIANT.PRODUCT_ID))
                .where(VARIANT.ID.in(variantIds))
                .fetch();
        var axisValues = axisValues(variantIds);
        // バリアントに画像を指定しているものだけ、画像の URL をまとめて引く
        var imageUrls = storeImageUrls.ofImages(
                rows.stream().map(row -> row.get(VARIANT.IMAGE_ID)).filter(Objects::nonNull).toList());
        var representatives = storeImageUrls.representatives(new HashSet<>(rows.map(row -> row.get(VARIANT.PRODUCT_ID))));

        var result = new LinkedHashMap<UUID, OrderableVariantView>();
        rows.forEach(row -> {
            var variantId = row.get(VARIANT.ID);
            var productId = row.get(VARIANT.PRODUCT_ID);
            var imageId = row.get(VARIANT.IMAGE_ID);
            // バリアントの画像、なければ代表画像
            var imageUrl = imageId != null && imageUrls.containsKey(imageId)
                    ? imageUrls.get(imageId)
                    : representatives.get(productId);
            result.put(variantId, new OrderableVariantView(variantId, productId, row.get(PRODUCT.PRODUCT_CODE),
                    row.get(VARIANT.VARIANT_CODE), row.get(PRODUCT.NAME), axisValues.getOrDefault(variantId, List.of()),
                    Money.of(row.get(PRODUCT.PRICE_EXCL_TAX)), row.get(PRODUCT.TAX_CATEGORY_ID), imageUrl,
                    Boolean.TRUE.equals(row.get(visible)), row.get(VARIANT.SALES_SUSPENDED)));
        });
        return result;
    }

    /**
     * バリアントごとの軸の名前と値を、商品の軸の並びで取得する。
     *
     * @return バリアント ID → 軸の名前と値。軸のない商品のバリアントは含まない
     */
    private Map<UUID, List<AxisValue>> axisValues(Collection<UUID> variantIds) {
        return dsl.select(VARIANT_AXIS_VALUE.VARIANT_ID, PRODUCT_AXIS.NAME, VARIANT_AXIS_VALUE.VALUE)
                .from(VARIANT_AXIS_VALUE)
                .join(VARIANT).on(VARIANT.ID.eq(VARIANT_AXIS_VALUE.VARIANT_ID))
                // 軸の値は、商品の軸と並び（位置）で対応する
                .join(PRODUCT_AXIS).on(PRODUCT_AXIS.PRODUCT_ID.eq(VARIANT.PRODUCT_ID)
                        .and(PRODUCT_AXIS.POSITION.eq(VARIANT_AXIS_VALUE.POSITION)))
                .where(VARIANT_AXIS_VALUE.VARIANT_ID.in(variantIds))
                .orderBy(VARIANT_AXIS_VALUE.VARIANT_ID, VARIANT_AXIS_VALUE.POSITION)
                .fetchGroups(VARIANT_AXIS_VALUE.VARIANT_ID, row -> new AxisValue(row.value2(), row.value3()));
    }
}
