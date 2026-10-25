package com.ecsite.module.storefront.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.module.catalog.infrastructure.directory.TaxRateScheduleCache;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NFR-1 の確認（T609）。<br />
 * 商品・価格・公開状態・在庫の変更は、直後の照会に反映される（キャッシュしない）。
 * 税率の一覧だけはインスタンス内で 30 秒キャッシュする（docs/design/modules/catalog.md）。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class StorefrontFreshnessTest {

    /**
     * API のパス
     */
    private static final String STORE = "/api/store";

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    TaxRateScheduleCache taxRateScheduleCache;

    @Autowired
    StockItemRepository stockItemRepository;

    private InventoryTestData inventory;

    private String taxCategoryId;

    private String productId;

    private String variantId;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        clock.setInstant(NOW);
        taxRateScheduleCache.evict();
        var catalog = new CatalogTestData(mockMvc, clock);
        taxCategoryId = catalog.taxCategory("標準", "10").toString();
        var product = catalog.product("FRESH-001", UUID.fromString(taxCategoryId), "T シャツ", 1000).publish();
        // 公開で進んだ時計を戻す（税率のキャッシュの確認が、NOW からの秒数で書かれている）
        clock.setInstant(NOW);
        productId = product.id.toString();
        variantId = product.variantIds.getFirst().toString();
        inventory.create(product.variantIds.getFirst(), product.id);
    }

    @Test
    @DisplayName("NFR-1、AC-26 価格の変更は、直後の照会に反映される")
    void reflectsPriceChange() throws Exception {
        mockMvc.perform(get(STORE + "/products")).andExpect(jsonPath("$.items[0].price.inclTax").value(1100));

        mockMvc.perform(put("/api/admin/products/" + productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "T シャツ", "description": "", "taxCategoryId": "%s", "priceExclTax": 2000,
                                 "categoryIds": [], "version": 2}""".formatted(taxCategoryId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(STORE + "/products")).andExpect(jsonPath("$.items[0].price.inclTax").value(2200));
        mockMvc.perform(get(STORE + "/products/" + productId)).andExpect(jsonPath("$.price.inclTax").value(2200));
    }

    @Test
    @DisplayName("NFR-1、AC-29、30 公開状態の変更は、直後の照会に反映される")
    void reflectsPublicationChange() throws Exception {
        mockMvc.perform(put("/api/admin/products/" + productId + "/unpublish")).andExpect(status().isNoContent());
        mockMvc.perform(get(STORE + "/products")).andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(get(STORE + "/products/" + productId)).andExpect(status().isNotFound());

        mockMvc.perform(put("/api/admin/products/" + productId + "/publish")).andExpect(status().isNoContent());
        mockMvc.perform(get(STORE + "/products")).andExpect(jsonPath("$.items[0].id").value(productId));
    }

    @Test
    @DisplayName("NFR-1 在庫の変更は、直後の照会に反映される")
    void reflectsStockChange() throws Exception {
        mockMvc.perform(get(STORE + "/products")).andExpect(jsonPath("$.items[0].inStock").value(false));

        inventory.stock(UUID.fromString(variantId), UUID.fromString(productId), 1);

        mockMvc.perform(get(STORE + "/products")).andExpect(jsonPath("$.items[0].inStock").value(true));
        mockMvc.perform(get(STORE + "/products/" + productId)).andExpect(jsonPath("$.variants[0].inStock").value(true));
    }

    @Test
    @DisplayName("税率の一覧のキャッシュの有効期間は 30 秒。税率の変更は、最大 30 秒遅れて反映される")
    void cachesTaxRatesFor30Seconds() throws Exception {
        // キャッシュに 10% の一覧が入る
        mockMvc.perform(get(STORE + "/products")).andExpect(jsonPath("$.items[0].price.inclTax").value(1100));

        // 10 秒後から 8% にする
        var switchAt = NOW.plusSeconds(10).atOffset(ZoneOffset.UTC);
        var body = mockMvc.perform(get("/api/admin/tax-categories/" + taxCategoryId)).andReturn().getResponse().getContentAsString();
        List<String> rateIds = JsonPath.read(body, "$.rates[*].id");
        mockMvc.perform(put("/api/admin/tax-categories/" + taxCategoryId + "/rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 1, "rates": [
                                  {"id": "%s", "ratePercent": 10, "effectiveTo": "%s"},
                                  {"ratePercent": 8, "effectiveFrom": "%s"}]}""".formatted(rateIds.getFirst(), switchAt, switchAt)))
                .andExpect(status().isNoContent());

        // 29 秒後：キャッシュの一覧には 8% の予定がないので、10% のまま
        clock.setInstant(NOW.plus(Duration.ofSeconds(29)));
        mockMvc.perform(get(STORE + "/products")).andExpect(jsonPath("$.items[0].price.inclTax").value(1100));

        // 30 秒後：一覧を取得し直し、8% になる
        clock.setInstant(NOW.plus(Duration.ofSeconds(30)));
        mockMvc.perform(get(STORE + "/products")).andExpect(jsonPath("$.items[0].price.inclTax").value(1080));
    }
}
