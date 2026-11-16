package com.ecsite.module.cart.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.TestJson;
import com.ecsite.module.cart.event.CartChanged;
import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
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
 * カートの変更の受け入れテスト
 *
 * <p>ログインせずに作ったカートへの、品目の追加・数量の変更・削除と、期限の切れたカートへの追加を、API を通して確認する。
 * カートの中身の表示は {@code StorefrontCartTest}、会員のカートとカートを移す操作は {@code MemberCartApiTest}、
 * 数量と品目の数の上限の判定そのものは {@code CartTest} が確認する。
 *
 * <p>商品は B 側の API で作る。在庫はバリアント登録のイベント（コミット後に動く）では作られないので、
 * 在庫モジュールのリポジトリで直接作る。カートの中身は DB で確認する。テストごとにロールバックする。
 */
@IntegrationTest
@RecordApplicationEvents
@Transactional
class CartApiTest {

    // ============================== 定数 ==============================

    /**
     * API のパス
     */
    private static final String BASE = "/api/store/carts";

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 1 つのカートに入れられる品目の数の上限
     */
    private static final int MAX_ITEMS = 30;

    /**
     * 販売可能数を超えたときの、利用者へのメッセージ（在庫数を含まない）
     */
    private static final String EXCEEDS_STOCK_MESSAGE = "在庫が足りないため、この数量ではカートに入れられません。";

    // ============================== Bean ==============================

    /**
     * API を呼ぶ MockMvc
     */
    @Autowired
    MockMvc mockMvc;

    /**
     * テストの時計
     */
    @Autowired
    MutableClock clock;

    /**
     * 在庫のリポジトリ（在庫数を増減する）
     */
    @Autowired
    StockItemRepository stockItemRepository;

    /**
     * 在庫の引当の API
     */
    @Autowired
    StockReservationApi stockReservationApi;

    /**
     * 発行されたイベント
     */
    @Autowired
    ApplicationEvents events;

    /**
     * DB を読む jOOQ
     */
    @Autowired
    DSLContext dsl;

    /**
     * 在庫を作る補助
     */
    private InventoryTestData inventory;

    /**
     * 税区分・カテゴリ・商品を作る補助
     */
    private CatalogTestData catalog;

    /**
     * 税率 10% の税区分の ID
     */
    private UUID standard;

    /**
     * テストごとに作る、ログインせずに作ったカートの ID
     */
    private UUID cartId;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        catalog = new CatalogTestData(mockMvc, clock);
        standard = catalog.taxCategory("標準", "10");
        cartId = newCart();
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("カートに入れる")
    class AddItem {

        @Test
        @DisplayName("AC-72 選べるバリアントを、指定した数量で入れられる")
        void addsSelectableVariant() throws Exception {
            // 準備：在庫が 5 のバリアント
            var variant = stockedVariant(5);

            // 操作：2 個入れる
            var result = add(cartId, variant, 2);

            // 確認：受け付けられ、カートに 2 個入っている
            result.andExpect(status().isNoContent());
            assertThat(items()).containsExactly(Map.entry(variant, 2));
        }

        @Test
        @DisplayName("入れると、そのカートの変更のイベントが発行される")
        void publishesCartChanged() throws Exception {
            // 準備：在庫が 5 のバリアント
            var variant = stockedVariant(5);

            // 操作：2 個入れる
            add(cartId, variant, 2).andExpect(status().isNoContent());

            // 確認：このカートの変更のイベントだけが発行されている
            assertThat(events.stream(CartChanged.class)).extracting(CartChanged::cartId).containsExactly(cartId);
        }

        @Test
        @DisplayName("AC-72 表示されない商品のバリアントは入れられない")
        void rejectsUnpublishedProduct() throws Exception {
            // 準備：在庫はあるが、一度も公開していない商品のバリアント
            var unpublished = catalog.product(standard, "非公開", 1000).variantIds.getFirst();
            inventory.stock(unpublished, 5);

            // 操作と確認：選べないバリアントとして拒否され、カートは空のまま
            assertNotSelectable(unpublished);
        }

        @Test
        @DisplayName("AC-72 販売停止のバリアントは入れられない")
        void rejectsSuspendedVariant() throws Exception {
            // 準備：在庫はあるが、販売停止にしたバリアント
            var suspended = catalog.product(standard, "販売停止", 1000, "色").variant("白").publish().suspend(0).variantIds.getFirst();
            inventory.stock(suspended, 5);

            // 操作と確認：選べないバリアントとして拒否され、カートは空のまま
            assertNotSelectable(suspended);
        }

        @Test
        @DisplayName("AC-72 存在しないバリアントは入れられない")
        void rejectsUnknownVariant() throws Exception {
            // 操作と確認：選べないバリアントとして拒否され、カートは空のまま
            assertNotSelectable(UUID.randomUUID());
        }

        @Test
        @DisplayName("AC-72 在庫のないバリアントは入れられない")
        void rejectsOutOfStockVariant() throws Exception {
            // 準備：在庫が 0 のバリアント
            var soldOut = stockedVariant(0);

            // 操作：1 個入れる
            var result = add(cartId, soldOut, 1);

            // 確認：販売可能数を超えるとして拒否され、カートは空のまま
            result.andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("QUANTITY_EXCEEDS_STOCK"));
            assertThat(items()).isEmpty();
        }

        @Test
        @DisplayName("AC-74 カートにあるバリアントをもう一度入れると、数量を合算する")
        void mergesQuantity() throws Exception {
            // 準備：在庫が 5 のバリアントを 2 個入れたカート
            var variant = stockedVariant(5);
            add(cartId, variant, 2).andExpect(status().isNoContent());

            // 操作：同じバリアントを 3 個入れる
            add(cartId, variant, 3).andExpect(status().isNoContent());

            // 確認：1 行のまま、数量が 5
            assertThat(items()).containsExactly(Map.entry(variant, 5));
        }

        @Test
        @DisplayName("存在しないカートには入れられない")
        void rejectsUnknownCart() throws Exception {
            // 準備：在庫のあるバリアント
            var variant = stockedVariant(1);

            // 操作と確認：見つからない
            add(UUID.randomUUID(), variant, 1).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("入れる数量の上限")
    class AddQuantityLimit {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {0, 11})
        @DisplayName("AC-75 1〜10 の外の数量は入れられない")
        void rejectsQuantityOutOfRange(int quantity) throws Exception {
            // 準備：在庫が 20 のバリアント
            var variant = stockedVariant(20);

            // 操作：範囲の外の数量を入れる
            var result = add(cartId, variant, quantity);

            // 確認：拒否され、カートは空のまま
            result.andExpect(status().isBadRequest());
            assertThat(items()).isEmpty();
        }

        @Test
        @DisplayName("AC-75 合算して 10 を超える数量は入れられず、数量は変わらない")
        void rejectsMergedQuantityOverLimit() throws Exception {
            // 準備：在庫が 20 のバリアントを 8 個入れたカート
            var variant = stockedVariant(20);
            add(cartId, variant, 8).andExpect(status().isNoContent());

            // 操作：同じバリアントを 3 個入れる（合算で 11）
            var result = add(cartId, variant, 3);

            // 確認：数量の誤りとして拒否され、8 個のまま
            result.andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CART_QUANTITY"));
            assertThat(items()).containsExactly(Map.entry(variant, 8));
        }
    }

    @Nested
    @DisplayName("品目の数の上限")
    class ItemLimit {

        @Test
        @DisplayName("AC-75 30 品目のカートに 31 品目めは入れられず、30 品目のまま")
        void rejectsThirtyFirstItem() throws Exception {
            // 準備：在庫のある 31 のバリアントのうち、30 を入れたカート
            var variants = stockedVariants(MAX_ITEMS + 1);
            for (var variant : variants.subList(0, MAX_ITEMS)) {
                add(cartId, variant, 1).andExpect(status().isNoContent());
            }

            // 操作：31 品目めを入れる
            var result = add(cartId, variants.getLast(), 1);

            // 確認：品目の数の上限として拒否され、品目は 30 のまま
            result.andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TOO_MANY_CART_ITEMS"));
            assertThat(items()).extracting(Map.Entry::getKey).containsExactlyElementsOf(variants.subList(0, MAX_ITEMS));
        }
    }

    @Nested
    @DisplayName("入れるときの販売可能数")
    class AddStock {

        @Test
        @DisplayName("AC-76 入れた後の数量が販売可能数を超えると入れられない。引当の分は売れないものとして数える")
        void rejectsQuantityOverSellable() throws Exception {
            // 準備：在庫 3 のうち 1 を引き当て、残りの 2 をカートに入れる
            var variant = stockedVariant(3);
            stockReservationApi.reserve(UUID.randomUUID(), Map.of(variant, 1), NOW.plus(Duration.ofMinutes(15)));
            add(cartId, variant, 2).andExpect(status().isNoContent());

            // 操作：もう 1 個入れる（合算で 3、販売可能数は 2）
            var result = add(cartId, variant, 1);

            // 確認：販売可能数を超えるとして拒否され、2 個のまま
            result.andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("QUANTITY_EXCEEDS_STOCK"));
            assertThat(items()).containsExactly(Map.entry(variant, 2));
        }

        @Test
        @DisplayName("AC-76 販売可能数を超えたときのメッセージは、販売可能数を示さない")
        void doesNotShowSellableQuantity() throws Exception {
            // 準備：在庫が 1 のバリアント
            var variant = stockedVariant(1);

            // 操作：2 個入れる
            var result = add(cartId, variant, 2);

            // 確認：数を含まない、決まったメッセージ
            result.andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("QUANTITY_EXCEEDS_STOCK"))
                    .andExpect(jsonPath("$.message").value(EXCEEDS_STOCK_MESSAGE));
        }

        @Test
        @DisplayName("AC-76 カートに入れても在庫は引き当てない")
        void doesNotReserveStock() throws Exception {
            // 準備：在庫が 3 のバリアントを、このカートに 3 個入れる
            var variant = stockedVariant(3);
            add(cartId, variant, 3).andExpect(status().isNoContent());

            // 操作：別のカートに、同じバリアントを 3 個入れる
            var otherCart = newCart();
            var result = add(otherCart, variant, 3);

            // 確認：先のカートの分を引かずに受け付けられる
            result.andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("数量の変更")
    class ChangeQuantity {

        @Test
        @DisplayName("AC-78 数量を減らすときは、在庫が減っていても変えられる")
        void decreasesRegardlessOfStock() throws Exception {
            // 準備：在庫 3 を全てカートに入れた後、在庫が 0 になる
            var variant = stockedVariant(3);
            add(cartId, variant, 3).andExpect(status().isNoContent());
            inventory.adjust(variant, -3);

            // 操作：2 個に減らす
            var result = change(cartId, variant, 2);

            // 確認：受け付けられ、2 個になる
            result.andExpect(status().isNoContent());
            assertThat(items()).containsExactly(Map.entry(variant, 2));
        }

        @Test
        @DisplayName("AC-78 数量を増やすときは、販売可能数を超えると変えられない")
        void rejectsIncreaseOverSellable() throws Exception {
            // 準備：在庫 3 のうち 2 をカートに入れた後、在庫が 2 になる
            var variant = stockedVariant(3);
            add(cartId, variant, 2).andExpect(status().isNoContent());
            inventory.adjust(variant, -1);

            // 操作：3 個に増やす
            var result = change(cartId, variant, 3);

            // 確認：販売可能数を超えるとして拒否され、2 個のまま
            result.andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("QUANTITY_EXCEEDS_STOCK"));
            assertThat(items()).containsExactly(Map.entry(variant, 2));
        }

        @Test
        @DisplayName("AC-78 販売可能数ちょうどまでは、数量を増やせる")
        void increasesUpToSellable() throws Exception {
            // 準備：在庫 3 のうち 2 をカートに入れる
            var variant = stockedVariant(3);
            add(cartId, variant, 2).andExpect(status().isNoContent());

            // 操作：3 個に増やす
            var result = change(cartId, variant, 3);

            // 確認：受け付けられ、3 個になる
            result.andExpect(status().isNoContent());
            assertThat(items()).containsExactly(Map.entry(variant, 3));
        }

        @Test
        @DisplayName("AC-78 今と同じ数量への変更は増量でないので、在庫がなくても受け付ける")
        void acceptsSameQuantityRegardlessOfStock() throws Exception {
            // 準備：在庫 3 のうち 2 をカートに入れた後、在庫が 0 になる
            var variant = stockedVariant(3);
            add(cartId, variant, 2).andExpect(status().isNoContent());
            inventory.adjust(variant, -3);

            // 操作：2 個に変える
            var result = change(cartId, variant, 2);

            // 確認：受け付けられ、2 個のまま
            result.andExpect(status().isNoContent());
            assertThat(items()).containsExactly(Map.entry(variant, 2));
        }

        @Test
        @DisplayName("AC-78 カートにない品目の数量は変えられない")
        void rejectsItemNotInCart() throws Exception {
            // 準備：在庫はあるが、カートに入れていないバリアント
            var variant = stockedVariant(3);

            // 操作と確認：見つからない
            change(cartId, variant, 1).andExpect(status().isNotFound());
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {0, 11})
        @DisplayName("AC-78 変更後の数量が 1〜10 の外なら変えられない")
        void rejectsQuantityOutOfRange(int quantity) throws Exception {
            // 準備：在庫が 3 のバリアントを 1 個入れたカート
            var variant = stockedVariant(3);
            add(cartId, variant, 1).andExpect(status().isNoContent());

            // 操作：範囲の外の数量に変える
            var result = change(cartId, variant, quantity);

            // 確認：拒否され、1 個のまま
            result.andExpect(status().isBadRequest());
            assertThat(items()).containsExactly(Map.entry(variant, 1));
        }
    }

    @Nested
    @DisplayName("品目の削除")
    class RemoveItem {

        @Test
        @DisplayName("AC-78 品目を削除でき、ほかの品目は残る")
        void removesItem() throws Exception {
            // 準備：白と黒を 1 個ずつ入れたカート
            var white = stockedVariant(3);
            var black = stockedVariant(3);
            add(cartId, white, 1).andExpect(status().isNoContent());
            add(cartId, black, 1).andExpect(status().isNoContent());

            // 操作：白を削除する
            var result = remove(cartId, white);

            // 確認：受け付けられ、黒だけが残る
            result.andExpect(status().isNoContent());
            assertThat(items()).containsExactly(Map.entry(black, 1));
        }

        @Test
        @DisplayName("AC-78 カートにない品目を削除しても、受け付けられる")
        void acceptsRemovingItemNotInCart() throws Exception {
            // 準備：白を入れて削除し、黒が残ったカート
            var white = stockedVariant(3);
            var black = stockedVariant(3);
            add(cartId, white, 1).andExpect(status().isNoContent());
            add(cartId, black, 1).andExpect(status().isNoContent());
            remove(cartId, white).andExpect(status().isNoContent());

            // 操作：もうない白を、もう一度削除する
            var result = remove(cartId, white);

            // 確認：受け付けられ、黒は残る
            result.andExpect(status().isNoContent());
            assertThat(items()).containsExactly(Map.entry(black, 1));
        }

        @Test
        @DisplayName("AC-78 在庫が減っていても、品目を削除できる")
        void removesRegardlessOfStock() throws Exception {
            // 準備：在庫 3 を全てカートに入れた後、在庫が 0 になる
            var variant = stockedVariant(3);
            add(cartId, variant, 3).andExpect(status().isNoContent());
            inventory.adjust(variant, -3);

            // 操作：削除する
            var result = remove(cartId, variant);

            // 確認：受け付けられ、カートは空になる
            result.andExpect(status().isNoContent());
            assertThat(items()).isEmpty();
        }
    }

    @Nested
    @DisplayName("入れた後に選べなくなった品目")
    class NoLongerSelectable {

        @Test
        @DisplayName("AC-78 入れた後に非公開になった商品の品目も、数量を減らせる")
        void decreasesUnpublished() throws Exception {
            // 準備：2 個入れた後に、商品を非公開にする
            var product = inCart(2);
            product.unpublish();
            var variant = product.variantIds.getFirst();

            // 操作と確認：1 個に減らせる
            change(cartId, variant, 1).andExpect(status().isNoContent());
            assertThat(items()).containsExactly(Map.entry(variant, 1));
        }

        @Test
        @DisplayName("AC-78 入れた後に非公開になった商品の品目も、削除できる")
        void removesUnpublished() throws Exception {
            // 準備：2 個入れた後に、商品を非公開にする
            var product = inCart(2);
            product.unpublish();

            // 操作と確認：削除でき、カートは空になる
            remove(cartId, product.variantIds.getFirst()).andExpect(status().isNoContent());
            assertThat(items()).isEmpty();
        }

        @Test
        @DisplayName("AC-78 入れた後に非公開になった商品の品目も、販売可能数の範囲で増やせる（増量は商品カタログを見ない）")
        void increasesUnpublishedWithinSellable() throws Exception {
            // 準備：2 個入れた後に、商品を非公開にする
            var product = inCart(2);
            product.unpublish();
            var variant = product.variantIds.getFirst();

            // 操作と確認：在庫 3 の範囲の 3 個に増やせる
            change(cartId, variant, 3).andExpect(status().isNoContent());
            assertThat(items()).containsExactly(Map.entry(variant, 3));
        }

        @Test
        @DisplayName("AC-78 入れた後に販売停止になったバリアントの品目も、数量を減らせる")
        void decreasesSuspended() throws Exception {
            // 準備：2 個入れた後に、バリアントを販売停止にする
            var product = inCart(2);
            product.suspend(0);
            var variant = product.variantIds.getFirst();

            // 操作と確認：1 個に減らせる
            change(cartId, variant, 1).andExpect(status().isNoContent());
            assertThat(items()).containsExactly(Map.entry(variant, 1));
        }

        @Test
        @DisplayName("AC-78 入れた後に販売停止になったバリアントの品目も、削除できる")
        void removesSuspended() throws Exception {
            // 準備：2 個入れた後に、バリアントを販売停止にする
            var product = inCart(2);
            product.suspend(0);

            // 操作と確認：削除でき、カートは空になる
            remove(cartId, product.variantIds.getFirst()).andExpect(status().isNoContent());
            assertThat(items()).isEmpty();
        }

        @Test
        @DisplayName("AC-78 入れた後に販売停止になったバリアントの品目も、販売可能数の範囲で増やせる（増量は商品カタログを見ない）")
        void increasesSuspendedWithinSellable() throws Exception {
            // 準備：2 個入れた後に、バリアントを販売停止にする
            var product = inCart(2);
            product.suspend(0);
            var variant = product.variantIds.getFirst();

            // 操作と確認：在庫 3 の範囲の 3 個に増やせる
            change(cartId, variant, 3).andExpect(status().isNoContent());
            assertThat(items()).containsExactly(Map.entry(variant, 3));
        }
    }

    @Nested
    @DisplayName("期限")
    class Expiry {

        @Test
        @DisplayName("AC-81 最後の変更から 30 日たったカートに入れると、古い品目は消える")
        void dropsExpiredItems() throws Exception {
            // 準備：古い品目を入れてから 30 日たつ
            var old = stockedVariant(3);
            var fresh = stockedVariant(3);
            // 商品の公開で時計が進むので、カートを変えた時刻から数える
            var changedAt = clock.instant();
            add(cartId, old, 1).andExpect(status().isNoContent());
            clock.setInstant(changedAt.plus(Duration.ofDays(30)));

            // 操作：新しい品目を入れる
            add(cartId, fresh, 1).andExpect(status().isNoContent());

            // 確認：新しい品目だけが残る
            assertThat(items()).containsExactly(Map.entry(fresh, 1));
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 選べないバリアントとして拒否され、カートが空のままであることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>入れるリクエストが 400</li>
     *   <li>エラーコードが {@code VARIANT_NOT_SELECTABLE}</li>
     *   <li>カートが空のまま</li>
     * </ul>
     *
     * @param variantId 入れるバリアントの ID
     */
    private void assertNotSelectable(UUID variantId) throws Exception {
        add(cartId, variantId, 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VARIANT_NOT_SELECTABLE"));
        assertThat(items()).isEmpty();
    }

    // ============================== 準備の補助 ==============================

    /**
     * ログインせずにカートを作る。
     *
     * @return カート ID
     */
    private UUID newCart() throws Exception {
        var body = mockMvc.perform(post(BASE))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return TestJson.createdId(body);
    }

    /**
     * カートに入れるリクエストを送る。
     *
     * @param cart      カート ID
     * @param variantId バリアント ID
     * @param quantity  数量
     * @return 応答
     */
    private ResultActions add(UUID cart, UUID variantId, int quantity) throws Exception {
        return mockMvc.perform(post(BASE + "/" + cart + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"variantId": "%s", "quantity": %d}""".formatted(variantId, quantity)));
    }

    /**
     * 品目の数量を変えるリクエストを送る。
     *
     * @param cart      カート ID
     * @param variantId バリアント ID
     * @param quantity  新しい数量
     * @return 応答
     */
    private ResultActions change(UUID cart, UUID variantId, int quantity) throws Exception {
        return mockMvc.perform(put(BASE + "/" + cart + "/items/" + variantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"quantity": %d}""".formatted(quantity)));
    }

    /**
     * 品目を削除するリクエストを送る。
     *
     * @param cart      カート ID
     * @param variantId バリアント ID
     * @return 応答
     */
    private ResultActions remove(UUID cart, UUID variantId) throws Exception {
        return mockMvc.perform(delete(BASE + "/" + cart + "/items/" + variantId));
    }

    /**
     * 公開した商品のバリアントを作り、在庫数を設定する。
     *
     * @param quantity 在庫数
     * @return バリアント ID
     */
    private UUID stockedVariant(int quantity) throws Exception {
        return inventory.stockedVariant(catalog, standard, "商品", quantity);
    }

    /**
     * 在庫のある、指定した数のバリアントを作る。
     *
     * <p>色の軸を持つ 1 つの商品に、指定した数のバリアントを登録して公開し、それぞれに在庫を 1 つ置く。
     *
     * @param count バリアントの数
     * @return バリアント ID（登録した順）
     */
    private List<UUID> stockedVariants(int count) throws Exception {
        var product = catalog.product(standard, "商品", 1000, "色");
        for (int i = 0; i < count; i++) {
            product.variant("色 " + i);
        }
        product.publish();
        product.variantIds.forEach(variant -> inventory.stock(variant, 1));
        return List.copyOf(product.variantIds);
    }

    /**
     * 在庫が 3 の、公開した商品のバリアントを、指定した数量でテストのカートに入れる。
     *
     * @param quantity 入れる数量
     * @return 商品（バリアントは 1 つ）
     */
    private CatalogTestData.TestProduct inCart(int quantity) throws Exception {
        var product = catalog.product(standard, "商品", 1000, "色").variant("白").publish();
        inventory.stock(product.variantIds.getFirst(), 3);
        add(cartId, product.variantIds.getFirst(), quantity).andExpect(status().isNoContent());
        return product;
    }

    /**
     * テストのカートの品目（入れた順）を DB から読む。
     *
     * @return バリアント ID と数量の組
     */
    private List<Map.Entry<UUID, Integer>> items() {
        return dsl.select(CART_ITEM.VARIANT_ID, CART_ITEM.QUANTITY)
                .from(CART_ITEM)
                .where(CART_ITEM.CART_ID.eq(cartId))
                .orderBy(CART_ITEM.SEQUENCE_NO)
                .fetch(row -> Map.entry(row.get(CART_ITEM.VARIANT_ID), row.get(CART_ITEM.QUANTITY)));
    }
}
