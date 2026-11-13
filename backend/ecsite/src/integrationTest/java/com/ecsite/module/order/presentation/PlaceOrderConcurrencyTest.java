package com.ecsite.module.order.presentation;

import com.ecsite.AsyncEvents;
import com.ecsite.CatalogTestData;
import com.ecsite.Concurrently;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.LockWaits;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestJson;
import com.ecsite.TestTokens;
import com.ecsite.module.cart.event.CartChanged;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.ecsite.OrderTestData.placeOrderRequest;
import static com.ecsite.jooq.inventory.Tables.STOCK_ITEM;
import static com.ecsite.jooq.inventory.Tables.STOCK_RESERVATION;
import static com.ecsite.jooq.ordering.Tables.CHECKOUT;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 注文の並行性の受け入れテスト
 *
 * <p>行ロックと会員のロックが働いていることを確認するため、リクエストごとにコミットする。ロールバックしないが、作ったデータは消さない。
 * 税区分の名称は一意なので、ほかのテストと重ならない名前にする。
 */
@IntegrationTest
class PlaceOrderConcurrencyTest {

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

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

    @Autowired
    AsyncEvents asyncEvents;

    /**
     * テストの側でトランザクションを開いて、在庫の行をロックする TransactionTemplate
     */
    @Autowired
    TransactionTemplate transactionTemplate;

    /**
     * 注文を作る補助
     */
    private OrderTestData orders;

    /**
     * テストで使う税区分の ID
     */
    private UUID taxCategoryId;

    private CatalogTestData.TestProduct product;

    /**
     * カート ID → 持ち主の会員の token（同じテストで 2 人の消費者を表すため、カートごとに会員を分ける）
     */
    private final Map<UUID, TestTokens.Builder> owners = new HashMap<>();

    /**
     * 注文手続き ID → 持ち主の会員の token
     */
    private final Map<UUID, TestTokens.Builder> checkoutOwners = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        clock.setInstant(NOW);
        ShippingTaxCategory.ensure(dsl);
        var catalog = new CatalogTestData(mockMvc, clock);
        orders = new OrderTestData(mockMvc);
        taxCategoryId = catalog.taxCategory("並行-" + UUID.randomUUID().toString().substring(0, 8), "10");
        product = catalog.product(taxCategoryId, "並行の商品", 1000).publish();
    }

    @Test
    @DisplayName("AC-91 同じ注文手続きで同時に何度送っても、注文は 1 件だけで、どの応答も同じ注文 ID")
    void placesOnceForConcurrentResends() throws Exception {
        stock(3);
        var cartId = cartWith(2);
        var checkoutId = startCheckout(cartId);

        var responses = Concurrently.run(5, i -> place(checkoutId));

        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsOnly(201, 200)
                .filteredOn(status -> status == 201).hasSize(1);
        assertThat(responses).extracting(response -> JsonPath.<String>read(response.getContentAsString(), "$.id"))
                .containsOnly(JsonPath.<String>read(responses.getFirst().getContentAsString(), "$.id"));
        assertThat(dsl.fetchCount(SALES_ORDER, SALES_ORDER.CHECKOUT_ID.eq(checkoutId))).isEqualTo(1);
        assertThat(quantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-87 引当の期限が切れた 2 人が、販売可能数 1 の在庫を同時に注文すると、1 件だけ成立する")
    void placesOnlyOneForLastItem() throws Exception {
        stock(1);
        var first = startCheckout(cartWith(1));
        // 1 人目の引当の期限が切れてから、2 人目が引き当てる
        clock.setInstant(clock.instant().plus(Duration.ofMinutes(20)));
        var second = startCheckout(cartWith(1));
        clock.setInstant(clock.instant().plus(Duration.ofMinutes(20)));

        var responses = placeAll(List.of(first, second));

        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(201, 409);
        assertThat(quantity()).isZero();
    }

    @Test
    @DisplayName("AC-406-14 同じ会員の確認していない 2 つの注文手続きから同時に注文すると、一方だけ成立し、在庫は 1 件分だけ減り、他方は確認を求められる")
    void placesOnlyOneForSameMember() throws Exception {
        stock(5);
        var cartId = cartWith(1);
        var checkouts = List.of(startCheckout(cartId), startCheckout(cartId));

        var responses = placeAll(checkouts);

        expectOnePlacedAndOneRejected(responses, checkouts);
        assertThat(quantity()).isEqualTo(4);
    }

    @Test
    @DisplayName("AC-406-12、AC-406-14 同じ支払待ちの注文を確認した 2 つの注文手続きから同時に注文すると、先に成立した注文を確認していないので、一方だけ成立する")
    void placesOnlyOneWhenBothAcknowledged() throws Exception {
        stock(5);
        var cartId = cartWith(1);
        var pending = TestJson.createdId(place(startCheckout(cartId)).getContentAsString());
        var checkouts = List.of(startAcknowledging(cartId, pending), startAcknowledging(cartId, pending));

        var responses = placeAll(checkouts);

        expectOnePlacedAndOneRejected(responses, checkouts);
        assertThat(quantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("AC-411-5、NFR-411-1 支払待ちの 2 件を全て確認した 2 つの注文手続きから同時に注文すると、一方だけ成立し、在庫は 1 個分だけ減り、支払待ちは上限（3 件）を超えない（後の注文は確認の判定で拒否される）")
    void placesOnlyOneBelowLimit() throws Exception {
        stock(5);
        var cartId = cartWith(1);
        var first = TestJson.createdId(place(startCheckout(cartId)).getContentAsString());
        var second = TestJson.createdId(place(startAcknowledging(cartId, first)).getContentAsString());
        var checkouts = List.of(startAcknowledging(cartId, first, second), startAcknowledging(cartId, first, second));

        var responses = placeAll(checkouts);

        // 確認するのは、同時の注文でも支払待ちが上限を超えない結果である。
        // 後の注文は、会員のロックを待った後の読み取りで先の注文を読み取れ、それを確認していないので、確認の判定で拒否される
        // （開始で上限を判定しており、上限の設定は同じなので、同時の注文では上限の判定に届かない。注文の時点の上限の判定は、PlaceOrderApiTest で確認する）
        expectOnePlacedAndOneRejected(responses, checkouts);
        assertThat(quantity()).isEqualTo(2);
        assertThat(OrderTestData.pendingOrderCount(dsl, memberOf(checkouts.getFirst()))).isEqualTo(3);
    }

    @Test
    @DisplayName("AC-406-14 先の注文のトランザクションが終わるまで、同じ会員の後の注文は会員のロックを待ち、先の注文の成立の後に判定されて、確認を求められる")
    void waitsForMemberLockAndRejectsLater() throws Exception {
        stock(5);
        var cartId = cartWith(1);
        var earlier = startCheckout(cartId);
        var later = startCheckout(cartId);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var placing = new ArrayList<Future<MockHttpServletResponse>>();
            whileStockLocked(() -> {
                // 先の注文は、会員のロックを持ったまま、在庫の行で待つ。トランザクションはコミットしない
                placing.add(executor.submit(() -> place(earlier)));
                LockWaits.awaitBlockedByThisTransaction(dsl);
                // 後の注文は、先の注文が持つ会員のロックを待つ。ロックがなければ、この待ちは現れない
                placing.add(executor.submit(() -> place(later)));
                LockWaits.awaitMemberLockWaiter(dsl, memberOf(later));
            });

            var placed = placing.getFirst().get(AsyncEvents.TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            var rejected = placing.get(1).get(AsyncEvents.TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertThat(placed.getStatus()).isEqualTo(201);
            expectOnePlacedAndOneRejected(List.of(placed, rejected), List.of(earlier, later));
            assertThat(rejected.getStatus()).isEqualTo(409);
            assertThat(quantity()).isEqualTo(4);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("AC-87 在庫の行を待つ間に在庫がなくなると、どの在庫も減らさずに拒否し、先に解いた引当は取り消しで元に戻る")
    void keepsReservationWhenRejectedAfterRelease() throws Exception {
        stock(1);
        var checkoutId = startCheckout(cartWith(1));
        var executor = Executors.newSingleThreadExecutor();
        try {
            var placing = new AtomicReference<Future<MockHttpServletResponse>>();
            whileStockLocked(() -> {
                placing.set(executor.submit(() -> place(checkoutId)));
                LockWaits.awaitBlockedByThisTransaction(dsl);
                // 注文の判定は終わっているので、在庫の行を読む減算で足りないと分かる
                dsl.update(STOCK_ITEM).set(STOCK_ITEM.QUANTITY, 0)
                        .where(STOCK_ITEM.VARIANT_ID.eq(product.variantIds.getFirst())).execute();
            });

            var rejected = placing.get().get(AsyncEvents.TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertThat(rejected.getStatus()).isEqualTo(409);
            assertThat(JsonPath.<String>read(rejected.getContentAsString(), "$.errorCode")).isEqualTo("OUT_OF_STOCK");
            assertThat(dsl.fetchCount(STOCK_RESERVATION, STOCK_RESERVATION.HOLDER_ID.eq(checkoutId))).isEqualTo(1);
            assertThat(dsl.fetchCount(SALES_ORDER, SALES_ORDER.CHECKOUT_ID.eq(checkoutId))).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    // ============================== 補助 ==============================

    /**
     * テストのトランザクションで商品の在庫の行をロックしたまま、処理を実行する。
     *
     * <p>処理の後のコミットでロックが解放される
     */
    private void whileStockLocked(Runnable body) {
        transactionTemplate.executeWithoutResult(status -> {
            dsl.selectFrom(STOCK_ITEM).where(STOCK_ITEM.VARIANT_ID.eq(product.variantIds.getFirst())).forUpdate().fetch();
            body.run();
        });
    }

    /**
     * 注文手続きの持ち主の会員 ID
     */
    private UUID memberOf(UUID checkoutId) {
        return dsl.select(CHECKOUT.MEMBER_ID).from(CHECKOUT).where(CHECKOUT.ID.eq(checkoutId)).fetchSingle(CHECKOUT.MEMBER_ID);
    }

    /**
     * 注文手続きごとに 1 つのスレッドで、同時に注文する
     */
    private List<MockHttpServletResponse> placeAll(List<UUID> checkouts) throws Exception {
        return Concurrently.run(checkouts.size(), i -> place(checkouts.get(i)));
    }

    /**
     * 一方だけ成立し、他方は確認を求める 409 で、注文が 1 件だけであることを確認する
     */
    private void expectOnePlacedAndOneRejected(List<MockHttpServletResponse> responses, List<UUID> checkouts) throws Exception {
        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(201, 409);
        var rejected = responses.stream().filter(response -> response.getStatus() == 409).findFirst().orElseThrow();
        assertThat(JsonPath.<String>read(rejected.getContentAsString(), "$.errorCode"))
                .isEqualTo("PENDING_ORDER_CONFIRMATION_REQUIRED");
        assertThat(dsl.fetchCount(SALES_ORDER, SALES_ORDER.CHECKOUT_ID.in(checkouts))).isEqualTo(1);
    }

    /**
     * 注文する（総額は 1,100 円 × 数量 ＋ 送料。数量はカートのとおり）
     */
    private MockHttpServletResponse place(UUID checkoutId) throws Exception {
        var owner = checkoutOwners.get(checkoutId);
        var checkout = mockMvc.perform(get("/api/store/checkouts/" + checkoutId).with(owner.bearer()))
                .andReturn().getResponse().getContentAsString();
        long total = JsonPath.<Number>read(checkout, "$.amounts.total").longValue();
        return mockMvc.perform(placeOrderRequest(checkoutId, total).with(owner.bearer()))
                .andReturn().getResponse();
    }

    /**
     * 新しい会員の会員のカートに商品を入れ、カート ID を返す
     */
    private UUID cartWith(int quantity) throws Exception {
        var owner = TestTokens.member().subject(UUID.randomUUID());
        var cartId = orders.cart(owner.bearer());
        owners.put(cartId, owner);
        orders.add(cartId, product.variantIds.getFirst(), quantity, owner.bearer());
        // カートの変更の処理が、注文手続きや注文と重ならないように待つ
        asyncEvents.awaitHandled(published -> published instanceof CartChanged changed && changed.cartId().equals(cartId));
        return cartId;
    }

    /**
     * 注文手続きに進み、注文手続き ID を返す
     */
    private UUID startCheckout(UUID cartId) throws Exception {
        var owner = owners.get(cartId);
        var checkoutId = orders.startCheckout(cartId, owner.bearer());
        checkoutOwners.put(checkoutId, owner);
        return checkoutId;
    }

    /**
     * 確認した注文の ID を送って注文手続きに進み、201 を確認して、注文手続き ID を返す
     */
    private UUID startAcknowledging(UUID cartId, UUID... acknowledged) throws Exception {
        var owner = owners.get(cartId);
        var checkoutId = orders.startCheckout(cartId, owner.bearer(), List.of(acknowledged));
        checkoutOwners.put(checkoutId, owner);
        return checkoutId;
    }

    /**
     * 商品の在庫を作り、在庫数を設定する
     */
    private void stock(int quantity) {
        var variantId = product.variantIds.getFirst();
        inventory.stock(variantId, product.id, quantity);
    }

    /**
     * 商品の在庫数
     */
    private int quantity() {
        return dsl.select(STOCK_ITEM.QUANTITY).from(STOCK_ITEM)
                .where(STOCK_ITEM.VARIANT_ID.eq(product.variantIds.getFirst()))
                .fetchSingle(STOCK_ITEM.QUANTITY);
    }
}
