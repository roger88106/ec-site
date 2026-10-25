package com.ecsite.module.cart.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.TestJson;
import com.ecsite.TestTokens;
import com.ecsite.module.cart.event.CartChanged;
import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.StockAdjustmentReason;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.jooq.cart.Tables.CART_ITEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * カートの変更の受け入れテスト（T2207、US-14、15）。<br />
 * 商品は B 側の API で作る。在庫はバリアント登録のイベント（コミット後に動く）では作られないので、
 * 在庫モジュールのリポジトリで直接作る。カートの中身は、表示の API（段階 3）の代わりに DB で確認する。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@RecordApplicationEvents
@Transactional
class CartApiTest {

    /**
     * API のパス
     */
    private static final String BASE = "/api/store/carts";

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    StockItemRepository stockItemRepository;

    private InventoryTestData inventory;

    @Autowired
    StockReservationApi stockReservationApi;

    @Autowired
    ApplicationEvents events;

    @Autowired
    DSLContext dsl;

    private CatalogTestData data;

    private UUID standard;

    private UUID cartId;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        clock.setInstant(NOW);
        data = new CatalogTestData(mockMvc, clock);
        standard = data.taxCategory("標準", "10");
        var body = mockMvc.perform(post(BASE))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        cartId = TestJson.createdId(body);
    }

    @Nested
    @DisplayName("AC-72、73、74、75、76 カートに入れる")
    class AddItem {

        @Test
        @DisplayName("AC-72 選べるバリアントを数量を指定して入れられる。変更でカート変更のイベントが発行される")
        void addsSelectableVariant() throws Exception {
            var variant = stockedVariant(5);

            add(cartId, variant, 2).andExpect(status().isNoContent());

            assertThat(items()).containsExactly(Map.entry(variant, 2));
            assertThat(events.stream(CartChanged.class)).extracting(CartChanged::cartId).containsExactly(cartId);
        }

        @Test
        @DisplayName("AC-72 表示されない商品・販売停止のバリアント・存在しないバリアントは 400 で、入らない")
        void rejectsUnselectableVariants() throws Exception {
            var unpublished = data.product(standard, "非公開", 1000);
            stock(unpublished.variantIds.getFirst(), 5);
            var suspended = data.product(standard, "販売停止", 1000, "色").variant("白").publish().suspend(0);
            stock(suspended.variantIds.getFirst(), 5);

            for (var variant : List.of(unpublished.variantIds.getFirst(), suspended.variantIds.getFirst(), UUID.randomUUID())) {
                add(cartId, variant, 1)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errorCode").value("VARIANT_NOT_SELECTABLE"));
            }
            assertThat(items()).isEmpty();
        }

        @Test
        @DisplayName("AC-74 同じバリアントを入れると数量を合算する")
        void mergesQuantity() throws Exception {
            var variant = stockedVariant(5);

            add(cartId, variant, 2).andExpect(status().isNoContent());
            add(cartId, variant, 3).andExpect(status().isNoContent());

            assertThat(items()).containsExactly(Map.entry(variant, 5));
        }

        @Test
        @DisplayName("AC-75 数量が 1〜10 でなければ 400。合算して 10 を超える場合も 400")
        void rejectsQuantityOutOfRange() throws Exception {
            var variant = stockedVariant(20);

            add(cartId, variant, 0).andExpect(status().isBadRequest());
            add(cartId, variant, 11).andExpect(status().isBadRequest());
            add(cartId, variant, 8).andExpect(status().isNoContent());
            add(cartId, variant, 3)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CART_QUANTITY"));

            assertThat(items()).containsExactly(Map.entry(variant, 8));
        }

        @Test
        @DisplayName("AC-76 入れた後の数量が販売可能数を超えると 400。引当の分は売れないものとして数える")
        void rejectsQuantityOverSellable() throws Exception {
            var variant = stockedVariant(3);
            stockReservationApi.reserve(UUID.randomUUID(), Map.of(variant, 1), NOW.plus(Duration.ofMinutes(15)));

            add(cartId, variant, 2).andExpect(status().isNoContent());
            add(cartId, variant, 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("QUANTITY_EXCEEDS_STOCK"))
                    // 在庫数そのものは示さない（AC-64）
                    .andExpect(jsonPath("$.message").value("在庫が足りないため、この数量ではカートに入れられません。"));

            assertThat(items()).containsExactly(Map.entry(variant, 2));
        }

        @Test
        @DisplayName("存在しないカートは 404")
        void rejectsUnknownCart() throws Exception {
            add(UUID.randomUUID(), stockedVariant(1), 1).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("AC-78 数量の変更と削除")
    class ChangeItem {

        @Test
        @DisplayName("AC-78 数量を変更できる。増やすときは販売可能数を超えると 400、減らすときは確認しない")
        void changesQuantity() throws Exception {
            var variant = stockedVariant(3);
            add(cartId, variant, 3).andExpect(status().isNoContent());
            // 入れた後に在庫が減っても、減らす変更はできる
            stockItemRepository.adjust(variant, StockAdjustment.create(-3, StockAdjustmentReason.DAMAGE_OR_LOSS, null, TestTokens.OPERATOR, NOW));

            change(cartId, variant, 2).andExpect(status().isNoContent());
            assertThat(items()).containsExactly(Map.entry(variant, 2));

            stockItemRepository.adjust(variant, StockAdjustment.create(2, StockAdjustmentReason.ARRIVAL, null, TestTokens.OPERATOR, NOW));
            change(cartId, variant, 3)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("QUANTITY_EXCEEDS_STOCK"));
        }

        @Test
        @DisplayName("AC-78 カートにない品目の数量の変更は 404。範囲外の数量は 400")
        void rejectsInvalidChange() throws Exception {
            var variant = stockedVariant(3);

            change(cartId, variant, 1).andExpect(status().isNotFound());
            add(cartId, variant, 1).andExpect(status().isNoContent());
            change(cartId, variant, 11).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("AC-78 品目を削除できる。カートにない品目の削除も 204")
        void removesItem() throws Exception {
            var white = stockedVariant(3);
            var black = stockedVariant(3);
            add(cartId, white, 1).andExpect(status().isNoContent());
            add(cartId, black, 1).andExpect(status().isNoContent());

            mockMvc.perform(delete(BASE + "/" + cartId + "/items/" + white)).andExpect(status().isNoContent());
            mockMvc.perform(delete(BASE + "/" + cartId + "/items/" + white)).andExpect(status().isNoContent());

            assertThat(items()).containsExactly(Map.entry(black, 1));
        }
    }

    @Test
    @DisplayName("AC-81 最後の変更から 30 日たったカートに入れると、古い品目は消える")
    void dropsExpiredItems() throws Exception {
        var old = stockedVariant(3);
        var fresh = stockedVariant(3);
        // 商品の公開で時計が進むので、カートを変えた時刻から数える
        var changedAt = clock.instant();
        add(cartId, old, 1).andExpect(status().isNoContent());

        clock.setInstant(changedAt.plus(Duration.ofDays(30)));
        add(cartId, fresh, 1).andExpect(status().isNoContent());

        assertThat(items()).containsExactly(Map.entry(fresh, 1));
    }

    // ============================== 補助 ==============================

    /**
     * カートに入れる要求を送る
     */
    private ResultActions add(UUID cart, UUID variantId, int quantity) throws Exception {
        return mockMvc.perform(post(BASE + "/" + cart + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"variantId": "%s", "quantity": %d}""".formatted(variantId, quantity)));
    }

    /**
     * 品目の数量を変える要求を送る
     */
    private ResultActions change(UUID cart, UUID variantId, int quantity) throws Exception {
        return mockMvc.perform(put(BASE + "/" + cart + "/items/" + variantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"quantity": %d}""".formatted(quantity)));
    }

    /**
     * 公開した商品のバリアントを作り、在庫数を設定して、バリアント ID を返す
     */
    private UUID stockedVariant(int quantity) throws Exception {
        var variantId = data.product(standard, "商品", 1000).publish().variantIds.getFirst();
        stock(variantId, quantity);
        return variantId;
    }

    /**
     * バリアントの在庫を作り、在庫数を設定する（入荷として調整する）
     */
    private void stock(UUID variantId, int quantity) {
        inventory.stock(variantId, UUID.randomUUID(), quantity);
    }

    /**
     * カートの品目（入れた順）を DB から読む
     */
    private List<Map.Entry<UUID, Integer>> items() {
        return dsl.select(CART_ITEM.VARIANT_ID, CART_ITEM.QUANTITY)
                .from(CART_ITEM)
                .where(CART_ITEM.CART_ID.eq(cartId))
                .orderBy(CART_ITEM.SEQUENCE_NO)
                .fetch(row -> Map.entry(row.value1(), row.value2()));
    }
}
