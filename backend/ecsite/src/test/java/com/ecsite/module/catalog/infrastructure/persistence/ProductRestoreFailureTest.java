package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.module.catalog.application.model.query.ProductListQuery;
import com.ecsite.module.catalog.application.port.MediaDirectory;
import com.ecsite.module.catalog.domain.product.ProductFactory;
import com.ecsite.support.RestoreFailures;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static com.ecsite.jooq.catalog.Tables.PRODUCT_IMAGE;
import static com.ecsite.jooq.catalog.Tables.VARIANT;
import static com.ecsite.support.RestoreFailures.RECORDS;
import static com.ecsite.support.RestoreFailures.assertDatabaseFailurePropagates;
import static com.ecsite.support.RestoreFailures.assertValueIsHidden;
import static org.mockito.Mockito.mock;

/**
 * 商品を DB の値から戻すときの失敗の単体テスト
 *
 * <p>例外の出力に DB の値が出ないことを確認する。
 */
class ProductRestoreFailureTest {

    // ============================== テストのデータ ==============================

    /**
     * 例外のメッセージに入ると分かる値
     *
     * <p>公開の状態の列に入れる。
     */
    private final String value = RestoreFailures.value();

    /**
     * 例外の出力に出ないことを確認する、負の表示順
     */
    private static final int NEGATIVE_ORDER = -123456;

    /**
     * 上限（20 枚）を超える画像の数
     */
    private static final int IMAGE_COUNT_OVER_LIMIT = 21;

    // ============================== テスト ==============================

    @Test
    @DisplayName("公開の状態が列挙にないとき、例外の出力に値が出ない")
    void hidesStatus() {
        // 準備：公開の状態を壊した商品の行を返す Repository
        var dsl = new RestoreFailures().with("product", brokenProduct()).dsl();
        var repository = new JooqProductRepository(dsl, mock(ApplicationEventPublisher.class), mock(ProductFactory.class), Clock.systemUTC());

        // 操作と確認：商品を読むと復元の失敗になり、例外の出力に値が出ない
        assertValueIsHidden(() -> repository.findById(UUID.randomUUID()), value, IllegalArgumentException.class);
    }

    @Test
    @DisplayName("商品の詳細で公開の状態が列挙にないとき、例外の出力に値が出ない")
    void hidesStatusInDetail() {
        // 準備：公開の状態を壊した商品の行を返す、参照用の Repository
        var dsl = new RestoreFailures().with("product", brokenProduct()).dsl();
        var repository = new JooqProductQueryRepository(dsl, mock(MediaDirectory.class));

        // 操作と確認：商品の詳細を読むと復元の失敗になり、例外の出力に値が出ない
        assertValueIsHidden(() -> repository.findDetailById(UUID.randomUUID()), value);
    }

    @Test
    @DisplayName("商品の一覧で公開の状態が列挙にないとき、例外の出力に値が出ない")
    void hidesStatusInSummaries() {
        // 準備：公開の状態を壊した、一覧の行を返す参照用の Repository
        var row = RECORDS.newRecord(PRODUCT.ID, PRODUCT.PRODUCT_CODE, PRODUCT.NAME, PRODUCT.STATUS, DSL.field("variant_count", Integer.class))
                .values(UUID.randomUUID(), "P-1", "商品", value, 1);
        var dsl = new RestoreFailures().with("product", row).dsl();
        var repository = new JooqProductQueryRepository(dsl, mock(MediaDirectory.class));
        var query = new ProductListQuery(null, null, null, null);

        // 操作と確認：一覧を読むと復元の失敗になり、例外の出力に値が出ない
        assertValueIsHidden(() -> repository.findSummaries(query, new PageRequest(1, 20)), value);
    }

    @Test
    @DisplayName("商品品番が書式に合わないとき、例外の出力に値が出ない")
    void hidesProductCode() {
        // 準備：品番を壊した商品の行を返す Repository
        var product = validProduct();
        product.set(PRODUCT.PRODUCT_CODE, value);
        var repository = realFactoryRepository(new RestoreFailures().with("product", product));

        // 操作と確認：商品を読むと復元の失敗になり、例外の出力に値が出ない
        assertValueIsHidden(() -> repository.findById(UUID.randomUUID()), value, BusinessException.class);
    }

    @Test
    @DisplayName("画像の表示順が負のとき、例外の出力に値が出ない")
    void hidesNegativeImageOrder() {
        // 準備：表示順が負の画像の行を持つ商品
        var image = validImage(NEGATIVE_ORDER);
        var repository = realFactoryRepository(new RestoreFailures().with("product", validProduct()).with("product_image", image));

        // 操作と確認：復元の失敗になり、例外の出力に表示順の値が出ない
        assertValueIsHidden(() -> repository.findById(UUID.randomUUID()), String.valueOf(NEGATIVE_ORDER),
                IllegalArgumentException.class);
    }

    @Test
    @DisplayName("バリアントの表示順が負のとき、例外の出力に値が出ない")
    void hidesNegativeVariantOrder() {
        // 準備：表示順が負のバリアントの行を持つ商品
        var variant = validVariant(NEGATIVE_ORDER, new String[0]);
        var repository = realFactoryRepository(new RestoreFailures().with("product", validProduct()).with("variant", variant));

        // 操作と確認：復元の失敗になり、例外の出力に表示順の値が出ない
        assertValueIsHidden(() -> repository.findById(UUID.randomUUID()), String.valueOf(NEGATIVE_ORDER),
                IllegalArgumentException.class);
    }

    @Test
    @DisplayName("軸のない商品のバリアントが軸の値を持つとき、例外の出力に値が出ない")
    void hidesAxisValuesWithoutAxes() {
        // 準備：軸の値の数が商品の軸の数と合わないバリアントの行を持つ商品
        var variant = validVariant(1, new String[]{value});
        var repository = realFactoryRepository(new RestoreFailures().with("product", validProduct()).with("variant", variant));

        // 操作と確認：復元の失敗になり、例外の出力に値が出ない
        assertValueIsHidden(() -> repository.findById(UUID.randomUUID()), value, IllegalArgumentException.class);
    }

    @Test
    @DisplayName("画像が 21 枚あるとき、例外の出力に値が出ない")
    void hidesTooManyImages() {
        // 準備：上限の 20 枚を 1 枚超える画像の行を持つ、説明に目印の値がある商品
        var product = validProduct();
        product.set(PRODUCT.DESCRIPTION, value);
        var images = IntStream.rangeClosed(1, IMAGE_COUNT_OVER_LIMIT).mapToObj(order -> validImage(order)).toList();
        var repository = realFactoryRepository(new RestoreFailures().with("product", product).withRows("product_image", images));

        // 操作と確認：復元の失敗になり、例外の出力に値が出ない
        assertValueIsHidden(() -> repository.findById(UUID.randomUUID()), value, IllegalArgumentException.class);
    }

    @Test
    @DisplayName("軸のない商品にバリアントが 2 つあるとき、例外の出力に値が出ない")
    void hidesTwoVariantsWithoutAxes() {
        // 準備：軸のない商品に、軸の値のないバリアントの行が 2 つ。説明に目印の値がある
        var product = validProduct();
        product.set(PRODUCT.DESCRIPTION, value);
        var variants = List.of(validVariant(1, new String[0]), validVariant(2, new String[0]));
        var repository = realFactoryRepository(new RestoreFailures().with("product", product).withRows("variant", variants));

        // 操作と確認：復元の失敗になり、例外の出力に値が出ない
        assertValueIsHidden(() -> repository.findById(UUID.randomUUID()), value, IllegalArgumentException.class);
    }

    @Test
    @DisplayName("バリアントの表を読む DB の失敗は、復元のエラーに変わらずそのまま出る")
    void propagatesVariantReadFailure() {
        // 準備：バリアントの表を読むと失敗する DB
        var repository = realFactoryRepository(new RestoreFailures().with("product", validProduct()).failingOn("variant"));

        // 操作と確認：DB の失敗のまま出る
        assertDatabaseFailurePropagates(() -> repository.findById(UUID.randomUUID()));
    }

    @Test
    @DisplayName("画像の表を読む DB の失敗は、復元のエラーに変わらずそのまま出る")
    void propagatesImageReadFailure() {
        // 準備：画像の表を読むと失敗する DB
        var repository = realFactoryRepository(new RestoreFailures().with("product", validProduct()).failingOn("product_image"));

        // 操作と確認：DB の失敗のまま出る
        assertDatabaseFailurePropagates(() -> repository.findById(UUID.randomUUID()));
    }

    @Test
    @DisplayName("バリアント品番が書式に合わないとき、例外の出力に値が出ない")
    void hidesVariantCode() {
        // 準備：品番を壊したバリアントの行を持つ商品
        var variant = validVariant(1, new String[0]);
        variant.set(VARIANT.VARIANT_CODE, value);
        var repository = realFactoryRepository(new RestoreFailures().with("product", validProduct()).with("variant", variant));

        // 操作と確認：復元の失敗になり、例外の出力に値が出ない
        assertValueIsHidden(() -> repository.findById(UUID.randomUUID()), value, BusinessException.class);
    }

    @Test
    @DisplayName("公開中で初めて表示した日時も公開終了もないとき、例外の出力に値が出ない")
    void hidesPublishedWithoutFirstShownAt() {
        // 準備：公開中なのに、初めて表示した日時も公開終了もない商品
        var product = validProduct();
        product.set(PRODUCT.STATUS, "PUBLISHED");
        product.set(PRODUCT.DESCRIPTION, value);
        var repository = realFactoryRepository(new RestoreFailures().with("product", product));

        // 操作と確認：復元の失敗になり、例外の出力に値が出ない
        assertValueIsHidden(() -> repository.findById(UUID.randomUUID()), value, IllegalArgumentException.class);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 本物の商品ファクトリーを使う Repository を作る。
     *
     * @param rows 表ごとの行
     * @return Repository
     */
    private static JooqProductRepository realFactoryRepository(RestoreFailures rows) {
        return new JooqProductRepository(rows.dsl(), mock(ApplicationEventPublisher.class), new ProductFactory(), Clock.systemUTC());
    }

    /**
     * 条件に合う、非公開で軸のない商品の行を作る。
     *
     * @return 商品の行
     */
    private Record validProduct() {
        var product = brokenProduct();
        product.set(PRODUCT.STATUS, "UNPUBLISHED");
        product.set(PRODUCT.NAME, "商品");
        product.set(PRODUCT.DESCRIPTION, "");
        product.set(PRODUCT.TAX_CATEGORY_ID, UUID.randomUUID());
        product.set(PRODUCT.VERSION, 0L);
        return product;
    }

    /**
     * 画像の行を作る。
     *
     * @param displayOrder 表示順
     * @return 画像の行
     */
    private static Record validImage(int displayOrder) {
        var image = RECORDS.newRecord(PRODUCT_IMAGE.fields());
        image.set(PRODUCT_IMAGE.ID, UUID.randomUUID());
        image.set(PRODUCT_IMAGE.PRODUCT_ID, UUID.randomUUID());
        image.set(PRODUCT_IMAGE.ASSET_ID, UUID.randomUUID());
        image.set(PRODUCT_IMAGE.DISPLAY_ORDER, displayOrder);
        return image;
    }

    /**
     * バリアントの行を作る。
     *
     * @param displayOrder 表示順
     * @param axisValues   軸の値
     * @return バリアントの行
     */
    private static Record validVariant(int displayOrder, String[] axisValues) {
        var axisValuesField = ProductParts.axisValues(VARIANT.ID);
        var fields = Stream.concat(Arrays.stream(VARIANT.fields()), Stream.of(axisValuesField)).toArray(Field<?>[]::new);
        var variant = RECORDS.newRecord(fields);
        variant.set(VARIANT.ID, UUID.randomUUID());
        variant.set(VARIANT.PRODUCT_ID, UUID.randomUUID());
        variant.set(VARIANT.VARIANT_CODE, "PROD-1-A");
        variant.set(VARIANT.AXIS_KEY, "");
        variant.set(VARIANT.DISPLAY_ORDER, displayOrder);
        variant.set(VARIANT.SALES_SUSPENDED, false);
        variant.set(axisValuesField, axisValues);
        return variant;
    }

    /**
     * 公開の状態を壊した商品の行を作る。
     *
     * <p>商品の詳細の問い合わせと同じく、商品の列の後に、軸と所属カテゴリの配列の列を持つ。
     *
     * @return 商品の行
     */
    private Record brokenProduct() {
        var axes = ProductParts.axes(PRODUCT.ID);
        var categoryIds = ProductParts.categoryIds(PRODUCT.ID);
        var fields = Stream.concat(Arrays.stream(PRODUCT.fields()), Stream.of(axes, categoryIds)).toArray(Field<?>[]::new);
        var product = RECORDS.newRecord(fields);
        product.set(PRODUCT.ID, UUID.randomUUID());
        product.set(PRODUCT.PRODUCT_CODE, "PROD-1");
        product.set(PRODUCT.PRICE_EXCL_TAX, 1L);
        product.set(PRODUCT.STATUS, value);
        product.set(axes, new String[0]);
        product.set(categoryIds, new UUID[0]);
        return product;
    }
}
