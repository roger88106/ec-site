package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.RestoreFailures;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.module.catalog.application.model.query.ProductListQuery;
import com.ecsite.module.catalog.application.port.MediaDirectory;
import com.ecsite.module.catalog.domain.product.ProductFactory;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static com.ecsite.RestoreFailures.RECORDS;
import static com.ecsite.RestoreFailures.assertValueIsHidden;
import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static org.mockito.Mockito.mock;

/**
 * 商品を DB の値から戻すときの失敗の単体テスト。例外の出力に DB の値が出ないことを確認する。
 */
class ProductRestoreFailureTest {

    /**
     * 例外のメッセージに入ると分かる値
     */
    private final String value = RestoreFailures.value();

    @Test
    @DisplayName("公開の状態が列挙にないとき、例外の出力に値が出ない")
    void hidesStatus() {
        var dsl = new RestoreFailures().with("product", brokenProduct()).dsl();
        var repository = new JooqProductRepository(dsl, mock(ApplicationEventPublisher.class), mock(ProductFactory.class), Clock.systemUTC());

        assertValueIsHidden(() -> repository.findById(UUID.randomUUID()), value);
    }

    @Test
    @DisplayName("商品の詳細で公開の状態が列挙にないとき、例外の出力に値が出ない")
    void hidesStatusInDetail() {
        var dsl = new RestoreFailures().with("product", brokenProduct()).dsl();
        var repository = new JooqProductQueryRepository(dsl, mock(MediaDirectory.class));

        assertValueIsHidden(() -> repository.findDetailById(UUID.randomUUID()), value);
    }

    @Test
    @DisplayName("商品の一覧で公開の状態が列挙にないとき、例外の出力に値が出ない")
    void hidesStatusInSummaries() {
        var row = RECORDS.newRecord(PRODUCT.ID, PRODUCT.PRODUCT_CODE, PRODUCT.NAME, PRODUCT.STATUS, DSL.field("variant_count", Integer.class))
                .values(UUID.randomUUID(), "P-1", "商品", value, 1);
        var dsl = new RestoreFailures().with("product", row).dsl();
        var repository = new JooqProductQueryRepository(dsl, mock(MediaDirectory.class));
        var query = new ProductListQuery(null, null, null, null);

        assertValueIsHidden(() -> repository.findSummaries(query, new PageRequest(1, 20)), value);
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
