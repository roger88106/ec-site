package com.ecsite.module.inventory.application.handler;

import com.ecsite.AsyncEvents;
import com.ecsite.IntegrationTest;
import com.ecsite.module.catalog.event.ProductDeleted;
import com.ecsite.module.catalog.event.VariantDeleted;
import com.ecsite.module.catalog.event.VariantRegistered;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static com.ecsite.AsyncEvents.TIMEOUT;
import static com.ecsite.jooq.inventory.Tables.STOCK_ADJUSTMENT;
import static com.ecsite.jooq.inventory.Tables.STOCK_ITEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 商品カタログのイベントによる在庫の作成・削除の統合テスト
 *
 * <p>イベントはコミット後に非同期で処理されるので、コミットするトランザクションで発行し、処理を待つ。
 * ロールバックしない。
 */
@IntegrationTest
class CatalogEventHandlerTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    DSLContext dsl;

    @Autowired
    AsyncEvents asyncEvents;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("バリアントの登録")
    class Registration {

        @Test
        @DisplayName("AC-41 バリアントが登録されると、在庫数 0 の在庫ができる")
        void createsStockOnVariantRegistered() {
            // 準備：商品とバリアント
            var productId = UUID.randomUUID();
            var variantId = UUID.randomUUID();

            // 操作：バリアント登録のイベントを発行する
            publish(VariantRegistered.of(NOW, productId, variantId));

            // 確認：その商品の、在庫数 0 の在庫ができる
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
            // 準備：バリアント登録のイベント
            var productId = UUID.randomUUID();
            var variantId = UUID.randomUUID();
            var event = VariantRegistered.of(NOW, productId, variantId);

            // 操作：同じイベントを 2 回発行する
            // 1 回目を処理し終えてから 2 回目を送る。同じイベントの処理が重なると、
            // Spring Modulith が処理中の発行を（イベント、リスナー）で取り違え、発行の記録が完了にならないことがあるため
            publish(event);
            asyncEvents.awaitHandled(event::equals);
            publish(event);
            asyncEvents.awaitHandled(event::equals);

            // 確認：在庫が 1 つ
            assertThat(stockCount(variantId)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("バリアントの削除")
    class VariantDeletion {

        @Test
        @DisplayName("バリアントが削除されると、その在庫と調整履歴が消える")
        void deletesStockOnVariantDeleted() {
            // 準備：調整履歴のある在庫
            var productId = UUID.randomUUID();
            var deleted = UUID.randomUUID();
            register(productId, deleted);
            var stockItemId = addAdjustment(deleted);

            // 操作：バリアント削除のイベントを発行する
            publish(VariantDeleted.of(NOW, productId, deleted));

            // 確認：在庫が消え、調整履歴も消える
            await().atMost(TIMEOUT).until(() -> stockCount(deleted) == 0);
            assertThat(adjustmentCount(stockItemId)).isZero();
        }

        @Test
        @DisplayName("バリアントが削除されても、同じ商品のほかのバリアントの在庫は残る")
        void keepsOtherVariants() {
            // 準備：同じ商品の 2 つのバリアントの在庫
            var productId = UUID.randomUUID();
            var deleted = UUID.randomUUID();
            var kept = UUID.randomUUID();
            register(productId, deleted, kept);

            // 操作：片方のバリアント削除のイベントを発行する
            publish(VariantDeleted.of(NOW, productId, deleted));

            // 確認：消した方の削除を待ってから、もう片方が残ることを確認する
            await().atMost(TIMEOUT).until(() -> stockCount(deleted) == 0);
            assertThat(stockCount(kept)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("商品の削除")
    class ProductDeletion {

        @Test
        @DisplayName("商品が削除されると、その商品のすべての在庫と調整履歴が消える")
        void deletesStocksOnProductDeleted() {
            // 準備：2 つのバリアントの在庫と、片方の調整履歴
            var productId = UUID.randomUUID();
            var first = UUID.randomUUID();
            var second = UUID.randomUUID();
            register(productId, first, second);
            var stockItemId = addAdjustment(first);

            // 操作：商品削除のイベントを発行する
            publish(ProductDeleted.of(NOW, productId));

            // 確認：両方の在庫が消え、調整履歴も消える
            await().atMost(TIMEOUT).until(() -> stockCount(first) + stockCount(second) == 0);
            assertThat(adjustmentCount(stockItemId)).isZero();
        }

        @Test
        @DisplayName("商品が削除されても、ほかの商品の在庫は残る")
        void keepsOtherProducts() {
            // 準備：削除する商品と、ほかの商品の在庫
            var productId = UUID.randomUUID();
            var otherProductId = UUID.randomUUID();
            var first = UUID.randomUUID();
            var other = UUID.randomUUID();
            register(productId, first);
            register(otherProductId, other);

            // 操作：商品削除のイベントを発行する
            publish(ProductDeleted.of(NOW, productId));

            // 確認：削除する商品の在庫が消えるのを待ってから、ほかの商品の在庫が残ることを確認する
            await().atMost(TIMEOUT).until(() -> stockCount(first) == 0);
            assertThat(stockCount(other)).isEqualTo(1);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * バリアントの在庫の件数を数える。
     *
     * @param variantId バリアント ID
     * @return 在庫があれば 1、なければ 0
     */
    private int stockCount(UUID variantId) {
        return dsl.fetchCount(STOCK_ITEM, STOCK_ITEM.VARIANT_ID.eq(variantId));
    }

    /**
     * 在庫の調整履歴の件数を数える。
     *
     * @param stockItemId 在庫 ID
     * @return 調整履歴の件数
     */
    private int adjustmentCount(UUID stockItemId) {
        return dsl.fetchCount(STOCK_ADJUSTMENT, STOCK_ADJUSTMENT.STOCK_ITEM_ID.eq(stockItemId));
    }

    // ============================== 準備の補助 ==============================

    /**
     * コミットするトランザクションでイベントを発行する。
     *
     * <p>リスナーはコミット後に動く。
     *
     * @param event イベント
     */
    private void publish(Object event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    /**
     * 商品のバリアントの登録のイベントを発行し、全ての在庫ができるまで待つ。
     *
     * @param productId  商品 ID
     * @param variantIds バリアント ID
     */
    private void register(UUID productId, UUID... variantIds) {
        Stream.of(variantIds).forEach(variantId -> publish(VariantRegistered.of(NOW, productId, variantId)));
        await().atMost(TIMEOUT).until(() -> Stream.of(variantIds).allMatch(variantId -> stockCount(variantId) == 1));
    }

    /**
     * 調整履歴を 1 件加える。
     *
     * <p>在庫数は変えない。削除で一緒に消えることを確認するため。
     *
     * @param variantId バリアント ID
     * @return 在庫 ID
     */
    private UUID addAdjustment(UUID variantId) {
        var stockItemId = dsl.select(STOCK_ITEM.ID).from(STOCK_ITEM).where(STOCK_ITEM.VARIANT_ID.eq(variantId)).fetchSingle(STOCK_ITEM.ID);
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
