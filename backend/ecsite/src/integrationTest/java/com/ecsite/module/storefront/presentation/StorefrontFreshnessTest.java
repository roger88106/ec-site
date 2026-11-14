package com.ecsite.module.storefront.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
 * 商品・価格・公開状態・在庫の変更が、1 分以内に C 側へ反映されることの確認
 *
 * <p>店舗の API は商品・価格・公開状態・在庫をキャッシュしないので、変更は 1 分を待たずに次の照会へ反映される。テストはこの次の照会への反映を確認する。
 * 税率の一覧だけはインスタンス内で 30 秒キャッシュするので、その遅れを確認する。
 * 一覧と詳細の項目そのものは {@code StorefrontApiTest} が確認する。
 *
 * <p>テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class StorefrontFreshnessTest {

    // ============================== 定数 ==============================

    /**
     * API のパス
     */
    private static final String STORE = "/api/store";

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 商品の税抜価格
     */
    private static final int PRICE_EXCL_TAX = 1000;

    /**
     * 商品の税込価格（税率 10%）
     */
    private static final int PRICE_INCL_TAX = 1100;

    /**
     * 変えた後の税抜価格
     */
    private static final int CHANGED_PRICE_EXCL_TAX = 2000;

    /**
     * 変えた後の税込価格（税率 10%）
     */
    private static final int CHANGED_PRICE_INCL_TAX = 2200;

    /**
     * 税率を 8% にした後の税込価格
     */
    private static final int REDUCED_PRICE_INCL_TAX = 1080;

    /**
     * 税率を切り替える日時（NOW の 10 秒後）
     */
    private static final Instant RATE_SWITCH_AT = NOW.plusSeconds(10);

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
     * 在庫を作る補助
     */
    private InventoryTestData inventory;

    /**
     * テストで使う税区分の ID
     */
    private String taxCategoryId;

    /**
     * テストで使う商品の ID
     */
    private String productId;

    /**
     * テストで使う商品のバリエーションの ID
     */
    private String variantId;

    /**
     * テストで使うカテゴリの ID
     */
    private String categoryId;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        var catalog = new CatalogTestData(mockMvc, clock);
        taxCategoryId = catalog.taxCategory("標準", "10").toString();
        // 巻き戻さないテストの商品が見えても影響しないよう、一覧はこのテストのカテゴリで絞る
        categoryId = catalog.category("鮮度 " + UUID.randomUUID(), null).toString();
        var product = catalog.product(UUID.fromString(taxCategoryId), "T シャツ", PRICE_EXCL_TAX, List.of(UUID.fromString(categoryId))).publish();
        // 公開で進んだ時計を戻す（税率のキャッシュの確認が、NOW からの秒数で書かれている）
        clock.setInstant(NOW);
        productId = product.id.toString();
        variantId = product.variantIds.getFirst().toString();
        inventory.create(product.variantIds.getFirst(), product.id);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("価格の変更")
    class PriceChange {

        @Test
        @DisplayName("AC-26、NFR-1 価格の変更は、直後の一覧に反映される")
        void reflectsInList() throws Exception {
            // 確認（前提）：変更の前の一覧は、税込 1,100 円
            mockMvc.perform(list()).andExpect(jsonPath("$.items[0].price.inclTax").value(PRICE_INCL_TAX));

            // 操作：税抜価格を変える
            changePrice();

            // 確認：直後の一覧は変えた価格
            mockMvc.perform(list()).andExpect(jsonPath("$.items[0].price.inclTax").value(CHANGED_PRICE_INCL_TAX));
        }

        @Test
        @DisplayName("AC-26、NFR-1 価格の変更は、直後の詳細に反映される")
        void reflectsInDetail() throws Exception {
            // 操作：税抜価格を変える
            changePrice();

            // 確認：直後の詳細は変えた価格
            mockMvc.perform(detail()).andExpect(jsonPath("$.price.inclTax").value(CHANGED_PRICE_INCL_TAX));
        }
    }

    @Nested
    @DisplayName("公開状態の変更")
    class PublicationChange {

        @Test
        @DisplayName("AC-30、NFR-1 非公開にすると、直後の一覧から消える")
        void unpublishReflectsInList() throws Exception {
            // 操作：非公開にする
            unpublish();

            // 確認：一覧は空
            mockMvc.perform(list()).andExpect(jsonPath("$.items").isEmpty());
        }

        @Test
        @DisplayName("AC-30、NFR-1 非公開にすると、直後の詳細が見つからない")
        void unpublishReflectsInDetail() throws Exception {
            // 操作：非公開にする
            unpublish();

            // 確認：見つからない
            mockMvc.perform(detail()).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-29、NFR-1 公開し直すと、直後の一覧に出る")
        void republishReflectsInList() throws Exception {
            // 準備：非公開にする
            unpublish();

            // 操作：公開し直す
            mockMvc.perform(put("/api/admin/products/" + productId + "/publish")).andExpect(status().isNoContent());

            // 確認：一覧に出る
            mockMvc.perform(list()).andExpect(jsonPath("$.items[0].id").value(productId));
        }
    }

    @Nested
    @DisplayName("在庫の変更")
    class StockChange {

        @Test
        @DisplayName("NFR-1 在庫の変更は、直後の一覧に反映される")
        void reflectsInList() throws Exception {
            // 確認（前提）：変更の前の一覧は、在庫なし
            mockMvc.perform(list()).andExpect(jsonPath("$.items[0].inStock").value(false));

            // 操作：1 個入荷する
            stockOne();

            // 確認：直後の一覧は在庫あり
            mockMvc.perform(list()).andExpect(jsonPath("$.items[0].inStock").value(true));
        }

        @Test
        @DisplayName("NFR-1 在庫の変更は、直後の詳細に反映される")
        void reflectsInDetail() throws Exception {
            // 操作：1 個入荷する
            stockOne();

            // 確認：直後の詳細は在庫あり
            mockMvc.perform(detail()).andExpect(jsonPath("$.variants[0].inStock").value(true));
        }
    }

    @Nested
    @DisplayName("税率の一覧のキャッシュ")
    class TaxRateCache {

        @Test
        @DisplayName("税率の一覧のキャッシュの有効期間（30 秒）の間は、税率の変更が反映されない")
        void keepsCachedRateWithin30Seconds() throws Exception {
            // 準備：10% の一覧をキャッシュに入れた後、10 秒後から 8% にする
            cacheRatesAndScheduleSwitch();

            // 操作：29 秒たつ
            clock.setInstant(NOW.plus(Duration.ofSeconds(29)));

            // 確認：キャッシュの一覧には 8% の予定がないので、10% のまま
            mockMvc.perform(list()).andExpect(jsonPath("$.items[0].price.inclTax").value(PRICE_INCL_TAX));
        }

        @Test
        @DisplayName("税率の一覧のキャッシュの有効期間（30 秒）が過ぎると、税率の変更が反映される")
        void reflectsRateAfter30Seconds() throws Exception {
            // 準備：10% の一覧をキャッシュに入れた後、10 秒後から 8% にする
            cacheRatesAndScheduleSwitch();

            // 操作：30 秒たつ
            clock.setInstant(NOW.plus(Duration.ofSeconds(30)));

            // 確認：一覧を取得し直し、8% になる
            mockMvc.perform(list()).andExpect(jsonPath("$.items[0].price.inclTax").value(REDUCED_PRICE_INCL_TAX));
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 管理の API で、商品の税抜価格を {@link #CHANGED_PRICE_EXCL_TAX} に変える。
     */
    private void changePrice() throws Exception {
        mockMvc.perform(put("/api/admin/products/" + productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "T シャツ", "description": "", "taxCategoryId": "%s", "priceExclTax": %d,
                                 "categoryIds": ["%s"], "version": 2}""".formatted(taxCategoryId, CHANGED_PRICE_EXCL_TAX, categoryId)))
                .andExpect(status().isNoContent());
    }

    /**
     * 管理の API で、商品を非公開にする。
     */
    private void unpublish() throws Exception {
        mockMvc.perform(put("/api/admin/products/" + productId + "/unpublish")).andExpect(status().isNoContent());
    }

    /**
     * 商品のバリアントに 1 個入荷する。
     */
    private void stockOne() {
        inventory.stock(UUID.fromString(variantId), UUID.fromString(productId), 1);
    }

    /**
     * 一覧を取得して 10% の税率の一覧をキャッシュに入れ、{@link #RATE_SWITCH_AT} から 8% にする税率の一覧を登録する。
     */
    private void cacheRatesAndScheduleSwitch() throws Exception {
        // キャッシュに 10% の一覧が入る
        mockMvc.perform(list()).andExpect(jsonPath("$.items[0].price.inclTax").value(PRICE_INCL_TAX));

        var switchAt = RATE_SWITCH_AT.atOffset(ZoneOffset.UTC);
        var body = mockMvc.perform(get("/api/admin/tax-categories/" + taxCategoryId)).andReturn().getResponse().getContentAsString();
        List<String> rateIds = JsonPath.read(body, "$.rates[*].id");
        mockMvc.perform(put("/api/admin/tax-categories/" + taxCategoryId + "/rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 1, "rates": [
                                  {"id": "%s", "ratePercent": 10, "effectiveTo": "%s"},
                                  {"ratePercent": 8, "effectiveFrom": "%s"}]}""".formatted(rateIds.getFirst(), switchAt, switchAt)))
                .andExpect(status().isNoContent());
    }

    /**
     * このテストのカテゴリで絞った商品の一覧のリクエストを作る。
     *
     * @return リクエスト
     */
    private MockHttpServletRequestBuilder list() {
        return get(STORE + "/products").param("categoryId", categoryId);
    }

    /**
     * 商品の詳細のリクエストを作る。
     *
     * @return リクエスト
     */
    private MockHttpServletRequestBuilder detail() {
        return get(STORE + "/products/" + productId);
    }
}
