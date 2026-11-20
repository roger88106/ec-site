package com.ecsite.module.order.presentation;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.application.port.CartDirectory;
import com.ecsite.module.order.event.OrderStatusChanged;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.ecsite.support.MutableClock;
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import com.ecsite.support.data.InventoryTestData;
import com.ecsite.support.data.OrderTestData;
import com.ecsite.support.data.ShippingTaxCategory;
import com.ecsite.support.security.TestTokens;
import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static com.ecsite.jooq.ordering.Tables.CHECKOUT;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static com.ecsite.support.async.AsyncEvents.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 与信の成功の後の、カートから品目を除く処理の受け入れテスト
 *
 * <p>注文を受付にする処理と、注文した品目をカートから除く処理は、別のトランザクションで動く。
 * カートの変更の競合で除く処理が失敗しても注文が受付のまま残ること、再発行で除く処理がやり直されること、
 * 注文手続きが消えた後でも会員のカートから除かれること、除く処理が遅れた間に会員が変えた同じバリアントの行も除かれることを確認する。
 * 注文した品目だけを除くことや、期限切れのジョブで受付になる経路は {@code OrderPaymentApiTest} が確認する。
 *
 * <p>イベントはコミットの後に非同期で届くので、コミットして処理を待つ。作ったデータは消さないので、会員と税区分の名前は
 * テストごとに変え、確認は自分の注文とカートだけにする。
 */
@IntegrationTest
class OrderPaymentCartRemovalApiTest {

    // ============================== 定数 ==============================

    /**
     * 準備で入荷する在庫数
     */
    private static final int STOCKED = 5;

    /**
     * 再発行の対象になる発行からの時間を過ぎるために、時計を進める時間
     */
    private static final Duration RESUBMIT_AFTER = Duration.ofMinutes(6);

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestPaymentGateway gateway;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    MutableClock clock;

    @Autowired
    StockItemRepository stockItemRepository;

    @Autowired
    DSLContext dsl;

    @Autowired
    AsyncEvents asyncEvents;

    /**
     * カートの腐敗防止層
     *
     * <p>準備では本物の処理を呼び、テストの中でだけカートから除く処理を失敗させる。
     */
    @MockitoSpyBean
    CartDirectory cartDirectory;

    /**
     * 注文を作る補助
     */
    private OrderTestData orders;

    /**
     * このテストの会員
     *
     * <p>巻き戻さないので、他のテストとカートを共有しないよう、テストごとに変える。
     */
    private TestTokens.Builder owner;

    /**
     * 注文する商品のバリアント ID（在庫は {@link #STOCKED}）
     */
    private UUID variantId;

    @BeforeEach
    void setUp() throws Exception {
        var inventory = new InventoryTestData(stockItemRepository, clock);
        ShippingTaxCategory.ensure(dsl);
        var catalog = new CatalogTestData(mockMvc, clock);
        var taxCategoryId = catalog.taxCategory("カート除去-" + UUID.randomUUID().toString().substring(0, 8), "10");
        variantId = inventory.stockedVariant(catalog, taxCategoryId, "カート除去の商品", STOCKED);
        owner = TestTokens.member().subject(UUID.randomUUID());
        orders = new OrderTestData(mockMvc, gateway, applicationContext, owner.bearer());
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("カートから除く処理が同時の変更で失敗しても、注文は受付のまま残る")
    void keepsOrderAcceptedWhenCartRemovalConflicts() throws Exception {
        // 準備：注文し、カートから除く処理が 1 回目だけ同時の変更で失敗するようにする
        var orderId = orders.placePendingOrder(variantId, 1);
        failRemovalOnce();

        // 操作：決済サービスで支払う
        gateway.pay(orders.openPaymentPage(orderId));

        // 確認：除く処理が失敗した後も、注文は受付で、品目はカートに残る
        asyncEvents.awaitFailed(accepted(orderId));
        orders.awaitStatus(orderId, "ACCEPTED");
        assertThat(cartVariantIds()).containsExactly(variantId.toString());
    }

    @Test
    @DisplayName("カートから除く処理が失敗しても、イベントの再発行でやり直され、品目が除かれる")
    void removesItemsOnResubmission() throws Exception {
        // 準備：注文し、カートから除く処理が 1 回目だけ失敗した状態にする
        var orderId = orders.placePendingOrder(variantId, 1);
        failRemovalOnce();
        gateway.pay(orders.openPaymentPage(orderId));
        asyncEvents.awaitFailed(accepted(orderId));

        // 操作：未完了のイベントを再発行する
        clock.advance(RESUBMIT_AFTER);
        asyncEvents.resubmit(accepted(orderId));

        // 確認：品目がカートから除かれる
        awaitCartEmpty();
    }

    @Test
    @DisplayName("注文手続きが消えた後に与信の成功を受けても、注文した品目が会員のカートから除かれる")
    void removesItemsAfterCheckoutIsDeleted() throws Exception {
        // 準備：注文し、注文手続きを消す
        var orderId = orders.placePendingOrder(variantId, 1);
        var checkoutId = dsl.select(SALES_ORDER.CHECKOUT_ID).from(SALES_ORDER).where(SALES_ORDER.ID.eq(orderId))
                .fetchOne(SALES_ORDER.CHECKOUT_ID);
        dsl.deleteFrom(CHECKOUT).where(CHECKOUT.ID.eq(checkoutId)).execute();

        // 操作：決済サービスで支払う
        gateway.pay(orders.openPaymentPage(orderId));

        // 確認：受付になり、品目がカートから除かれる
        orders.awaitStatus(orderId, "ACCEPTED");
        awaitCartEmpty();
    }

    @Test
    @DisplayName("与信の成功の処理が遅れた間に会員が同じバリアントの行を変えても、その行は数量によらず除かれる")
    void removesRowChangedByMemberBeforeRemoval() throws Exception {
        // 準備：注文の後、与信の成功の処理の前に、会員が同じバリアントの数量を変える
        var orderId = orders.placePendingOrder(variantId, 1);
        mockMvc.perform(put("/api/store/carts/" + orders.cart() + "/items/" + variantId).with(owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 3}"""))
                .andExpect(status().isNoContent());

        // 操作：決済サービスで支払う
        gateway.pay(orders.openPaymentPage(orderId));

        // 確認：変えた行も除かれる
        orders.awaitStatus(orderId, "ACCEPTED");
        awaitCartEmpty();
    }

    // ============================== 準備の補助 ==============================

    /**
     * カートから除く処理を、1 回目だけ同時の変更（409）で失敗させる。2 回目からは本物の処理を呼ぶ。
     */
    private void failRemovalOnce() {
        doThrow(BusinessException.of(CommonErrorCode.CONCURRENT_MODIFICATION))
                .doCallRealMethod()
                .when(cartDirectory).removeItems(any(), any());
    }

    /**
     * 注文が受付になった変化のイベントを選ぶ条件を返す。
     *
     * @param orderId 注文 ID
     * @return イベントの条件
     */
    private static Predicate<Object> accepted(UUID orderId) {
        return event -> event instanceof OrderStatusChanged changed && changed.orderId().equals(orderId)
                && changed.to() == OrderStatusChanged.Status.ACCEPTED;
    }

    // ============================== 確認の補助 ==============================

    /**
     * 会員のカートの品目のバリアント ID を読む。
     *
     * @return バリアント ID の一覧
     */
    private List<String> cartVariantIds() throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/store/carts/" + orders.cart()).with(owner.bearer())).andReturn().getResponse().getContentAsString(),
                "$.items[*].variantId");
    }

    /**
     * 会員のカートが空になるのを待つ。
     */
    private void awaitCartEmpty() {
        await().atMost(TIMEOUT).until(() -> cartVariantIds().isEmpty());
    }
}
