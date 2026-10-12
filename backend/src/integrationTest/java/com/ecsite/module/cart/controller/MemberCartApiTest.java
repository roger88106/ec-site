package com.ecsite.module.cart.controller;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestClockConfig;
import com.ecsite.TestTokens;
import com.ecsite.module.cart.event.CartChanged;
import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.StockAdjustmentReason;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.jooq.cart.Tables.CART_ITEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会員のカートの受け入れテスト（T3308、spec 003 US-25、docs/modules/cart.md）。<br />
 * 会員の token はテストの鍵で作る（TestTokens）。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@RecordApplicationEvents
@Transactional
class MemberCartApiTest {

    private static final String CARTS = "/api/store/carts";
    private static final String MEMBER_CART = "/api/store/me/cart";
    private static final String MERGE = "/api/store/me/cart/merge";

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

    @Autowired
    ApplicationEvents events;

    @Autowired
    DSLContext dsl;

    private CatalogTestData data;

    private UUID standard;

    /**
     * 会員（持ち主）
     */
    private final TestTokens.Builder owner = TestTokens.member().subject(UUID.randomUUID());

    /**
     * ほかの会員
     */
    private final TestTokens.Builder other = TestTokens.member().subject(UUID.randomUUID());

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        data = new CatalogTestData(mockMvc, clock);
        standard = data.taxCategory("標準", "10");
        // カートの表示（見積もり）は、送料の税区分を使う
        ShippingTaxCategory.ensure(dsl);
    }

    @Nested
    @DisplayName("AC-121 会員のカート")
    class MemberCart {

        @Test
        @DisplayName("AC-121 初めてなら空のカートを作り、2 回目は同じカートを返す")
        void createsOnceAndReturnsSameCart() throws Exception {
            var first = memberCart(owner);
            var second = memberCart(owner);

            assertThat(second).isEqualTo(first);
            mockMvc.perform(get(CARTS + "/" + first).with(owner.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(0)));
        }

        @Test
        @DisplayName("AC-121 会員ごとに別のカートになる")
        void differentMembersHaveDifferentCarts() throws Exception {
            assertThat(memberCart(owner)).isNotEqualTo(memberCart(other));
        }

        @Test
        @DisplayName("AC-137 token がなければ 401")
        void requiresToken() throws Exception {
            mockMvc.perform(get(MEMBER_CART).with(TestTokens.none()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        }

        @Test
        @DisplayName("AC-121 持ち主は、会員のカートに入れて、表示できる")
        void ownerCanUseCart() throws Exception {
            var cartId = memberCart(owner);
            var variant = stockedVariant(5);

            add(cartId, variant, 2, owner.bearer()).andExpect(status().isNoContent());

            mockMvc.perform(get(CARTS + "/" + cartId).with(owner.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(1)));
        }

        @Test
        @DisplayName("AC-121 会員のカートを、ほかの会員やログインしていない人が読み書きすると 404")
        void othersCannotUseMemberCart() throws Exception {
            var cartId = memberCart(owner);
            var variant = stockedVariant(5);
            add(cartId, variant, 1, owner.bearer()).andExpect(status().isNoContent());

            for (var requester : List.of(other.bearer(), TestTokens.none())) {
                mockMvc.perform(get(CARTS + "/" + cartId).with(requester))
                        .andExpect(status().isNotFound());
                add(cartId, variant, 1, requester)
                        .andExpect(status().isNotFound());
                mockMvc.perform(put(CARTS + "/" + cartId + "/items/" + variant).with(requester)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\": 3}"))
                        .andExpect(status().isNotFound());
                mockMvc.perform(delete(CARTS + "/" + cartId + "/items/" + variant).with(requester))
                        .andExpect(status().isNotFound());
            }
            assertThat(items(cartId)).containsExactly(Map.entry(variant, 1));
        }
    }

    @Nested
    @DisplayName("AC-120 ログインせずに作ったカート")
    class GuestCart {

        @Test
        @DisplayName("AC-120 会員を持たないカートは、今までどおりカート ID で使える（token があってもなくても）")
        void guestCartIsUsableById() throws Exception {
            var cartId = guestCart();
            var variant = stockedVariant(5);

            add(cartId, variant, 1, TestTokens.none()).andExpect(status().isNoContent());
            add(cartId, variant, 1, owner.bearer()).andExpect(status().isNoContent());
            mockMvc.perform(get(CARTS + "/" + cartId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(1)));

            assertThat(items(cartId)).containsExactly(Map.entry(variant, 2));
        }
    }

    @Nested
    @DisplayName("AC-122、123、124 カートを移す")
    class Merge {

        @Test
        @DisplayName("AC-122 品目が会員のカートに移り、同じバリアントは合算され、移したカートは空になる。どちらのカートも変更のイベントが出る")
        void movesItems() throws Exception {
            var memberCartId = memberCart(owner);
            var white = stockedVariant(20);
            var black = stockedVariant(20);
            add(memberCartId, white, 2, owner.bearer()).andExpect(status().isNoContent());
            var guestCartId = guestCart();
            add(guestCartId, black, 1, TestTokens.none()).andExpect(status().isNoContent());
            add(guestCartId, white, 3, TestTokens.none()).andExpect(status().isNoContent());

            merge(guestCartId, owner.bearer())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cartId").value(memberCartId.toString()))
                    .andExpect(jsonPath("$.truncatedItems", hasSize(0)))
                    .andExpect(jsonPath("$.skippedItems", hasSize(0)));

            assertThat(items(memberCartId)).containsExactly(Map.entry(white, 5), Map.entry(black, 1));
            assertThat(items(guestCartId)).isEmpty();
            assertThat(events.stream(CartChanged.class)).extracting(CartChanged::cartId)
                    .contains(memberCartId, guestCartId);
        }

        @Test
        @DisplayName("AC-122 同じカートを 2 回移しても、2 回目は何も変わらない")
        void mergingTwiceChangesNothing() throws Exception {
            var memberCartId = memberCart(owner);
            var guestCartId = guestCart();
            add(guestCartId, stockedVariant(5), 2, TestTokens.none()).andExpect(status().isNoContent());
            merge(guestCartId, owner.bearer()).andExpect(status().isOk());
            var afterFirst = items(memberCartId);
            var eventsAfterFirst = events.stream(CartChanged.class).count();

            merge(guestCartId, owner.bearer()).andExpect(status().isOk());

            assertThat(items(memberCartId)).isEqualTo(afterFirst);
            assertThat(events.stream(CartChanged.class).count()).isEqualTo(eventsAfterFirst);
        }

        @Test
        @DisplayName("AC-123 商品・在庫は確かめずに移し、切り詰めた品目を返す")
        void doesNotVerifyCatalogOrStock() throws Exception {
            var memberCartId = memberCart(owner);
            var unknown = UUID.randomUUID();
            var stocked = stockedVariant(20);
            add(memberCartId, stocked, 8, owner.bearer()).andExpect(status().isNoContent());
            var guestCartId = guestCart();
            add(guestCartId, stocked, 5, TestTokens.none()).andExpect(status().isNoContent());
            // 存在しないバリアントは、カートの API では入れられないので、DB に直接入れる
            dsl.insertInto(CART_ITEM)
                    .set(CART_ITEM.CART_ID, guestCartId)
                    .set(CART_ITEM.VARIANT_ID, unknown)
                    .set(CART_ITEM.QUANTITY, 1)
                    .set(CART_ITEM.ADDED_AT, NOW)
                    .execute();

            merge(guestCartId, owner.bearer())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.truncatedItems[0].variantId").value(stocked.toString()))
                    .andExpect(jsonPath("$.truncatedItems[0].quantity").value(10));

            assertThat(items(memberCartId)).containsExactly(Map.entry(stocked, 10), Map.entry(unknown, 1));
        }

        @Test
        @DisplayName("会員のカートは、移す元に指定できない（自分のもの・ほかの会員のものとも 404）。ないカートも 404")
        void rejectsMemberCartAsSource() throws Exception {
            var ownCart = memberCart(owner);
            var othersCart = memberCart(other);

            merge(ownCart, owner.bearer()).andExpect(status().isNotFound());
            merge(othersCart, owner.bearer()).andExpect(status().isNotFound());
            merge(UUID.randomUUID(), owner.bearer()).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-137 token がなければ 401")
        void requiresToken() throws Exception {
            merge(guestCart(), TestTokens.none())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        }
    }

    // ============================== 補助 ==============================

    /**
     * 会員のカートの ID を求める
     */
    private UUID memberCart(TestTokens.Builder member) throws Exception {
        var body = mockMvc.perform(get(MEMBER_CART).with(member.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.cartId"));
    }

    /**
     * ログインせずにカートを作る
     */
    private UUID guestCart() throws Exception {
        var body = mockMvc.perform(post(CARTS))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    /**
     * カートに入れる要求を送る
     */
    private ResultActions add(UUID cartId, UUID variantId, int quantity, RequestPostProcessor requester) throws Exception {
        return mockMvc.perform(post(CARTS + "/" + cartId + "/items").with(requester)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"variantId": "%s", "quantity": %d}""".formatted(variantId, quantity)));
    }

    /**
     * カートを移す要求を送る
     */
    private ResultActions merge(UUID sourceCartId, RequestPostProcessor requester) throws Exception {
        return mockMvc.perform(post(MERGE).with(requester)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"cartId": "%s"}""".formatted(sourceCartId)));
    }

    /**
     * 在庫のある、選べるバリアントを作る
     */
    private UUID stockedVariant(int quantity) throws Exception {
        var variantId = data.product(standard, "商品", 1000).publish().variantIds.getFirst();
        stockItemRepository.createIfAbsent(variantId, UUID.randomUUID());
        stockItemRepository.adjust(variantId, StockAdjustment.create(quantity, StockAdjustmentReason.ARRIVAL, null, TestTokens.OPERATOR, NOW));
        return variantId;
    }

    /**
     * カートの品目（入れた順）を DB から読む
     */
    private List<Map.Entry<UUID, Integer>> items(UUID cartId) {
        return dsl.select(CART_ITEM.VARIANT_ID, CART_ITEM.QUANTITY)
                .from(CART_ITEM)
                .where(CART_ITEM.CART_ID.eq(cartId))
                .orderBy(CART_ITEM.SEQUENCE_NO)
                .fetch(row -> Map.entry(row.value1(), row.value2()));
    }
}
