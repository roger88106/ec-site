package com.ecsite.module.order.presentation;

import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.application.payment.OrderPayments;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import com.ecsite.support.data.InventoryTestData;
import com.ecsite.support.data.OrderTestData;
import com.ecsite.support.data.ShippingTaxCategory;
import com.ecsite.support.http.TestJson;
import com.ecsite.support.security.TestTokens;
import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.jooq.member.Tables.MEMBER_;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static com.ecsite.support.data.OrderTestData.orderBody;
import static com.ecsite.support.data.OrderTestData.startCheckoutRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会員の注文の受け入れテスト
 *
 * <p>会員だけが自分の注文手続きと注文を扱えること、注文が会員に結び付くこと、自分の注文の一覧と支払待ちの注文の一覧を確認する。
 * 認証の失敗の種類（期限切れ、署名の誤りなど）は {@code AuthorizationTest} が確認する。
 *
 * <p>会員の token はテストの鍵で作る（TestTokens）。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class MemberOrderApiTest {

    // ============================== 定数 ==============================

    /**
     * 店舗の API のパス
     */
    private static final String STORE = "/api/store";

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 商品名
     */
    private static final String PRODUCT_NAME = "T シャツ";

    /**
     * 持ち主のメールアドレス
     */
    private static final String OWNER_EMAIL = "owner@example.com";

    // ============================== Bean ==============================

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    StockItemRepository stockItemRepository;

    /**
     * 在庫を作る補助
     */
    private InventoryTestData inventory;

    @Autowired
    DSLContext dsl;

    /**
     * 注文を作る補助
     */
    private OrderTestData orders;

    /**
     * 商品のバリアント ID
     */
    private UUID variantId;

    /**
     * 持ち主の IdP のアカウントの ID
     */
    private final UUID ownerSubject = UUID.randomUUID();

    /**
     * 会員（持ち主）
     */
    private final TestTokens.Builder owner = TestTokens.member().subject(ownerSubject).email(OWNER_EMAIL);

    /**
     * ほかの会員
     */
    private final TestTokens.Builder other = TestTokens.member().subject(UUID.randomUUID()).email("other@example.com");

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        ShippingTaxCategory.ensure(dsl);
        var catalog = new CatalogTestData(mockMvc, clock);
        orders = new OrderTestData(mockMvc);
        var standard = catalog.taxCategory("標準", "10");
        variantId = catalog.product(standard, PRODUCT_NAME, 2700).publish().variantIds.getFirst();
        inventory.stock(variantId, 50);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("注文手続きの開始")
    class StartCheckout {

        @Test
        @DisplayName("AC-125 会員は、自分の会員のカートから注文手続きを始められる")
        void startsFromOwnMemberCart() throws Exception {
            // 準備：持ち主のカートに品目を入れる
            var cartId = memberCartWithItem(owner);

            // 操作と確認：201
            startCheckout(cartId, owner.bearer()).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("AC-125 token がなければ 401")
        void requiresToken() throws Exception {
            // 準備：持ち主のカートに品目を入れる
            var cartId = memberCartWithItem(owner);

            // 操作と確認：token なしでは 401 の UNAUTHENTICATED
            startCheckout(cartId, TestTokens.none())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        }

        @Test
        @DisplayName("AC-125 ログインせずに作ったカートからは始められない（404）")
        void rejectsGuestCart() throws Exception {
            // 準備：ログインせずにカートを作り、品目を入れる
            var cartId = TestJson.createdId(mockMvc.perform(post(STORE + "/carts").with(TestTokens.none()))
                    .andReturn().getResponse().getContentAsString());
            orders.add(cartId, variantId, 1, TestTokens.none());

            // 操作と確認：会員が始めようとすると 404
            startCheckout(cartId, owner.bearer()).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-126 ほかの会員のカートからは始められない（404）")
        void rejectsOthersCart() throws Exception {
            // 準備：持ち主のカートに品目を入れる
            var cartId = memberCartWithItem(owner);

            // 操作と確認：ほかの会員には 404
            startCheckout(cartId, other.bearer()).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("注文手続きの参照")
    class ViewCheckout {

        @Test
        @DisplayName("AC-126 注文手続きを始めた会員は、注文手続きを参照できる")
        void ownerCanView() throws Exception {
            // 準備：持ち主が注文手続きを始める
            var checkoutId = checkout(owner);

            // 操作と確認：200
            mockMvc.perform(get(STORE + "/checkouts/" + checkoutId).with(owner.bearer()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("AC-126 ほかの会員は、注文手続きを参照できない（404）")
        void otherCannotView() throws Exception {
            // 準備：持ち主が注文手続きを始める
            var checkoutId = checkout(owner);

            // 操作と確認：ほかの会員には 404
            mockMvc.perform(get(STORE + "/checkouts/" + checkoutId).with(other.bearer()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-126 token がなければ、注文手続きを参照できない（401）")
        void requiresToken() throws Exception {
            // 準備：持ち主が注文手続きを始める
            var checkoutId = checkout(owner);

            // 操作と確認：token なしでは 401
            mockMvc.perform(get(STORE + "/checkouts/" + checkoutId).with(TestTokens.none()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("注文")
    class PlaceOrder {

        @Test
        @DisplayName("AC-127 注文には、注文手続きで入力しない token のメールアドレスを写して残す")
        void copiesEmailFromToken() throws Exception {
            // 操作：メールアドレスを本文に含めずに注文する
            var orderId = placeOrder(owner);

            // 確認：注文のメールアドレスは token のもの
            mockMvc.perform(get(STORE + "/orders/" + orderId).with(owner.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(OWNER_EMAIL));
        }

        @Test
        @DisplayName("AC-128 注文は、注文した会員に結び付く")
        void linksOrderToMember() throws Exception {
            // 操作：持ち主が注文する
            var orderId = placeOrder(owner);

            // 確認：注文の会員 ID は、持ち主の IdP のアカウントの会員
            var ownerMemberId = dsl.select(MEMBER_.ID).from(MEMBER_)
                    .where(MEMBER_.IDP_SUBJECT.eq(ownerSubject.toString())).fetchOne(MEMBER_.ID);
            assertThat(ownerMemberId).isNotNull();
            assertThat(dsl.fetchValue(SALES_ORDER.MEMBER_ID, SALES_ORDER.ID.eq(orderId))).isEqualTo(ownerMemberId);
        }

        @Test
        @DisplayName("AC-126 ほかの会員は、注文手続きで注文できない（404）")
        void othersCannotPlaceOrder() throws Exception {
            // 準備：持ち主が注文手続きを始める
            var checkoutId = checkout(owner);

            // 操作：ほかの会員が注文する
            order(checkoutId, owner, other.bearer()).andExpect(status().isNotFound());

            // 確認：注文は作られない
            assertThat(dsl.fetchCount(SALES_ORDER, SALES_ORDER.CHECKOUT_ID.eq(checkoutId))).isZero();
        }

        @Test
        @DisplayName("AC-127 token のメールアドレスが確認済みでなければ、注文しない")
        void rejectsUnverifiedEmail() throws Exception {
            // 準備：メールアドレスが確認済みでない会員が、注文手続きを始める
            var unverified = TestTokens.member().subject(UUID.randomUUID()).emailVerified(false);
            var checkoutId = checkout(unverified);

            // 操作：注文する
            order(checkoutId, unverified, unverified.bearer())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("EMAIL_NOT_VERIFIED"));

            // 確認：注文は作られない
            assertThat(dsl.fetchCount(SALES_ORDER, SALES_ORDER.CHECKOUT_ID.eq(checkoutId))).isZero();
        }
    }

    @Nested
    @DisplayName("注文の参照")
    class ViewOrder {

        @Test
        @DisplayName("AC-131 ほかの会員は、注文を参照できない（404）")
        void otherCannotView() throws Exception {
            // 準備：持ち主が注文する
            var orderId = placeOrder(owner);

            // 操作と確認：ほかの会員には 404
            mockMvc.perform(get(STORE + "/orders/" + orderId).with(other.bearer()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-131 token がなければ、注文を参照できない（401）")
        void requiresToken() throws Exception {
            // 準備：持ち主が注文する
            var orderId = placeOrder(owner);

            // 操作と確認：token なしでは 401
            mockMvc.perform(get(STORE + "/orders/" + orderId).with(TestTokens.none()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("キャンセルの申請")
    class RequestCancellation {

        @Test
        @DisplayName("AC-130、131 注文した会員は、キャンセルを申請できる")
        void ownerCanRequest() throws Exception {
            // 準備：持ち主の受付の注文
            var orderId = acceptedOrder(owner);

            // 操作：持ち主が申請する
            requestCancellation(orderId, owner.bearer()).andExpect(status().isNoContent());

            // 確認：キャンセル申請中になる
            assertOrderStatus(orderId, "CANCELLATION_REQUESTED");
        }

        @Test
        @DisplayName("AC-131 ほかの会員は、キャンセルを申請できない（404）")
        void otherCannotRequest() throws Exception {
            // 準備：持ち主の受付の注文
            var orderId = acceptedOrder(owner);

            // 操作：ほかの会員が申請する
            requestCancellation(orderId, other.bearer()).andExpect(status().isNotFound());

            // 確認：受付のまま
            assertOrderStatus(orderId, "ACCEPTED");
        }

        @Test
        @DisplayName("AC-131 token がなければ、キャンセルを申請できない（401）")
        void requiresToken() throws Exception {
            // 準備：持ち主の受付の注文
            var orderId = acceptedOrder(owner);

            // 操作：token なしで申請する
            requestCancellation(orderId, TestTokens.none()).andExpect(status().isUnauthorized());

            // 確認：受付のまま
            assertOrderStatus(orderId, "ACCEPTED");
        }
    }

    @Nested
    @DisplayName("自分の注文の一覧")
    class MyOrders {

        @Test
        @DisplayName("AC-129 自分の注文だけを、注文日時の新しい順に、ページに分けて返す")
        void listsOwnOrdersNewestFirst() throws Exception {
            // 準備：持ち主が 1 時間おきに 3 件、ほかの会員が 1 件を注文する
            var ids = placeThreeOwnOrdersAndOneOther();

            // 操作と確認：1 ページ 2 件で、1 ページ目は 3 件目と 2 件目、2 ページ目は 1 件目
            mockMvc.perform(get(STORE + "/me/orders").param("size", "2").with(owner.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id").value(contains(ids.get(2).toString(), ids.get(1).toString())));
            mockMvc.perform(get(STORE + "/me/orders").param("size", "2").param("page", "2").with(owner.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id").value(contains(ids.get(0).toString())));
        }

        @Test
        @DisplayName("AC-129 ページの番号・大きさと、自分の注文の件数・ページ数を返す")
        void returnsPageInfo() throws Exception {
            // 準備：持ち主が 3 件、ほかの会員が 1 件を注文する
            placeThreeOwnOrdersAndOneOther();

            // 操作と確認：1 ページ 2 件で、件数はほかの会員の注文を数えない
            mockMvc.perform(get(STORE + "/me/orders").param("size", "2").with(owner.bearer()))
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.totalItems").value(3))
                    .andExpect(jsonPath("$.totalPages").value(2));
        }

        @Test
        @DisplayName("AC-129 各行に、注文番号・注文日時・総額・状態を返す")
        void showsColumns() throws Exception {
            // 準備：持ち主が 3 件、ほかの会員が 1 件を注文する
            placeThreeOwnOrdersAndOneOther();

            // 操作と確認：先頭の行に各項目があり、状態は支払待ち
            mockMvc.perform(get(STORE + "/me/orders").param("size", "2").with(owner.bearer()))
                    .andExpect(jsonPath("$.items[0].orderNumber").isNotEmpty())
                    .andExpect(jsonPath("$.items[0].orderedAt").isNotEmpty())
                    .andExpect(jsonPath("$.items[0].totalAmount").isNumber())
                    .andExpect(jsonPath("$.items[0].status").value("PENDING_PAYMENT"));
        }

        @Test
        @DisplayName("AC-129 注文のない会員は空の一覧")
        void emptyForMemberWithoutOrders() throws Exception {
            // 準備：持ち主だけが注文する
            placeOrder(owner);

            // 操作と確認：ほかの会員の一覧は空
            mockMvc.perform(get(STORE + "/me/orders").with(other.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(0)))
                    .andExpect(jsonPath("$.totalItems").value(0));
        }

        @Test
        @DisplayName("AC-137 token がなければ 401")
        void requiresToken() throws Exception {
            // 操作と確認：token なしでは 401
            mockMvc.perform(get(STORE + "/me/orders").with(TestTokens.none()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("自分の支払待ちの注文の一覧")
    class MyPendingOrders {

        @Test
        @DisplayName("AC-406-4 自分の支払待ちの注文だけを、注文日時の新しい順に全て返す")
        void listsOwnPendingOrdersNewestFirst() throws Exception {
            // 準備：持ち主の支払待ち 2 件・受付 1 件・キャンセル 1 件と、ほかの会員の 1 件
            var first = placeOrder(owner);
            clock.setInstant(NOW.plus(Duration.ofHours(1)));
            var second = placeOrder(owner, startAcknowledging(owner, first));
            clock.setInstant(NOW.plus(Duration.ofHours(2)));
            var accepted = placeOrder(owner, startAcknowledging(owner, first, second));
            OrderPayments.accept(applicationContext, accepted);
            clock.setInstant(NOW.plus(Duration.ofHours(3)));
            var expired = placeOrder(owner, startAcknowledging(owner, first, second));
            OrderPayments.expire(applicationContext, expired);
            placeOrder(other);

            // 操作と確認：持ち主の支払待ちの 2 件だけが、新しい順に並ぶ
            mockMvc.perform(get(STORE + "/me/orders/pending").with(owner.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id").value(contains(second.toString(), first.toString())));
        }

        @Test
        @DisplayName("AC-406-4 各項目は、注文の表示と同じ内容")
        void returnsSameContentAsOrder() throws Exception {
            // 準備：持ち主が注文する
            var orderId = placeOrder(owner);

            // 操作：支払待ちの一覧と、注文の表示を取得する
            var list = mockMvc.perform(get(STORE + "/me/orders/pending").with(owner.bearer()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            var order = mockMvc.perform(get(STORE + "/orders/" + orderId).with(owner.bearer()))
                    .andReturn().getResponse().getContentAsString();

            // 確認：一覧の項目が、注文の表示の全体と同じ
            assertThat(JsonPath.<Object>read(list, "$.items[0]")).isEqualTo(JsonPath.<Object>read(order, "$"));
        }

        @Test
        @DisplayName("AC-406-4 各項目は、確認の画面に必要な注文番号・注文日時・明細・総額・支払の期限を含む")
        void containsFieldsForConfirmation() throws Exception {
            // 準備：持ち主が注文する
            var orderId = placeOrder(owner);

            // 操作と確認：1 件だけで、確認の画面の項目を持つ
            assertPendingItem(mockMvc.perform(get(STORE + "/me/orders/pending").with(owner.bearer())), orderId);
        }

        @Test
        @DisplayName("AC-406-4 支払の期限を過ぎ、まだキャンセルされていない注文も示し、支払えないことを示す")
        void includesOverdueOrder() throws Exception {
            // 準備：持ち主が注文し、支払の期限の 1 秒後にする
            var orderId = placeOrder(owner);
            var order = mockMvc.perform(get(STORE + "/orders/" + orderId).with(owner.bearer()))
                    .andReturn().getResponse().getContentAsString();
            clock.setInstant(Instant.parse(JsonPath.read(order, "$.paymentDeadline")).plusSeconds(1));

            // 操作と確認：一覧に残り、支払えない
            mockMvc.perform(get(STORE + "/me/orders/pending").with(owner.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].id").value(orderId.toString()))
                    .andExpect(jsonPath("$.items[0].actions.pay").value(false));
        }

        @Test
        @DisplayName("AC-406-4 支払待ちの注文がない会員は空の一覧")
        void emptyWithoutPendingOrders() throws Exception {
            // 準備：持ち主だけが注文する
            placeOrder(owner);

            // 操作と確認：ほかの会員の一覧は空
            mockMvc.perform(get(STORE + "/me/orders/pending").with(other.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(0)));
        }

        @Test
        @DisplayName("AC-406-4 token がなければ 401")
        void requiresToken() throws Exception {
            // 操作と確認：token なしでは 401
            mockMvc.perform(get(STORE + "/me/orders/pending").with(TestTokens.none()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 持ち主から見た注文の状態を確認する。
     *
     * @param orderId 注文 ID
     * @param status  期待する注文の状態
     */
    private void assertOrderStatus(UUID orderId, String status) throws Exception {
        mockMvc.perform(get(STORE + "/orders/" + orderId).with(owner.bearer()))
                .andExpect(jsonPath("$.status").value(status));
    }

    /**
     * 支払待ちの一覧が、注文 1 件だけで、確認の画面の項目を持つことを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>200</li>
     *   <li>項目が 1 件</li>
     *   <li>注文 ID</li>
     *   <li>注文番号がある</li>
     *   <li>注文日時が今の時刻</li>
     *   <li>状態が支払待ち</li>
     *   <li>明細の商品名</li>
     *   <li>総額が数</li>
     *   <li>支払の期限がある</li>
     *   <li>支払える</li>
     * </ul>
     *
     * @param list    支払待ちの一覧の応答
     * @param orderId 注文 ID（注文のときに作るので、定数にできない）
     */
    private void assertPendingItem(ResultActions list, UUID orderId) throws Exception {
        list.andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(orderId.toString()))
                .andExpect(jsonPath("$.items[0].orderNumber").isNotEmpty())
                .andExpect(jsonPath("$.items[0].orderedAt").value(clock.instant().toString()))
                .andExpect(jsonPath("$.items[0].status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.items[0].lines[0].productName").value(PRODUCT_NAME))
                .andExpect(jsonPath("$.items[0].amounts.total").isNumber())
                .andExpect(jsonPath("$.items[0].paymentDeadline").isNotEmpty())
                .andExpect(jsonPath("$.items[0].actions.pay").value(true));
    }

    // ============================== 準備の補助 ==============================

    /**
     * 持ち主が 1 時間おきに 3 件を注文し、ほかの会員が 1 件を注文する。
     *
     * @return 持ち主の注文の ID（注文した順）
     */
    private List<UUID> placeThreeOwnOrdersAndOneOther() throws Exception {
        var first = placeOrder(owner);
        clock.setInstant(NOW.plus(Duration.ofHours(1)));
        var second = placeOrder(owner, startAcknowledging(owner, first));
        clock.setInstant(NOW.plus(Duration.ofHours(2)));
        var third = placeOrder(owner, startAcknowledging(owner, first, second));
        placeOrder(other);
        return List.of(first, second, third);
    }

    /**
     * 会員として注文し、受付にする。
     *
     * <p>申請できるのは受付の注文（与信の成功の後）。ロールバックするテストなので、リスナーと同じ処理を呼ぶ。
     *
     * @param member 会員
     * @return 注文 ID
     */
    private UUID acceptedOrder(TestTokens.Builder member) throws Exception {
        var orderId = placeOrder(member);
        OrderPayments.accept(applicationContext, orderId);
        return orderId;
    }

    /**
     * 会員のカートに品目を入れ、カート ID を返す。
     *
     * @param member 会員
     * @return カート ID
     */
    private UUID memberCartWithItem(TestTokens.Builder member) throws Exception {
        var cartId = orders.cart(member.bearer());
        orders.add(cartId, variantId, 1, member.bearer());
        return cartId;
    }

    /**
     * 注文手続きを始めるリクエストを送る。
     *
     * @param cartId    カート ID
     * @param requester token などを付ける後処理
     * @return 応答
     */
    private ResultActions startCheckout(UUID cartId, RequestPostProcessor requester) throws Exception {
        return mockMvc.perform(startCheckoutRequest(cartId).with(requester));
    }

    /**
     * 会員のカートに入れて、注文手続きを始める。
     *
     * @param member 会員
     * @return 注文手続き ID
     */
    private UUID checkout(TestTokens.Builder member) throws Exception {
        return orders.startCheckout(memberCartWithItem(member), member.bearer());
    }

    /**
     * 注文のリクエストを送る。
     *
     * <p>総額は、注文手続きの持ち主として読んだものを送る（ほかの会員のリクエストが、総額の違いで拒まれないように）。
     *
     * @param checkoutId    注文手続き ID
     * @param checkoutOwner 注文手続きの持ち主
     * @param requester     token などを付ける後処理
     * @return 応答
     */
    private ResultActions order(UUID checkoutId, TestTokens.Builder checkoutOwner, RequestPostProcessor requester) throws Exception {
        var checkout = mockMvc.perform(get(STORE + "/checkouts/" + checkoutId).with(checkoutOwner.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long total = JsonPath.<Number>read(checkout, "$.amounts.total").longValue();
        return mockMvc.perform(post(STORE + "/checkouts/" + checkoutId + "/order").with(requester)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody(total)));
    }

    /**
     * 会員として注文し、注文 ID を返す。
     *
     * @param member 会員
     * @return 注文 ID
     */
    private UUID placeOrder(TestTokens.Builder member) throws Exception {
        return placeOrder(member, checkout(member));
    }

    /**
     * 会員のカートに入れて、確認した注文の一覧を送って注文手続きを始め、注文手続き ID を返す。
     *
     * @param member       会員
     * @param acknowledged 確認した支払待ちの注文の ID
     * @return 注文手続き ID
     */
    private UUID startAcknowledging(TestTokens.Builder member, UUID... acknowledged) throws Exception {
        return orders.startCheckout(memberCartWithItem(member), member.bearer(), List.of(acknowledged));
    }

    /**
     * 会員として注文手続きから注文し、注文 ID を返す。
     *
     * @param member     会員
     * @param checkoutId 注文手続き ID
     * @return 注文 ID
     */
    private UUID placeOrder(TestTokens.Builder member, UUID checkoutId) throws Exception {
        return orders.placeFromCheckout(checkoutId, member.bearer());
    }

    /**
     * キャンセルの申請のリクエストを送る。
     *
     * @param orderId   注文 ID
     * @param requester token などを付ける後処理
     * @return 応答
     */
    private ResultActions requestCancellation(UUID orderId, RequestPostProcessor requester) throws Exception {
        return mockMvc.perform(put(STORE + "/orders/" + orderId + "/cancellation-request").with(requester)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"間違えた\"}"));
    }
}
