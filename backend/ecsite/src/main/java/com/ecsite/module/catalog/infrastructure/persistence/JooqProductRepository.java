package com.ecsite.module.catalog.infrastructure;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.persistence.ConstraintViolationTranslator;
import com.ecsite.jooq.catalog.Keys;
import com.ecsite.jooq.catalog.tables.records.ProductRecord;
import com.ecsite.module.catalog.domain.product.Product;
import com.ecsite.module.catalog.domain.product.ProductErrorCode;
import com.ecsite.module.catalog.domain.product.ProductFactory;
import com.ecsite.module.catalog.domain.product.ProductImage;
import com.ecsite.module.catalog.domain.product.PublicationStatus;
import com.ecsite.module.catalog.domain.product.Variant;
import com.ecsite.module.catalog.domain.product.port.ProductRepository;
import com.ecsite.module.catalog.domain.product.vo.PriceExclTax;
import com.ecsite.module.catalog.domain.product.vo.ProductCode;
import com.ecsite.module.catalog.domain.product.vo.PublicationPeriod;
import com.ecsite.module.catalog.domain.product.vo.VariantCode;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
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
 * 商品のリポジトリ。<br />
 * 商品の一部（軸、所属カテゴリ、バリアント、軸の値）は、別のテーブルに保存する。
 */
@Repository
@RequiredArgsConstructor
class JooqProductRepository implements ProductRepository {

    /**
     * 制約名 → 業務のエラー。所属カテゴリの外部キーの違反は、存在しないカテゴリか、同時に削除されたカテゴリ（AC-12）
     */
    private static final ConstraintViolationTranslator CONSTRAINTS = ConstraintViolationTranslator.of(Map.of(
            Keys.PRODUCT_PRODUCT_CODE_KEY.getName(), () -> BusinessException.of(ProductErrorCode.PRODUCT_CODE_ALREADY_EXISTS),
            Keys.VARIANT_VARIANT_CODE_KEY.getName(), () -> BusinessException.of(ProductErrorCode.VARIANT_CODE_ALREADY_EXISTS),
            Keys.VARIANT_PRODUCT_ID_AXIS_KEY_KEY.getName(), () -> BusinessException.of(ProductErrorCode.VARIANT_AXIS_VALUES_ALREADY_EXISTS),
            Keys.PRODUCT_CATEGORY__PRODUCT_CATEGORY_CATEGORY_ID_FKEY.getName(), () -> BusinessException.of(ProductErrorCode.INVALID_CATEGORY_ID)));

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * Spring イベントパブリッシャー
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 商品ファクトリ
     */
    private final ProductFactory factory;

    /**
     * 時計（登録・更新日時）
     */
    private final Clock clock;

    /**
     * 商品を取得する。
     *
     * @param id 商品 ID
     * @return 商品
     */
    @Override
    public Optional<Product> findById(UUID id) {
        return dsl.selectFrom(PRODUCT)
                .where(PRODUCT.ID.eq(id))
                .fetchOptional()
                .map(this::toProduct);
    }

    /**
     * 商品を登録する。
     *
     * @param product 商品
     */
    @Override
    public void add(Product product) {
        var snapshot = product.toSnapshot();
        var now = clock.instant();

        try {
            // 商品の登録
            dsl.insertInto(PRODUCT)
                    .set(PRODUCT.ID, snapshot.id())
                    .set(PRODUCT.PRODUCT_CODE, snapshot.productCode().value())
                    .set(PRODUCT.NAME, snapshot.name())
                    .set(PRODUCT.DESCRIPTION, snapshot.description())
                    .set(PRODUCT.TAX_CATEGORY_ID, snapshot.taxCategoryId())
                    .set(PRODUCT.PRICE_EXCL_TAX, snapshot.priceExclTax().yen())
                    .set(PRODUCT.STATUS, snapshot.status().name())
                    .set(PRODUCT.FIRST_SHOWN_AT, snapshot.firstShownAt())
                    .set(PRODUCT.PUBLISH_START, snapshot.publicationPeriod().publishStart())
                    .set(PRODUCT.PUBLISH_END, snapshot.publicationPeriod().publishEnd())
                    .set(PRODUCT.VERSION, snapshot.version())
                    .set(PRODUCT.CREATED_AT, now)
                    .set(PRODUCT.UPDATED_AT, now)
                    .execute();

            // 軸の登録（登録後は変わらない）
            for (int i = 0; i < snapshot.axes().size(); i++) {
                dsl.insertInto(PRODUCT_AXIS)
                        .set(PRODUCT_AXIS.PRODUCT_ID, snapshot.id())
                        .set(PRODUCT_AXIS.POSITION, (short) (i + 1))
                        .set(PRODUCT_AXIS.NAME, snapshot.axes().get(i))
                        .execute();
            }

            // 所属カテゴリ、画像、バリアントの登録 ※バリアントが画像を指すので、画像を先に保存する
            saveCategories(snapshot);
            saveImages(snapshot);
            saveVariants(snapshot);
        } catch (DataAccessException | org.jooq.exception.DataAccessException e) {
            throw CONSTRAINTS.translate(e);
        }

        // イベント発行
        product.pullEvents().forEach(eventPublisher::publishEvent);
    }

    /**
     * 商品を更新する。所属カテゴリ、画像、バリアントは差分で更新する。
     *
     * @param product 商品
     */
    @Override
    public void save(Product product) {
        var snapshot = product.toSnapshot();

        try {
            // 商品の更新 ※バージョンが一致しなければ、取得後に他で更新されている。
            // 商品の行を先にロックするので、同じ商品の一部の書き換えは直列になる
            int affected = dsl.update(PRODUCT)
                    .set(PRODUCT.NAME, snapshot.name())
                    .set(PRODUCT.DESCRIPTION, snapshot.description())
                    .set(PRODUCT.TAX_CATEGORY_ID, snapshot.taxCategoryId())
                    .set(PRODUCT.PRICE_EXCL_TAX, snapshot.priceExclTax().yen())
                    .set(PRODUCT.STATUS, snapshot.status().name())
                    .set(PRODUCT.FIRST_SHOWN_AT, snapshot.firstShownAt())
                    .set(PRODUCT.PUBLISH_START, snapshot.publicationPeriod().publishStart())
                    .set(PRODUCT.PUBLISH_END, snapshot.publicationPeriod().publishEnd())
                    .set(PRODUCT.VERSION, snapshot.version() + 1)
                    .set(PRODUCT.UPDATED_AT, clock.instant())
                    .where(PRODUCT.ID.eq(snapshot.id()))
                    .and(PRODUCT.VERSION.eq(snapshot.version()))
                    .execute();
            if (affected == 0) {
                throw BusinessException.of(CommonErrorCode.CONCURRENT_MODIFICATION);
            }

            saveCategories(snapshot);
            saveImages(snapshot);
            saveVariants(snapshot);
        } catch (DataAccessException | org.jooq.exception.DataAccessException e) {
            throw CONSTRAINTS.translate(e);
        }

        // イベント発行
        product.pullEvents().forEach(eventPublisher::publishEvent);
    }

    /**
     * 商品を削除する。商品の一部（画像を含む）は外部キーの ON DELETE CASCADE で消える。<br />
     * 画像のアセットを手放すのは、アプリケーションサービス（メディアモジュールの呼び出し）。
     *
     * @param product 商品
     */
    @Override
    public void delete(Product product) {
        var snapshot = product.toSnapshot();

        int affected = dsl.deleteFrom(PRODUCT)
                .where(PRODUCT.ID.eq(snapshot.id()))
                .and(PRODUCT.VERSION.eq(snapshot.version()))
                .execute();
        if (affected == 0) {
            throw BusinessException.of(CommonErrorCode.CONCURRENT_MODIFICATION);
        }

        // イベント発行
        product.pullEvents().forEach(eventPublisher::publishEvent);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 所属カテゴリを差分で保存する：なくなったものを削除し、新しいものを追加する。
     *
     * @param snapshot 商品のスナップショット
     */
    private void saveCategories(Product.Snapshot snapshot) {
        dsl.deleteFrom(PRODUCT_CATEGORY)
                .where(PRODUCT_CATEGORY.PRODUCT_ID.eq(snapshot.id()))
                .and(PRODUCT_CATEGORY.CATEGORY_ID.notIn(snapshot.categoryIds()))
                .execute();
        for (var categoryId : snapshot.categoryIds()) {
            dsl.insertInto(PRODUCT_CATEGORY)
                    .set(PRODUCT_CATEGORY.PRODUCT_ID, snapshot.id())
                    .set(PRODUCT_CATEGORY.CATEGORY_ID, categoryId)
                    .onConflictDoNothing()
                    .execute();
        }
    }

    /**
     * 画像を差分で保存する：なくなったものを削除し、新しいものを追加し、残るものの表示順を更新する。<br />
     * アセット ID は登録後に変わらないので、更新しない。削除した画像を指すバリアントの指定は、外部キーで外れる。
     * 削除した画像のアセットを手放すのは、アプリケーションサービス（メディアモジュールの呼び出し）。
     *
     * @param snapshot 商品のスナップショット
     */
    private void saveImages(Product.Snapshot snapshot) {
        var imageIds = snapshot.images().stream().map(ProductImage::id).toList();

        dsl.deleteFrom(PRODUCT_IMAGE)
                .where(PRODUCT_IMAGE.PRODUCT_ID.eq(snapshot.id()))
                .and(PRODUCT_IMAGE.ID.notIn(imageIds))
                .execute();

        var existingIds = new HashSet<>(dsl.select(PRODUCT_IMAGE.ID)
                .from(PRODUCT_IMAGE)
                .where(PRODUCT_IMAGE.PRODUCT_ID.eq(snapshot.id()))
                .fetch(PRODUCT_IMAGE.ID));

        for (var image : snapshot.images()) {
            if (existingIds.contains(image.id())) {
                dsl.update(PRODUCT_IMAGE)
                        .set(PRODUCT_IMAGE.DISPLAY_ORDER, image.displayOrder())
                        .where(PRODUCT_IMAGE.ID.eq(image.id()))
                        .execute();
            } else {
                dsl.insertInto(PRODUCT_IMAGE)
                        .set(PRODUCT_IMAGE.ID, image.id())
                        .set(PRODUCT_IMAGE.PRODUCT_ID, snapshot.id())
                        .set(PRODUCT_IMAGE.ASSET_ID, image.assetId())
                        .set(PRODUCT_IMAGE.DISPLAY_ORDER, image.displayOrder())
                        .set(PRODUCT_IMAGE.CREATED_AT, clock.instant())
                        .execute();
            }
        }
    }

    /**
     * バリアントを差分で保存する：なくなったものを削除し、新しいものを追加し、残るものの表示順、販売停止、画像を更新する。<br />
     * 品番と軸の値は登録後に変わらないので、更新しない。
     *
     * @param snapshot 商品のスナップショット
     */
    private void saveVariants(Product.Snapshot snapshot) {
        var variantIds = snapshot.variants().stream().map(Variant::id).toList();

        // なくなったバリアントの削除 ※軸の値は ON DELETE CASCADE で消える。品番を新しいバリアントで使えるよう、追加より先に行う
        dsl.deleteFrom(VARIANT)
                .where(VARIANT.PRODUCT_ID.eq(snapshot.id()))
                .and(VARIANT.ID.notIn(variantIds))
                .execute();

        var existingIds = new HashSet<>(dsl.select(VARIANT.ID)
                .from(VARIANT)
                .where(VARIANT.PRODUCT_ID.eq(snapshot.id()))
                .fetch(VARIANT.ID));

        for (var variant : snapshot.variants()) {
            if (existingIds.contains(variant.id())) {
                dsl.update(VARIANT)
                        .set(VARIANT.DISPLAY_ORDER, variant.displayOrder())
                        .set(VARIANT.SALES_SUSPENDED, variant.salesSuspended())
                        .set(VARIANT.IMAGE_ID, variant.imageId())
                        .where(VARIANT.ID.eq(variant.id()))
                        .execute();
            } else {
                insertVariant(snapshot.id(), variant);
            }
        }
    }

    /**
     * バリアントと軸の値を追加する。
     *
     * @param productId 商品 ID
     * @param variant   バリアント
     */
    private void insertVariant(UUID productId, Variant variant) {
        dsl.insertInto(VARIANT)
                .set(VARIANT.ID, variant.id())
                .set(VARIANT.PRODUCT_ID, productId)
                .set(VARIANT.VARIANT_CODE, variant.variantCode().value())
                .set(VARIANT.AXIS_KEY, variant.axisKey())
                .set(VARIANT.DISPLAY_ORDER, variant.displayOrder())
                .set(VARIANT.SALES_SUSPENDED, variant.salesSuspended())
                .set(VARIANT.IMAGE_ID, variant.imageId())
                .execute();
        for (int i = 0; i < variant.axisValues().size(); i++) {
            dsl.insertInto(VARIANT_AXIS_VALUE)
                    .set(VARIANT_AXIS_VALUE.VARIANT_ID, variant.id())
                    .set(VARIANT_AXIS_VALUE.POSITION, (short) (i + 1))
                    .set(VARIANT_AXIS_VALUE.VALUE, variant.axisValues().get(i))
                    .execute();
        }
    }

    /**
     * レコードから商品に変換する。商品の一部は別に取得する。
     *
     * @param record 商品のレコード
     * @return 商品
     */
    private Product toProduct(ProductRecord record) {
        var id = record.getId();

        var axes = dsl.select(PRODUCT_AXIS.NAME)
                .from(PRODUCT_AXIS)
                .where(PRODUCT_AXIS.PRODUCT_ID.eq(id))
                .orderBy(PRODUCT_AXIS.POSITION)
                .fetch(PRODUCT_AXIS.NAME);

        var categoryIds = dsl.select(PRODUCT_CATEGORY.CATEGORY_ID)
                .from(PRODUCT_CATEGORY)
                .where(PRODUCT_CATEGORY.PRODUCT_ID.eq(id))
                .fetchSet(PRODUCT_CATEGORY.CATEGORY_ID);

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
                .fetch(variant -> new Variant(
                        variant.getId(),
                        VariantCode.of(variant.getVariantCode()),
                        axisValuesByVariant.getOrDefault(variant.getId(), List.of()),
                        variant.getDisplayOrder(),
                        variant.getSalesSuspended(),
                        variant.getImageId()));

        var images = dsl.selectFrom(PRODUCT_IMAGE)
                .where(PRODUCT_IMAGE.PRODUCT_ID.eq(id))
                .orderBy(PRODUCT_IMAGE.DISPLAY_ORDER)
                .fetch(image -> new ProductImage(image.getId(), image.getAssetId(), image.getDisplayOrder()));

        return factory.restore(new Product.Snapshot(
                id,
                ProductCode.of(record.getProductCode()),
                record.getName(),
                record.getDescription(),
                record.getTaxCategoryId(),
                PriceExclTax.of(record.getPriceExclTax()),
                categoryIds,
                axes,
                PublicationStatus.valueOf(record.getStatus()),
                record.getFirstShownAt(),
                PublicationPeriod.of(record.getPublishStart(), record.getPublishEnd()),
                images,
                variants,
                record.getVersion()));
    }
}
