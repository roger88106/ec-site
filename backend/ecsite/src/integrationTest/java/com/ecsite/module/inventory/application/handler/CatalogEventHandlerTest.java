package com.ecsite.module.inventory.application.handler;

import com.ecsite.module.catalog.event.ProductDeleted;
import com.ecsite.module.catalog.event.VariantDeleted;
import com.ecsite.module.catalog.event.VariantRegistered;
import com.ecsite.support.MutableClock;
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static com.ecsite.jooq.inventory.Tables.STOCK_ADJUSTMENT;
import static com.ecsite.jooq.inventory.Tables.STOCK_ITEM;
import static com.ecsite.support.async.AsyncEvents.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    // ============================== Bean ==============================

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    DSLContext dsl;

    @Autowired
    AsyncEvents asyncEvents;

    /**
     * 管理の API を呼ぶ MockMvc（商品の登録・削除と、在庫の取得）
     */
    @Autowired
    MockMvc mockMvc;

    /**
     * 商品の補助が公開のたびに進める時計
     */
    @Autowired
    MutableClock clock;

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

    @Nested
    @DisplayName("管理の API からの削除（商品の登録から在庫の削除までの流れ）")
    class DeletionFromAdminApi {

        @Test
        @DisplayName("公開前の商品のバリアントを管理の API で削除すると、その在庫が消え、同じ商品のほかのバリアントの在庫は残る")
        void deletesStockOfDeletedVariant() throws Exception {
            // 準備：管理の API で、2 つのバリアントを持つ公開前の商品を登録し、両方の在庫ができるのを待つ
            var product = draftWithTwoVariants();
            var deleted = product.variantIds.getFirst();
            var kept = product.variantIds.getLast();

            // 操作：1 つ目のバリアントを管理の API で削除する
            mockMvc.perform(delete("/api/admin/products/" + product.id + "/variants/" + deleted)
                            .param("version", String.valueOf(productVersion(product.id))))
                    .andExpect(status().isNoContent());

            // 確認：削除したバリアントの在庫の取得が 404 になり、もう 1 つの在庫は取得できる
            await().atMost(TIMEOUT).until(() -> inventoryStatus(deleted) == HttpStatus.NOT_FOUND.value());
            assertThat(inventoryStatus(kept)).isEqualTo(HttpStatus.OK.value());
        }

        @Test
        @DisplayName("公開前の商品を管理の API で削除すると、その商品の全てのバリアントの在庫が消える")
        void deletesStocksOfDeletedProduct() throws Exception {
            // 準備：管理の API で、2 つのバリアントを持つ公開前の商品を登録し、両方の在庫ができるのを待つ
            var product = draftWithTwoVariants();

            // 操作：商品を管理の API で削除する
            mockMvc.perform(delete("/api/admin/products/" + product.id)
                            .param("version", String.valueOf(productVersion(product.id))))
                    .andExpect(status().isNoContent());

            // 確認：両方のバリアントの在庫の取得が 404 になる
            await().atMost(TIMEOUT).until(() -> product.variantIds.stream()
                    .allMatch(variantId -> inventoryStatus(variantId) == HttpStatus.NOT_FOUND.value()));
        }

        /**
         * 管理の API で、2 つのバリアント（白・黒）を持つ公開前の商品を登録し、両方の在庫ができるまで待つ。
         *
         * @return 登録した商品
         * @throws Exception API の呼び出しに失敗したとき
         */
        private CatalogTestData.TestProduct draftWithTwoVariants() throws Exception {
            var catalog = new CatalogTestData(mockMvc, clock);
            var taxCategory = catalog.taxCategory("在庫の削除 " + UUID.randomUUID().toString().substring(0, 8), "10");
            var product = catalog.product(taxCategory, "削除する商品", 1000, "色").variant("白").variant("黒");
            await().atMost(TIMEOUT).until(() -> product.variantIds.stream()
                    .allMatch(variantId -> inventoryStatus(variantId) == HttpStatus.OK.value()));
            return product;
        }

        /**
         * 管理の API で商品を読み、今のバージョンを返す。
         *
         * @param productId 商品 ID
         * @return バージョン
         * @throws Exception API の呼び出しに失敗したとき
         */
        private long productVersion(UUID productId) throws Exception {
            var detail = mockMvc.perform(get("/api/admin/products/" + productId)).andReturn().getResponse().getContentAsString();
            return JsonPath.<Number>read(detail, "$.version").longValue();
        }

        /**
         * 管理の API でバリアントの在庫を取得し、応答の状態コードを返す。
         *
         * @param variantId バリアント ID
         * @return 状態コード
         * @throws IllegalStateException API の呼び出しに失敗したとき（待つ条件の中で呼ぶので、検査例外にしない）
         */
        private int inventoryStatus(UUID variantId) {
            try {
                return mockMvc.perform(get("/api/admin/inventory/variants/" + variantId)).andReturn().getResponse().getStatus();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
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
