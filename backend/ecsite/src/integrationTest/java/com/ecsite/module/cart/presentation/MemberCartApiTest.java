package com.ecsite.module.cart.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestJson;
import com.ecsite.TestTokens;
import com.ecsite.module.cart.event.CartChanged;
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
 * 会員のカートの受け入れテスト
 *
 * <p>会員のカートの取得と持ち主の確認、ログインせずに作ったカートの扱い、カートを移す操作を、API を通して確認する。
 * 同時の最初のリクエストは {@code MemberCartConcurrencyTest}、移すときの合算・切り詰め・品目の数の判定そのものは
 * {@code CartMergeTest} が確認する。
 *
 * <p>会員の token はテストの鍵で作る（{@code TestTokens}）。テストごとにロールバックする。
 */
@IntegrationTest
@RecordApplicationEvents
@Transactional
class MemberCartApiTest {

    // ============================== 定数 ==============================

    /**
     * カートの API のパス
     */
    private static final String CARTS = "/api/store/carts";

    /**
     * 会員のカートの API のパス
     */
    private static final String MEMBER_CART = "/api/store/me/cart";

    /**
     * カートを移す API のパス
     */
    private static final String MERGE = "/api/store/me/cart/merge";

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 1 つのカートに入れられる品目の数の上限
     */
    private static final int ITEM_LIMIT = 30;

    /**
     * 持ち主でない人の呼び名：ほかの会員
     */
    private static final String OTHER_MEMBER = "ほかの会員";

    /**
     * 持ち主でない人の呼び名：ログインしていない人
     */
    private static final String ANONYMOUS = "ログインしていない人";

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
     * 在庫のリポジトリ
     */
    @Autowired
    StockItemRepository stockItemRepository;

    /**
     * 発行されたイベント
     */
    @Autowired
    ApplicationEvents events;

    /**
     * DB を読み書きする jOOQ
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
     * 会員（持ち主）
     */
    private final TestTokens.Builder owner = TestTokens.member().subject(UUID.randomUUID());

    /**
     * ほかの会員
     */
    private final TestTokens.Builder other = TestTokens.member().subject(UUID.randomUUID());

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        catalog = new CatalogTestData(mockMvc, clock);
        standard = catalog.taxCategory("標準", "10");
        // カートの表示（見積もり）は、送料の税区分を使う
        ShippingTaxCategory.ensure(dsl);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("会員のカート")
    class MemberCart {

        @Test
        @DisplayName("AC-121 初めて求めると、空のカートが作られる")
        void createsEmptyCartFirst() throws Exception {
            // 操作：初めて会員のカートを求める
            var cartId = memberCart(owner);

            // 確認：持ち主が読むと、品目のないカート
            mockMvc.perform(get(CARTS + "/" + cartId).with(owner.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(0)));
        }

        @Test
        @DisplayName("AC-121 2 回目に求めると、同じカートが返る")
        void returnsSameCartSecondTime() throws Exception {
            // 準備：一度求めた会員のカート
            var first = memberCart(owner);

            // 操作：もう一度求める
            var second = memberCart(owner);

            // 確認：同じカート
            assertThat(second).isEqualTo(first);
        }

        @Test
        @DisplayName("AC-121 会員ごとに別のカートになる")
        void differentMembersHaveDifferentCarts() throws Exception {
            // 操作と確認：持ち主とほかの会員のカートが違う
            assertThat(memberCart(owner)).isNotEqualTo(memberCart(other));
        }

        @Test
        @DisplayName("AC-137 token がなければ、会員のカートを求められない")
        void requiresToken() throws Exception {
            // 操作と確認：ログインが必要というエラー
            mockMvc.perform(get(MEMBER_CART).with(TestTokens.none()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        }

        @Test
        @DisplayName("AC-121 持ち主は、会員のカートに入れて、表示できる")
        void ownerCanUseCart() throws Exception {
            // 準備：持ち主の会員のカートと、在庫のあるバリアント
            var cartId = memberCart(owner);
            var variant = stockedVariant(5);

            // 操作：持ち主が入れる
            add(cartId, variant, 2, owner.bearer()).andExpect(status().isNoContent());

            // 確認：持ち主が読むと、品目が 1 つある
            mockMvc.perform(get(CARTS + "/" + cartId).with(owner.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("持ち主でない人による会員のカートの操作")
    class NotOwner {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {OTHER_MEMBER, ANONYMOUS})
        @DisplayName("AC-121 持ち主でない人には、会員のカートは見つからない")
        void cannotRead(String who) throws Exception {
            // 準備：持ち主が 1 個入れた会員のカート
            var cartId = memberCart(owner);
            addAsOwner(cartId, stockedVariant(5));

            // 操作と確認：見つからない
            mockMvc.perform(get(CARTS + "/" + cartId).with(requester(who)))
                    .andExpect(status().isNotFound());
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {OTHER_MEMBER, ANONYMOUS})
        @DisplayName("AC-121 持ち主でない人は、会員のカートに入れられない")
        void cannotAdd(String who) throws Exception {
            // 準備：持ち主が 1 個入れた会員のカート
            var cartId = memberCart(owner);
            var variant = stockedVariant(5);
            addAsOwner(cartId, variant);

            // 操作：持ち主でない人が入れる
            var result = add(cartId, variant, 1, requester(who));

            // 確認：見つからず、品目は変わらない
            result.andExpect(status().isNotFound());
            assertThat(items(cartId)).containsExactly(Map.entry(variant, 1));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {OTHER_MEMBER, ANONYMOUS})
        @DisplayName("AC-121 持ち主でない人は、会員のカートの数量を変えられない")
        void cannotChangeQuantity(String who) throws Exception {
            // 準備：持ち主が 1 個入れた会員のカート
            var cartId = memberCart(owner);
            var variant = stockedVariant(5);
            addAsOwner(cartId, variant);

            // 操作：持ち主でない人が 3 個に変える
            var result = mockMvc.perform(put(CARTS + "/" + cartId + "/items/" + variant).with(requester(who))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"quantity\": 3}"));

            // 確認：見つからず、品目は変わらない
            result.andExpect(status().isNotFound());
            assertThat(items(cartId)).containsExactly(Map.entry(variant, 1));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {OTHER_MEMBER, ANONYMOUS})
        @DisplayName("AC-121 持ち主でない人は、会員のカートの品目を削除できない")
        void cannotRemove(String who) throws Exception {
            // 準備：持ち主が 1 個入れた会員のカート
            var cartId = memberCart(owner);
            var variant = stockedVariant(5);
            addAsOwner(cartId, variant);

            // 操作：持ち主でない人が削除する
            var result = mockMvc.perform(delete(CARTS + "/" + cartId + "/items/" + variant).with(requester(who)));

            // 確認：見つからず、品目は変わらない
            result.andExpect(status().isNotFound());
            assertThat(items(cartId)).containsExactly(Map.entry(variant, 1));
        }
    }

    @Nested
    @DisplayName("ログインせずに作ったカート")
    class GuestCart {

        @Test
        @DisplayName("AC-120 会員を持たないカートは、ログインしていなくても、カート ID で入れて表示できる")
        void usableWithoutToken() throws Exception {
            // 準備：ログインせずに作ったカートと、在庫のあるバリアント
            var cartId = guestCart();
            var variant = stockedVariant(5);

            // 操作：token なしで入れる
            add(cartId, variant, 1, TestTokens.none()).andExpect(status().isNoContent());

            // 確認：token なしで読むと品目が 1 つあり、DB にも入っている
            mockMvc.perform(get(CARTS + "/" + cartId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(1)));
            assertThat(items(cartId)).containsExactly(Map.entry(variant, 1));
        }

        @Test
        @DisplayName("AC-120 会員を持たないカートには、会員の token があっても入れられる")
        void usableWithToken() throws Exception {
            // 準備：token なしで 1 個入れたカート
            var cartId = guestCart();
            var variant = stockedVariant(5);
            add(cartId, variant, 1, TestTokens.none()).andExpect(status().isNoContent());

            // 操作：会員の token で、同じバリアントを 1 個入れる
            var result = add(cartId, variant, 1, owner.bearer());

            // 確認：受け付けられ、合算される
            result.andExpect(status().isNoContent());
            assertThat(items(cartId)).containsExactly(Map.entry(variant, 2));
        }
    }

    @Nested
    @DisplayName("カートを移す")
    class Merge {

        @Test
        @DisplayName("AC-122 上限に当たらなければ、会員のカートの ID と、切り詰め・移さなかった品目のない結果を返す")
        void returnsMergeResult() throws Exception {
            // 準備：会員のカートと、白と黒を入れたログインせずに作ったカート
            var carts = cartsToMerge();

            // 操作：移す
            var result = merge(carts.guestCartId(), owner.bearer());

            // 確認：会員のカートの ID を返し、切り詰めた品目も移さなかった品目もない
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.cartId").value(carts.memberCartId().toString()))
                    .andExpect(jsonPath("$.truncatedItems", hasSize(0)))
                    .andExpect(jsonPath("$.skippedItems", hasSize(0)));
        }

        @Test
        @DisplayName("AC-122 品目が会員のカートの後ろに移り、同じバリアントは数量を合算する")
        void movesItemsIntoMemberCart() throws Exception {
            // 準備：白 2 の会員のカートと、黒 1・白 3 のログインせずに作ったカート
            var carts = cartsToMerge();

            // 操作：移す
            merge(carts.guestCartId(), owner.bearer()).andExpect(status().isOk());

            // 確認：白は 2 + 3 に合算され、黒は後ろに加わる
            assertThat(items(carts.memberCartId()))
                    .containsExactly(Map.entry(carts.white(), 5), Map.entry(carts.black(), 1));
        }

        @Test
        @DisplayName("AC-122 移した後、移した元のカートは空になる")
        void emptiesSourceCart() throws Exception {
            // 準備：品目のある、ログインせずに作ったカート
            var carts = cartsToMerge();

            // 操作：移す
            merge(carts.guestCartId(), owner.bearer()).andExpect(status().isOk());

            // 確認：移した元のカートは空
            assertThat(items(carts.guestCartId())).isEmpty();
        }

        @Test
        @DisplayName("移すと、会員のカートと移した元のカートの両方に、変更のイベントが発行される")
        void publishesCartChangedForBothCarts() throws Exception {
            // 準備：品目のある、ログインせずに作ったカート
            var carts = cartsToMerge();

            // 操作：移す
            merge(carts.guestCartId(), owner.bearer()).andExpect(status().isOk());

            // 確認：両方のカートの変更のイベントがある
            assertThat(events.stream(CartChanged.class)).extracting(CartChanged::cartId)
                    .contains(carts.memberCartId(), carts.guestCartId());
        }

        @Test
        @DisplayName("AC-122 同じカートをもう一度移しても、会員のカートの品目は変わらない")
        void secondMergeKeepsItems() throws Exception {
            // 準備：一度移した後
            var memberCartId = memberCart(owner);
            var guestCartId = guestCart();
            add(guestCartId, stockedVariant(5), 2, TestTokens.none()).andExpect(status().isNoContent());
            merge(guestCartId, owner.bearer()).andExpect(status().isOk());
            var afterFirst = items(memberCartId);

            // 操作：もう一度移す
            merge(guestCartId, owner.bearer()).andExpect(status().isOk());

            // 確認：1 回目の後と同じ
            assertThat(items(memberCartId)).isEqualTo(afterFirst);
        }

        @Test
        @DisplayName("AC-122 同じカートをもう一度移しても、変更のイベントは発行されない")
        void secondMergePublishesNothing() throws Exception {
            // 準備：一度移した後
            var guestCartId = guestCart();
            add(guestCartId, stockedVariant(5), 2, TestTokens.none()).andExpect(status().isNoContent());
            merge(guestCartId, owner.bearer()).andExpect(status().isOk());
            var eventsAfterFirst = events.stream(CartChanged.class).count();

            // 操作：もう一度移す
            merge(guestCartId, owner.bearer()).andExpect(status().isOk());

            // 確認：イベントの数が増えない
            assertThat(events.stream(CartChanged.class).count()).isEqualTo(eventsAfterFirst);
        }

        @Test
        @DisplayName("AC-124 移した後も会員のカートは残り、次に求めると、同じカートを品目と共に使える")
        void memberCartRemainsForNextLogin() throws Exception {
            // 準備：品目を移した会員のカート
            var carts = cartsToMerge();
            merge(carts.guestCartId(), owner.bearer()).andExpect(status().isOk());

            // 操作：次のログインで、会員のカートを求める
            var next = memberCart(owner);

            // 確認：同じカートで、移した 2 品目がある
            assertThat(next).isEqualTo(carts.memberCartId());
            mockMvc.perform(get(CARTS + "/" + next).with(owner.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(2)));
        }

        @Test
        @DisplayName("会員のカートを、自分の会員のカートから移すことはできない")
        void rejectsOwnMemberCartAsSource() throws Exception {
            // 準備：自分の会員のカート
            var ownCart = memberCart(owner);

            // 操作と確認：見つからない
            merge(ownCart, owner.bearer()).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("ほかの会員のカートから移すことはできない")
        void rejectsOthersMemberCartAsSource() throws Exception {
            // 準備：ほかの会員のカート
            var othersCart = memberCart(other);

            // 操作と確認：見つからない
            merge(othersCart, owner.bearer()).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("存在しないカートから移すことはできない")
        void rejectsUnknownSource() throws Exception {
            // 操作と確認：見つからない
            merge(UUID.randomUUID(), owner.bearer()).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-137 token がなければ、カートを移せない")
        void requiresToken() throws Exception {
            // 操作と確認：ログインが必要というエラー
            merge(guestCart(), TestTokens.none())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        }
    }

    @Nested
    @DisplayName("移すときの上限と、商品・在庫")
    class MergeLimits {

        @Test
        @DisplayName("AC-123 合算して 10 を超える品目は 10 に切り詰めて移し、切り詰めた品目として返す")
        void truncatesQuantityOverLimit() throws Exception {
            // 準備：8 個入れた会員のカートと、同じバリアントを 5 個入れたログインせずに作ったカート
            var memberCartId = memberCart(owner);
            var variant = stockedVariant(20);
            add(memberCartId, variant, 8, owner.bearer()).andExpect(status().isNoContent());
            var guestCartId = guestCart();
            add(guestCartId, variant, 5, TestTokens.none()).andExpect(status().isNoContent());

            // 操作：移す
            var result = merge(guestCartId, owner.bearer());

            // 確認：切り詰めた品目（切り詰めた後の数量）を返し、会員のカートは 10 個
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.truncatedItems[0].variantId").value(variant.toString()))
                    .andExpect(jsonPath("$.truncatedItems[0].quantity").value(10));
            assertThat(items(memberCartId)).containsExactly(Map.entry(variant, 10));
        }

        @Test
        @DisplayName("AC-123 商品を確認せずに移す（存在しないバリアントも移る）")
        void doesNotVerifyCatalog() throws Exception {
            // 準備：存在しないバリアントの品目を持つ、ログインせずに作ったカート
            var memberCartId = memberCart(owner);
            var guestCartId = guestCart();
            var unknown = UUID.randomUUID();
            // 存在しないバリアントは、カートの API では入れられないので、DB に直接入れる
            insertItem(guestCartId, unknown, 1);

            // 操作：移す
            merge(guestCartId, owner.bearer()).andExpect(status().isOk());

            // 確認：存在しないバリアントも、会員のカートに移っている
            assertThat(items(memberCartId)).containsExactly(Map.entry(unknown, 1));
        }

        @Test
        @DisplayName("AC-123 販売可能数を確認せずに移し、足りないことはカートの表示で示す")
        void doesNotVerifyStockButShowsIt() throws Exception {
            // 準備：在庫 3 のバリアントを、会員のカートに 2、ログインせずに作ったカートに 2 入れる
            var memberCartId = memberCart(owner);
            var variant = stockedVariant(3);
            add(memberCartId, variant, 2, owner.bearer()).andExpect(status().isNoContent());
            var guestCartId = guestCart();
            add(guestCartId, variant, 2, TestTokens.none()).andExpect(status().isNoContent());

            // 操作：移す（合算で 4、販売可能数は 3）
            merge(guestCartId, owner.bearer()).andExpect(status().isOk());

            // 確認：4 個で移り、表示では在庫が足りないので購入できないと示す
            assertThat(items(memberCartId)).containsExactly(Map.entry(variant, 4));
            mockMvc.perform(get(CARTS + "/" + memberCartId).with(owner.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].purchasable").value(false))
                    .andExpect(jsonPath("$.items[0].unpurchasableReason").value("INSUFFICIENT_STOCK"));
        }

        @Test
        @DisplayName("AC-123 会員のカートが 30 品目なら、別のバリアントを移さなかった品目として返す")
        void reportsSkippedItemsOverItemLimit() throws Exception {
            // 準備：30 品目の会員のカートと、別のバリアントを 4 個入れたログインせずに作ったカート
            memberCartWithFullItems();
            var extra = stockedVariant(20);
            var guestCartId = guestCart();
            add(guestCartId, extra, 4, TestTokens.none()).andExpect(status().isNoContent());

            // 操作：移す
            var result = merge(guestCartId, owner.bearer());

            // 確認：切り詰めはなく、移さなかった品目として 4 個を返す
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.truncatedItems", hasSize(0)))
                    .andExpect(jsonPath("$.skippedItems", hasSize(1)))
                    .andExpect(jsonPath("$.skippedItems[0].variantId").value(extra.toString()))
                    .andExpect(jsonPath("$.skippedItems[0].quantity").value(4));
        }

        @Test
        @DisplayName("AC-123 会員のカートが 30 品目なら、別のバリアントは会員のカートに移らない")
        void skipsItemsOverItemLimit() throws Exception {
            // 準備：30 品目の会員のカートと、別のバリアントを入れたログインせずに作ったカート
            var memberCartId = memberCartWithFullItems();
            var extra = stockedVariant(20);
            var guestCartId = guestCart();
            add(guestCartId, extra, 4, TestTokens.none()).andExpect(status().isNoContent());

            // 操作：移す
            merge(guestCartId, owner.bearer()).andExpect(status().isOk());

            // 確認：会員のカートは 30 品目のままで、別のバリアントはない
            assertThat(items(memberCartId)).hasSize(ITEM_LIMIT).noneMatch(item -> item.getKey().equals(extra));
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 移す前の 2 つのカート
     *
     * @param memberCartId 会員のカートの ID（白 2 個）
     * @param guestCartId  ログインせずに作ったカートの ID（黒 1 個、白 3 個の順）
     * @param white        白のバリアント ID
     * @param black        黒のバリアント ID
     */
    private record CartsToMerge(UUID memberCartId, UUID guestCartId, UUID white, UUID black) {
    }

    /**
     * 会員のカートに白を 2 個、ログインせずに作ったカートに黒を 1 個・白を 3 個入れる。
     *
     * @return 2 つのカートとバリアント
     */
    private CartsToMerge cartsToMerge() throws Exception {
        var memberCartId = memberCart(owner);
        var white = stockedVariant(20);
        var black = stockedVariant(20);
        add(memberCartId, white, 2, owner.bearer()).andExpect(status().isNoContent());
        var guestCartId = guestCart();
        add(guestCartId, black, 1, TestTokens.none()).andExpect(status().isNoContent());
        add(guestCartId, white, 3, TestTokens.none()).andExpect(status().isNoContent());
        return new CartsToMerge(memberCartId, guestCartId, white, black);
    }

    /**
     * 品目の数が上限（30）の、持ち主の会員のカートを作る。
     *
     * <p>品目のバリアントは、商品カタログにないものを DB に直接入れる。
     *
     * @return 会員のカートの ID
     */
    private UUID memberCartWithFullItems() throws Exception {
        var memberCartId = memberCart(owner);
        for (int i = 0; i < ITEM_LIMIT; i++) {
            insertItem(memberCartId, UUID.randomUUID(), 1);
        }
        return memberCartId;
    }

    /**
     * 品目を DB に直接入れる（カートの API では入れられないバリアントに使う）。
     *
     * @param cartId    カート ID
     * @param variantId バリアント ID
     * @param quantity  数量
     */
    private void insertItem(UUID cartId, UUID variantId, int quantity) {
        dsl.insertInto(CART_ITEM)
                .set(CART_ITEM.CART_ID, cartId)
                .set(CART_ITEM.VARIANT_ID, variantId)
                .set(CART_ITEM.QUANTITY, quantity)
                .set(CART_ITEM.ADDED_AT, NOW)
                .execute();
    }

    /**
     * 会員のカートの ID を求める。
     *
     * @param member 会員
     * @return カート ID
     */
    private UUID memberCart(TestTokens.Builder member) throws Exception {
        var body = mockMvc.perform(get(MEMBER_CART).with(member.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return TestJson.uuid(body, "$.cartId");
    }

    /**
     * ログインせずにカートを作る。
     *
     * @return カート ID
     */
    private UUID guestCart() throws Exception {
        var body = mockMvc.perform(post(CARTS))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return TestJson.createdId(body);
    }

    /**
     * カートに入れるリクエストを送る。
     *
     * @param cartId    カート ID
     * @param variantId バリアント ID
     * @param quantity  数量
     * @param requester リクエストする人
     * @return 応答
     */
    private ResultActions add(UUID cartId, UUID variantId, int quantity, RequestPostProcessor requester) throws Exception {
        return mockMvc.perform(post(CARTS + "/" + cartId + "/items").with(requester)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"variantId": "%s", "quantity": %d}""".formatted(variantId, quantity)));
    }

    /**
     * 持ち主として 1 個入れる。
     *
     * @param cartId    会員のカートの ID
     * @param variantId バリアント ID
     */
    private void addAsOwner(UUID cartId, UUID variantId) throws Exception {
        add(cartId, variantId, 1, owner.bearer()).andExpect(status().isNoContent());
    }

    /**
     * カートを移すリクエストを送る。
     *
     * @param sourceCartId 移す元のカートの ID
     * @param requester    リクエストする人
     * @return 応答
     */
    private ResultActions merge(UUID sourceCartId, RequestPostProcessor requester) throws Exception {
        return mockMvc.perform(post(MERGE).with(requester)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"cartId": "%s"}""".formatted(sourceCartId)));
    }

    /**
     * 持ち主でない人の呼び名から、リクエストする人を返す。
     *
     * @param who {@link #OTHER_MEMBER} か {@link #ANONYMOUS}
     * @return リクエストする人
     */
    private RequestPostProcessor requester(String who) {
        return switch (who) {
            case OTHER_MEMBER -> other.bearer();
            case ANONYMOUS -> TestTokens.none();
            default -> throw new IllegalArgumentException(who);
        };
    }

    /**
     * 在庫のある、選べるバリアントを作る。
     *
     * @param quantity 在庫数
     * @return バリアント ID
     */
    private UUID stockedVariant(int quantity) throws Exception {
        return inventory.stockedVariant(catalog, standard, "商品", quantity);
    }

    /**
     * カートの品目（入れた順）を DB から読む。
     *
     * @param cartId カート ID
     * @return バリアント ID と数量の組
     */
    private List<Map.Entry<UUID, Integer>> items(UUID cartId) {
        return dsl.select(CART_ITEM.VARIANT_ID, CART_ITEM.QUANTITY)
                .from(CART_ITEM)
                .where(CART_ITEM.CART_ID.eq(cartId))
                .orderBy(CART_ITEM.SEQUENCE_NO)
                .fetch(row -> Map.entry(row.get(CART_ITEM.VARIANT_ID), row.get(CART_ITEM.QUANTITY)));
    }
}
