package com.ecsite.module.order.presentation;

import com.ecsite.common.model.id.VariantId;
import com.ecsite.module.cart.event.CartChanged;
import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.support.MutableClock;
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import com.ecsite.support.data.InventoryTestData;
import com.ecsite.support.data.OrderTestData;
import com.ecsite.support.data.ShippingTaxCategory;
import com.ecsite.support.security.TestTokens;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static com.ecsite.jooq.ordering.Tables.CHECKOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文手続きと、カートの変更のイベントの受け入れテスト
 *
 * <p>カートの変更のイベントで、そのカートの注文手続きの引当が解けることを確認する。
 * 変更の後に始めた注文手続きと、注文手続きの開始のバージョンより古いバージョンのイベントでは解かないこと、バージョンのないイベントでは全て解くことも確認する。
 * 進み直しと期限の経過による引当の解除は {@link CheckoutStartApiTest} が確認する。
 *
 * <p>カートの変更のイベントはコミットの後に非同期で届くので、テストはトランザクションにせず、コミットして処理を待つ。
 *
 * <p>作ったデータは消さない。他のテストは、一覧の全体ではなく自分のデータだけを確認する。
 */
@IntegrationTest
class CheckoutCartChangeApiTest {

    // ============================== Bean ==============================

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
    StockReservationApi stockReservationApi;

    @Autowired
    AsyncEvents asyncEvents;

    @Autowired
    DSLContext dsl;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    PlatformTransactionManager transactionManager;

    /**
     * 税区分・カテゴリ・商品を作る補助
     */
    private CatalogTestData catalog;

    /**
     * 注文を作る補助
     */
    private OrderTestData orders;

    /**
     * テストで使う税区分の ID
     */
    private UUID taxCategoryId;

    /**
     * このテストの会員のカート ID
     */
    private UUID cartId;

    /**
     * このテストの会員
     *
     * <p>コミットするので、他のテストとカートを共有しないよう、テストごとに変える。
     */
    private TestTokens.Builder owner;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, clock);
        ShippingTaxCategory.ensure(dsl);
        catalog = new CatalogTestData(mockMvc, clock);
        owner = TestTokens.member().subject(UUID.randomUUID());
        orders = new OrderTestData(mockMvc, null, null, owner.bearer());
        taxCategoryId = catalog.taxCategory("カート変更-" + UUID.randomUUID().toString().substring(0, 8), "10");
        cartId = orders.cart();
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("AC-84 カートを変更すると（カート変更のイベント）、そのカートの注文手続きの引当が解かれる")
    void releasesReservationOnCartChange() throws Exception {
        // 準備：在庫 3 の品目を 2 個入れて、注文手続きに進む
        var variant = stockedVariant(3);
        orders.add(cartId, variant, 2);
        // 追加のイベントの処理と注文手続きの開始が重ならないよう、先に処理を待つ（イベントは開始と同じバージョンなので、引当は解かない）
        asyncEvents.awaitCartChanged(cartId);
        orders.startCheckout(cartId);

        // 確認（前提）：2 個を引き当てている
        assertThat(stockReservationApi.sellableQuantities(List.of(new VariantId(variant)), null)).containsEntry(new VariantId(variant), 1);

        // 操作：カートの数量を変え、変更のイベントの処理を待つ
        mockMvc.perform(put("/api/store/carts/" + cartId + "/items/" + variant).with(owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 1}"""))
                .andExpect(status().isNoContent());
        asyncEvents.awaitCartChanged(cartId);

        // 確認：引当が解け、販売可能数が在庫数に戻っている
        assertThat(stockReservationApi.sellableQuantities(List.of(new VariantId(variant)), null)).containsEntry(new VariantId(variant), 3);
    }

    @Test
    @DisplayName("カートの変更の後に始めた注文手続きの引当は、遅れて届いた変更のイベントで解かれない")
    void keepsReservationStartedAfterChangeWhenEventArrivesLate() throws Exception {
        // 準備：在庫 3 の品目を 2 個入れて、注文手続きに進む
        var variant = stockedVariant(3);
        orders.add(cartId, variant, 2);
        asyncEvents.awaitCartChanged(cartId);
        orders.startCheckout(cartId);
        var startedVersion = startedCartVersion();

        // 操作：開始の時点のバージョンのイベント（開始の前の変更で、処理が遅れたもの）が届く
        publishAndAwait(startedVersion);

        // 確認：引当が残っている
        assertThat(stockReservationApi.sellableQuantities(List.of(new VariantId(variant)), null)).containsEntry(new VariantId(variant), 1);
    }

    @Test
    @DisplayName("注文手続きの開始のバージョンより古いバージョンの変更のイベントが遅れて届いても、引当は解かれない")
    void keepsReservationWhenEventIsOlderThanStartedVersion() throws Exception {
        // 準備：在庫 3 の品目を 2 個入れて、注文手続きに進む
        var variant = stockedVariant(3);
        orders.add(cartId, variant, 2);
        asyncEvents.awaitCartChanged(cartId);
        orders.startCheckout(cartId);
        var startedVersion = startedCartVersion();

        // 操作：開始より古いバージョンのイベントが届く
        publishAndAwait(startedVersion - 1);

        // 確認：引当が残っている
        assertThat(stockReservationApi.sellableQuantities(List.of(new VariantId(variant)), null)).containsEntry(new VariantId(variant), 1);
    }

    @Test
    @DisplayName("注文手続きの開始より新しいバージョンの変更のイベントで、引当が解かれる")
    void releasesReservationWhenEventIsNewerThanStartedVersion() throws Exception {
        // 準備
        var variant = stockedVariant(3);
        orders.add(cartId, variant, 2);
        asyncEvents.awaitCartChanged(cartId);
        orders.startCheckout(cartId);
        var startedVersion = startedCartVersion();

        // 操作：開始の後の変更のイベントが届く
        publishAndAwait(startedVersion + 1);

        // 確認
        assertThat(stockReservationApi.sellableQuantities(List.of(new VariantId(variant)), null)).containsEntry(new VariantId(variant), 3);
    }

    @Test
    @DisplayName("バージョンを持たない変更のイベントでは、全ての注文手続きの引当が解かれる")
    void releasesReservationWhenEventHasNoVersion() throws Exception {
        // 準備
        var variant = stockedVariant(3);
        orders.add(cartId, variant, 2);
        asyncEvents.awaitCartChanged(cartId);
        orders.startCheckout(cartId);

        // 操作：変更の前に発行されたイベント（バージョンなし）が届く
        publishAndAwait(null);

        // 確認
        assertThat(stockReservationApi.sellableQuantities(List.of(new VariantId(variant)), null)).containsEntry(new VariantId(variant), 3);
    }

    // ============================== 準備の補助 ==============================

    /**
     * このカートの最新の注文手続きを始めたときの、カートのバージョンを返す。
     */
    private long startedCartVersion() {
        return dsl.select(CHECKOUT.CART_VERSION).from(CHECKOUT).where(CHECKOUT.CART_ID.eq(cartId))
                .orderBy(CHECKOUT.CREATED_AT.desc()).limit(1).fetchOne(CHECKOUT.CART_VERSION);
    }

    /**
     * カート変更のイベントをコミットして発行し、その処理を待つ。
     *
     * @param cartVersion カートのバージョン。バージョンを持たないイベントは null
     */
    private void publishAndAwait(@Nullable Long cartVersion) {
        var event = new CartChanged(UUID.randomUUID(), clock.instant(), cartId, cartVersion);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> publisher.publishEvent(event));
        asyncEvents.awaitHandled(published -> published instanceof CartChanged changed && changed.eventId().equals(event.eventId()));
    }

    /**
     * 公開した商品（1,000 円・10%）のバリアントを作り、在庫数を設定して、バリアント ID を返す。
     *
     * @param quantity 在庫数
     * @return バリアント ID
     */
    private UUID stockedVariant(int quantity) throws Exception {
        return inventory.stockedVariant(catalog, taxCategoryId, "商品", quantity);
    }
}
