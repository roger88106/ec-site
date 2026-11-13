package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.base.DomainEvents;
import com.ecsite.common.model.base.RestoreGuard;
import com.ecsite.common.persistence.ConstraintViolationTranslator;
import com.ecsite.jooq.catalog.Keys;
import com.ecsite.jooq.catalog.tables.records.ProductRecord;
import com.ecsite.module.catalog.domain.ProductErrorCode;
import com.ecsite.module.catalog.domain.product.Product;
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
import org.jooq.impl.DSL;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static com.ecsite.jooq.catalog.Tables.PRODUCT_AXIS;
import static com.ecsite.jooq.catalog.Tables.PRODUCT_CATEGORY;
import static com.ecsite.jooq.catalog.Tables.PRODUCT_IMAGE;
import static com.ecsite.jooq.catalog.Tables.VARIANT;
import static com.ecsite.jooq.catalog.Tables.VARIANT_AXIS_VALUE;

/**
 * 商品のリポジトリ
 *
 * <p>商品の一部（軸、所属カテゴリ、バリアント、軸の値）は、別のテーブルに保存する。
 */
@Repository
@RequiredArgsConstructor
class JooqProductRepository implements ProductRepository {

    /**
     * 制約名 → 業務のエラー
     *
     * <p>所属カテゴリの外部キーの違反は、存在しないカテゴリか、同時に削除されたカテゴリ。
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
        var axes = ProductParts.axes(PRODUCT.ID);
        var categoryIds = ProductParts.categoryIds(PRODUCT.ID);
        var found = dsl.select(PRODUCT.asterisk(), axes, categoryIds)
                .from(PRODUCT)
                .where(PRODUCT.ID.eq(id))
                .fetchOptional();
        return found.map(row -> toProduct(row.into(PRODUCT), List.of(row.get(axes)), Set.of(row.get(categoryIds))));
    }

    /**
     * 商品を登録する。
     *
     * @param product 商品
     * @throws BusinessException 商品品番が重複している（{@code PRODUCT_CODE_ALREADY_EXISTS}）
     * @throws BusinessException バリアント品番が重複している（{@code VARIANT_CODE_ALREADY_EXISTS}）
     * @throws BusinessException 軸の値の組み合わせが重複している（{@code VARIANT_AXIS_VALUES_ALREADY_EXISTS}）
     * @throws BusinessException 所属カテゴリが存在しない（{@code INVALID_CATEGORY_ID}）
     */
    @Override
    public void add(Product product) {
        var snapshot = product.toSnapshot();
        var now = clock.instant();

        CONSTRAINTS.run(() -> {
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
            insertAxes(snapshot);

            // 所属カテゴリ、画像、バリアントの登録 ※新しい商品には削除するものがないので、削除は実行しない
            writeParts(snapshot);
        });

        // イベント発行
        DomainEvents.publish(eventPublisher, product);
    }

    /**
     * 商品を更新する。
     *
     * <p>所属カテゴリ、画像、バリアントは差分で更新する。
     *
     * @param product 商品
     * @throws BusinessException 取得した後に他で更新された（{@code CONCURRENT_MODIFICATION}）
     * @throws BusinessException 商品品番が重複している（{@code PRODUCT_CODE_ALREADY_EXISTS}）
     * @throws BusinessException バリアント品番が重複している（{@code VARIANT_CODE_ALREADY_EXISTS}）
     * @throws BusinessException 軸の値の組み合わせが重複している（{@code VARIANT_AXIS_VALUES_ALREADY_EXISTS}）
     * @throws BusinessException 所属カテゴリが存在しない（{@code INVALID_CATEGORY_ID}）
     */
    @Override
    public void save(Product product) {
        var snapshot = product.toSnapshot();

        CONSTRAINTS.run(() -> {
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

            // 所属カテゴリ、画像、バリアントの保存
            deleteRemovedParts(snapshot);
            writeParts(snapshot);
        });

        // イベント発行
        DomainEvents.publish(eventPublisher, product);
    }

    /**
     * 商品を削除する。
     *
     * <p>商品の一部（画像を含む）は外部キーの ON DELETE CASCADE で消える。画像のアセットは手放さない。
     *
     * @param product 商品
     * @throws BusinessException 取得した後に他で更新された（{@code CONCURRENT_MODIFICATION}）
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
        DomainEvents.publish(eventPublisher, product);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 軸を追加する。
     *
     * @param snapshot 商品のスナップショット
     */
    private void insertAxes(Product.Snapshot snapshot) {
        if (snapshot.axes().isEmpty()) {
            return;
        }
        var insert = dsl.insertInto(PRODUCT_AXIS, PRODUCT_AXIS.PRODUCT_ID, PRODUCT_AXIS.POSITION, PRODUCT_AXIS.NAME);
        short position = 1;
        for (var axis : snapshot.axes()) {
            insert = insert.values(snapshot.id(), position, axis);
            position++;
        }
        insert.execute();
    }

    /**
     * なくなった所属カテゴリ、画像、バリアントを削除する。
     *
     * <p>削除した画像のアセットは手放さない。
     *
     * @param snapshot 商品のスナップショット
     */
    private void deleteRemovedParts(Product.Snapshot snapshot) {
        dsl.deleteFrom(PRODUCT_CATEGORY)
                .where(PRODUCT_CATEGORY.PRODUCT_ID.eq(snapshot.id()))
                .and(PRODUCT_CATEGORY.CATEGORY_ID.notIn(snapshot.categoryIds()))
                .execute();

        // 削除した画像を指すバリアントの指定は、外部キーで外れる
        dsl.deleteFrom(PRODUCT_IMAGE)
                .where(PRODUCT_IMAGE.PRODUCT_ID.eq(snapshot.id()))
                .and(PRODUCT_IMAGE.ID.notIn(snapshot.images().stream().map(ProductImage::id).toList()))
                .execute();

        // 軸の値は ON DELETE CASCADE で消える。品番を新しいバリアントで使えるよう、追加より先に行う
        dsl.deleteFrom(VARIANT)
                .where(VARIANT.PRODUCT_ID.eq(snapshot.id()))
                .and(VARIANT.ID.notIn(snapshot.variants().stream().map(Variant::id).toList()))
                .execute();
    }

    /**
     * 所属カテゴリ、画像、バリアントを、それぞれ 1 つの文で追加または更新する。
     *
     * <p>バリアントが画像を指すので、画像を先に保存する。
     *
     * @param snapshot 商品のスナップショット
     */
    private void writeParts(Product.Snapshot snapshot) {
        insertCategories(snapshot);
        upsertImages(snapshot);
        upsertVariants(snapshot);
        insertAxisValues(snapshot);
    }

    /**
     * 所属カテゴリを追加する。
     *
     * <p>すでにあるものは変えない。
     *
     * @param snapshot 商品のスナップショット
     */
    private void insertCategories(Product.Snapshot snapshot) {
        if (snapshot.categoryIds().isEmpty()) {
            return;
        }
        var insert = dsl.insertInto(PRODUCT_CATEGORY, PRODUCT_CATEGORY.PRODUCT_ID, PRODUCT_CATEGORY.CATEGORY_ID);
        for (var categoryId : snapshot.categoryIds()) {
            insert = insert.values(snapshot.id(), categoryId);
        }
        insert.onConflictDoNothing().execute();
    }

    /**
     * 画像を追加する。
     *
     * <p>すでにあるものは表示順を更新する。アセット ID と登録日時は、登録の後に変わらないので更新しない。
     *
     * @param snapshot 商品のスナップショット
     */
    private void upsertImages(Product.Snapshot snapshot) {
        if (snapshot.images().isEmpty()) {
            return;
        }
        var now = clock.instant();
        var insert = dsl.insertInto(PRODUCT_IMAGE, PRODUCT_IMAGE.ID, PRODUCT_IMAGE.PRODUCT_ID, PRODUCT_IMAGE.ASSET_ID,
                PRODUCT_IMAGE.DISPLAY_ORDER, PRODUCT_IMAGE.CREATED_AT);
        for (var image : snapshot.images()) {
            insert = insert.values(image.id(), snapshot.id(), image.assetId(), image.displayOrder(), now);
        }
        insert.onConflict(PRODUCT_IMAGE.ID)
                .doUpdate()
                .set(PRODUCT_IMAGE.DISPLAY_ORDER, DSL.excluded(PRODUCT_IMAGE.DISPLAY_ORDER))
                .where(PRODUCT_IMAGE.PRODUCT_ID.eq(snapshot.id()))
                .execute();
    }

    /**
     * バリアントを追加する。
     *
     * <p>すでにあるものは表示順、販売停止、画像を更新する。品番と軸の値は、登録の後に変わらないので更新しない。
     *
     * @param snapshot 商品のスナップショット
     */
    private void upsertVariants(Product.Snapshot snapshot) {
        if (snapshot.variants().isEmpty()) {
            return;
        }
        var insert = dsl.insertInto(VARIANT, VARIANT.ID, VARIANT.PRODUCT_ID, VARIANT.VARIANT_CODE, VARIANT.AXIS_KEY,
                VARIANT.DISPLAY_ORDER, VARIANT.SALES_SUSPENDED, VARIANT.IMAGE_ID);
        for (var variant : snapshot.variants()) {
            insert = insert.values(variant.id(), snapshot.id(), variant.variantCode().value(), variant.axisKey(),
                    variant.displayOrder(), variant.salesSuspended(), variant.imageId());
        }
        insert.onConflict(VARIANT.ID)
                .doUpdate()
                .set(VARIANT.DISPLAY_ORDER, DSL.excluded(VARIANT.DISPLAY_ORDER))
                .set(VARIANT.SALES_SUSPENDED, DSL.excluded(VARIANT.SALES_SUSPENDED))
                .set(VARIANT.IMAGE_ID, DSL.excluded(VARIANT.IMAGE_ID))
                .where(VARIANT.PRODUCT_ID.eq(snapshot.id()))
                .execute();
    }

    /**
     * バリアントの軸の値を追加する。
     *
     * <p>すでにあるものは変えない（登録の後に変わらない）。
     *
     * @param snapshot 商品のスナップショット
     */
    private void insertAxisValues(Product.Snapshot snapshot) {
        var insert = dsl.insertInto(VARIANT_AXIS_VALUE, VARIANT_AXIS_VALUE.VARIANT_ID, VARIANT_AXIS_VALUE.POSITION,
                VARIANT_AXIS_VALUE.VALUE);
        boolean empty = true;
        for (var variant : snapshot.variants()) {
            short position = 1;
            for (var value : variant.axisValues()) {
                insert = insert.values(variant.id(), position, value);
                position++;
                empty = false;
            }
        }
        if (!empty) {
            insert.onConflictDoNothing().execute();
        }
    }

    /**
     * レコードから商品に変換する。
     *
     * <p>バリアントと画像は別に取得する。
     *
     * @param record      商品のレコード
     * @param axes        軸の名前（並び順）
     * @param categoryIds 所属カテゴリ ID
     * @return 商品
     */
    private Product toProduct(ProductRecord record, List<String> axes, Set<UUID> categoryIds) {
        var id = record.getId();

        var axisValues = ProductParts.axisValues(VARIANT.ID);
        var variants = dsl.select(VARIANT.asterisk(), axisValues)
                .from(VARIANT)
                .where(VARIANT.PRODUCT_ID.eq(id))
                .orderBy(VARIANT.DISPLAY_ORDER, VARIANT.ID)
                .fetch(row -> new Variant(
                        row.get(VARIANT.ID),
                        VariantCode.of(row.get(VARIANT.VARIANT_CODE)),
                        List.of(row.get(axisValues)),
                        row.get(VARIANT.DISPLAY_ORDER),
                        row.get(VARIANT.SALES_SUSPENDED),
                        row.get(VARIANT.IMAGE_ID)));

        var images = dsl.selectFrom(PRODUCT_IMAGE)
                .where(PRODUCT_IMAGE.PRODUCT_ID.eq(id))
                .orderBy(PRODUCT_IMAGE.DISPLAY_ORDER, PRODUCT_IMAGE.ID)
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
                RestoreGuard.restore("product", () -> PublicationStatus.valueOf(record.getStatus())),
                record.getFirstShownAt(),
                PublicationPeriod.of(record.getPublishStart(), record.getPublishEnd()),
                images,
                variants,
                record.getVersion()));
    }
}
