package com.ecsite.module.inventory.application;

import com.ecsite.IntegrationTest;
import com.ecsite.module.catalog.event.ProductDeleted;
import com.ecsite.module.catalog.event.VariantDeleted;
import com.ecsite.module.catalog.event.VariantRegistered;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.ecsite.AsyncEvents.TIMEOUT;
import static com.ecsite.jooq.inventory.Tables.STOCK_ADJUSTMENT;
import static com.ecsite.jooq.inventory.Tables.STOCK_ITEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 商品カタログのイベントによる在庫の作成・削除の統合テスト（T404）。<br />
 * イベントはコミット後に非同期で処理されるので、コミットするトランザクションで発行し、処理を待つ。
 * ロールバックしないので、作ったデータを後で消す。
 */
@IntegrationTest
class CatalogEventHandlerTest {

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    DSLContext dsl;

    /**
     * このテストで作った商品の ID（後で在庫を消す）
     */
    private final List<UUID> productIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        dsl.deleteFrom(STOCK_ITEM).where(STOCK_ITEM.PRODUCT_ID.in(productIds)).execute();
    }

    @Test
    @DisplayName("AC-41 バリアントが登録されると、在庫数 0 の在庫ができる")
    void createsStockOnVariantRegistered() {
        var productId = newProductId();
        var variantId = UUID.randomUUID();

        publish(VariantRegistered.of(NOW, productId, variantId));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            var stock = dsl.selectFrom(STOCK_ITEM).where(STOCK_ITEM.VARIANT_ID.eq(variantId)).fetchOne();
            assertThat(stock).isNotNull();
            assertThat(stock.getQuantity()).isZero();
            assertThat(stock.getProductId()).isEqualTo(productId);
        });
    }

    @Test
    @DisplayName("同じバリアント登録のイベントを 2 回受けても、在庫は 1 つ")
    void createsStockOnlyOnce() {
        var productId = newProductId();
        var variantId = UUID.randomUUID();
        var event = VariantRegistered.of(NOW, productId, variantId);

        publish(event);
        publish(event);
        // 2 回目の処理を待つため、別の在庫ができるまで待つ
        var marker = UUID.randomUUID();
        publish(VariantRegistered.of(NOW, productId, marker));

        await().atMost(TIMEOUT).until(() -> stockCount(marker) == 1);
        await().during(Duration.ofMillis(500)).atMost(TIMEOUT).until(() -> stockCount(variantId) == 1);
    }

    @Test
    @DisplayName("バリアントが削除されると、その在庫と調整履歴が消える")
    void deletesStockOnVariantDeleted() {
        var productId = newProductId();
        var deleted = UUID.randomUUID();
        var kept = UUID.randomUUID();
        publish(VariantRegistered.of(NOW, productId, deleted));
        publish(VariantRegistered.of(NOW, productId, kept));
        await().atMost(TIMEOUT).until(() -> stockCount(deleted) == 1 && stockCount(kept) == 1);
        var stockItemId = addAdjustment(deleted);

        publish(VariantDeleted.of(NOW, productId, deleted));

        await().atMost(TIMEOUT).until(() -> stockCount(deleted) == 0);
        assertThat(stockCount(kept)).isEqualTo(1);
        assertThat(dsl.fetchCount(STOCK_ADJUSTMENT, STOCK_ADJUSTMENT.STOCK_ITEM_ID.eq(stockItemId))).isZero();
    }

    @Test
    @DisplayName("商品が削除されると、その商品のすべての在庫と調整履歴が消える")
    void deletesStocksOnProductDeleted() {
        var productId = newProductId();
        var otherProductId = newProductId();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var other = UUID.randomUUID();
        publish(VariantRegistered.of(NOW, productId, first));
        publish(VariantRegistered.of(NOW, productId, second));
        publish(VariantRegistered.of(NOW, otherProductId, other));
        await().atMost(TIMEOUT).until(() -> stockCount(first) + stockCount(second) + stockCount(other) == 3);
        var stockItemId = addAdjustment(first);

        publish(ProductDeleted.of(NOW, productId));

        await().atMost(TIMEOUT).until(() -> stockCount(first) + stockCount(second) == 0);
        assertThat(dsl.fetchCount(STOCK_ADJUSTMENT, STOCK_ADJUSTMENT.STOCK_ITEM_ID.eq(stockItemId))).isZero();
        assertThat(stockCount(other)).isEqualTo(1);
    }

    // ============================== 補助 ==============================

    /**
     * 新しい商品 ID を作り、テストの後に在庫を消すために覚えておく
     */
    private UUID newProductId() {
        var productId = UUID.randomUUID();
        productIds.add(productId);
        return productId;
    }

    /**
     * コミットするトランザクションでイベントを発行する（リスナーはコミット後に動く）
     */
    private void publish(Object event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    /**
     * バリアントの在庫の件数（あれば 1、なければ 0）
     */
    private int stockCount(UUID variantId) {
        return dsl.fetchCount(STOCK_ITEM, STOCK_ITEM.VARIANT_ID.eq(variantId));
    }

    /**
     * 調整履歴を 1 件加える（在庫数は変えない。削除で一緒に消えることを確かめるため）
     *
     * @return 在庫 ID
     */
    private UUID addAdjustment(UUID variantId) {
        var stockItemId = dsl.select(STOCK_ITEM.ID).from(STOCK_ITEM).where(STOCK_ITEM.VARIANT_ID.eq(variantId)).fetchSingle().value1();
        dsl.insertInto(STOCK_ADJUSTMENT)
                .set(STOCK_ADJUSTMENT.ID, UUID.randomUUID())
                .set(STOCK_ADJUSTMENT.STOCK_ITEM_ID, stockItemId)
                .set(STOCK_ADJUSTMENT.DELTA, 1)
                .set(STOCK_ADJUSTMENT.REASON, "ARRIVAL")
                .set(STOCK_ADJUSTMENT.QUANTITY_AFTER, 1)
                .set(STOCK_ADJUSTMENT.ADJUSTED_AT, NOW)
                .execute();
        return stockItemId;
    }
}
