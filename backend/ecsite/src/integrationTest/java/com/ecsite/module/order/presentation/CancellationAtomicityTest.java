package com.ecsite.module.order.presentation;

import com.ecsite.AsyncEvents;
import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestTokens;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.application.port.StockDirectory;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * キャンセルと在庫の戻しの一貫性の受け入れテスト
 *
 * <p>在庫の戻しを失敗させ、注文もキャンセルにならないこと（トランザクションごと取り消されること）を確認する。
 * キャンセルが成功するときに在庫が戻ることは、{@code CancellationApiTest} が確認する。
 *
 * <p>取り消しを見るためにコミットが必要だが、作ったデータは消さない。税区分の名前と会員はテストごとに変え、
 * 確認は自分の注文と在庫だけにする。
 */
@IntegrationTest
class CancellationAtomicityTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 準備で入荷する在庫数
     */
    private static final int STOCKED = 5;

    /**
     * 注文の数量
     */
    private static final int ORDERED = 2;

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

    /**
     * 在庫を作る補助
     */
    private InventoryTestData inventory;

    @Autowired
    DSLContext dsl;

    @Autowired
    AsyncEvents asyncEvents;

    @MockitoSpyBean
    StockDirectory stockDirectory;

    /**
     * テストで使う税区分の ID
     */
    private UUID taxCategoryId;

    /**
     * 注文した商品のバリアント ID
     */
    private UUID variantId;

    /**
     * 受付の注文の ID
     */
    private UUID orderId;

    /**
     * このテストの会員
     *
     * <p>コミットするので、他のテストとカートを共有しないよう、テストごとに変える。
     */
    private TestTokens.Builder owner;

    @BeforeEach
    void setUp() throws Exception {
        owner = TestTokens.member().subject(UUID.randomUUID());
        inventory = new InventoryTestData(stockItemRepository, NOW);
        ShippingTaxCategory.ensure(dsl);
        var catalog = new CatalogTestData(mockMvc, clock);
        taxCategoryId = catalog.taxCategory("一貫性-" + UUID.randomUUID().toString().substring(0, 8), "10");
        var product = catalog.product(taxCategoryId, "一貫性の商品", 1000).publish();
        variantId = product.variantIds.getFirst();
        inventory.stock(variantId, product.id, STOCKED);
        orderId = new OrderTestData(mockMvc, gateway, applicationContext, owner.bearer()).placeOrder(variantId, ORDERED);
        // コミットするので、支払うとリスナーが非同期で注文を受け付け直す。終わる前にキャンセルすると、
        // 楽観ロックで衝突し、在庫の戻しの失敗（500）ではなく衝突（409）になることがある
        asyncEvents.awaitHandled(OrderTestData.paymentAuthorized(orderId));
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("AC-104 在庫の戻しに失敗すると、注文もキャンセルにならず、在庫も変わらない")
    void keepsOrderWhenRestoreFails() throws Exception {
        // 準備：在庫の戻しが失敗する
        doThrow(new IllegalStateException("在庫の戻しの失敗")).when(stockDirectory).restore(any(), any(), any());

        // 操作と確認：キャンセルは 500 になる
        mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "在庫の不備"}"""))
                .andExpect(status().isInternalServerError());

        // 確認：注文は受付のまま、在庫数は注文の後のまま
        mockMvc.perform(get("/api/store/orders/" + orderId).with(owner.bearer())).andExpect(jsonPath("$.status").value("ACCEPTED"));
        mockMvc.perform(get("/api/admin/inventory/variants/" + variantId))
                .andExpect(jsonPath("$.quantity").value(STOCKED - ORDERED));
    }
}
