package com.ecsite.module.catalog.api;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.common.model.Money;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.module.catalog.infrastructure.directory.TaxRateScheduleCache;
import com.ecsite.module.tax.api.TaxRateApi;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C 側向けの商品照会の統合テスト（T603、AC-52〜54）。<br />
 * 商品は B 側の API で作って公開する。新着順を確かめるため、公開するごとに時計を 1 分進める。
 * 税率の一覧のキャッシュは、前のテストのものが残らないよう、テストごとに捨てる。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class StoreCatalogApiTest {

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    StoreCatalogApi api;

    @Autowired
    TaxRateApi taxRateApi;

    @Autowired
    TaxRateScheduleCache taxRateScheduleCache;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    private String standard;

    private String reduced;

    private int sequence;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        taxRateScheduleCache.evict();
        standard = createTaxCategory("標準", "10");
        reduced = createTaxCategory("軽減", "8");
    }

    @Test
    @DisplayName("BR-1 税込価格の計算が、Java（税モジュール）と SQL で一致する（端数の境界値を含む）")
    void priceInclTaxAgreesWithJava() throws Exception {
        var odd = createTaxCategory("端数", "7.25");
        long[] prices = {1, 9, 10, 11, 99, 100, 101, 999, 1234, 9999, 99_999, 1_000_001};
        var expected = new ArrayList<Long>();
        for (var taxCategory : List.of(standard, reduced, odd)) {
            var rate = taxRateApi.rateAt(UUID.fromString(taxCategory), NOW).orElseThrow();
            for (var price : prices) {
                publishedProduct(taxCategory, price);
                expected.add(javaPriceInclTax(price, rate));
            }
        }

        var page = api.listProducts(query(null, null, null, StoreProductSort.NEWEST), PageRequest.of(1, 100));

        // 新着順なので、公開した順の逆
        var actual = new ArrayList<>(page.items().stream().map(StoreProductSummary::priceInclTax).toList());
        Collections.reverse(actual);
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("AC-53 税込価格の範囲で絞り込める。下限・上限ともその値を含む")
    void filtersByPriceRange() throws Exception {
        publishedProduct(standard, 900);   // 990
        var lower = publishedProduct(standard, 1000);  // 1100
        var upper = publishedProduct(reduced, 2000);   // 2160
        publishedProduct(standard, 2000);  // 2200

        var page = api.listProducts(query(null, 1100L, 2160L, StoreProductSort.PRICE_ASC), PageRequest.of(1, 20));

        assertThat(page.items()).extracting(StoreProductSummary::id).containsExactly(id(lower), id(upper));
        assertThat(page.items()).extracting(StoreProductSummary::priceExclTax).containsExactly(1000L, 2000L);
    }

    @Test
    @DisplayName("AC-54 価格の順は税込価格で決まる。税抜と税込の順が逆になる場合も、税込の順に並ぶ")
    void sortsByPriceInclTax() throws Exception {
        // 税抜は 990 < 1000 だが、税込は 1089（10%）> 1080（8%）
        var cheaperExclTax = publishedProduct(standard, 990);
        var cheaperInclTax = publishedProduct(reduced, 1000);

        assertThat(api.listProducts(query(null, null, null, StoreProductSort.PRICE_ASC), PageRequest.of(1, 20)).items())
                .extracting(StoreProductSummary::id).containsExactly(id(cheaperInclTax), id(cheaperExclTax));
        assertThat(api.listProducts(query(null, null, null, StoreProductSort.PRICE_DESC), PageRequest.of(1, 20)).items())
                .extracting(StoreProductSummary::id).containsExactly(id(cheaperExclTax), id(cheaperInclTax));
    }

    @Test
    @DisplayName("AC-54 新着順は初回表示日時の新しい順。再公開しても順位は上がらない")
    void sortsByFirstShownAt() throws Exception {
        var first = publishedProduct(standard, 1000);
        var second = publishedProduct(standard, 1000);
        mockMvc.perform(put("/api/admin/products/" + first + "/unpublish")).andExpect(status().isNoContent());
        clock.setInstant(clock.instant().plus(Duration.ofMinutes(1)));
        mockMvc.perform(put("/api/admin/products/" + first + "/publish")).andExpect(status().isNoContent());

        assertThat(api.listProducts(query(null, null, null, StoreProductSort.NEWEST), PageRequest.of(1, 20)).items())
                .extracting(StoreProductSummary::id).containsExactly(id(second), id(first));
    }

    @Test
    @DisplayName("並びの基準が同じ商品は ID の順になり、ページをまたいでも重複・欠落しない")
    void pagesWithoutDuplicatesOrGaps() throws Exception {
        var ids = new ArrayList<UUID>();
        for (int i = 0; i < 7; i++) {
            ids.add(id(publishedProduct(standard, 1000)));
        }

        var collected = new ArrayList<UUID>();
        for (int page = 1; page <= 3; page++) {
            api.listProducts(query(null, null, null, StoreProductSort.PRICE_ASC), PageRequest.of(page, 3)).items()
                    .forEach(item -> collected.add(item.id()));
        }

        // PostgreSQL の uuid の順は文字列の辞書順（Java の UUID#compareTo は符号付きで比べるので使えない）
        assertThat(collected).doesNotHaveDuplicates()
                .containsExactlyElementsOf(ids.stream().sorted(Comparator.comparing(UUID::toString)).toList());
    }

    @Test
    @DisplayName("AC-51 表示されない商品（非公開、販売終了）は含まれない")
    void excludesInvisibleProducts() throws Exception {
        var visible = publishedProduct(standard, 1000);
        var unpublished = publishedProduct(standard, 1000);
        mockMvc.perform(put("/api/admin/products/" + unpublished + "/unpublish")).andExpect(status().isNoContent());
        var discontinued = publishedProduct(standard, 1000);
        mockMvc.perform(put("/api/admin/products/" + discontinued + "/discontinue")).andExpect(status().isNoContent());

        var page = api.listProducts(query(null, null, null, StoreProductSort.NEWEST), PageRequest.of(1, 20));

        assertThat(page.items()).extracting(StoreProductSummary::id).containsExactly(id(visible));
        assertThat(page.totalItems()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-52 カテゴリで絞り込める。子孫のカテゴリの商品も含む")
    void filtersByCategoryIncludingDescendants() throws Exception {
        var clothing = createCategory("衣類", null);
        var tops = createCategory("トップス", clothing);
        var tShirts = createCategory("T シャツ", tops);
        var food = createCategory("食品", null);
        var inClothing = publishedProduct(standard, 1000, clothing);
        var inTShirts = publishedProduct(standard, 1000, tShirts);
        publishedProduct(standard, 1000, food);

        var page = api.listProducts(query(id(clothing), null, null, StoreProductSort.NEWEST), PageRequest.of(1, 20));

        assertThat(page.items()).extracting(StoreProductSummary::id).containsExactly(id(inTShirts), id(inClothing));
    }

    @Test
    @DisplayName("一覧の各商品は、販売中のバリアントの ID を持つ（在庫の有無の判定に使う）")
    void returnsSellableVariantIds() throws Exception {
        var product = publishedProduct(standard, 1000);

        var item = api.listProducts(query(null, null, null, StoreProductSort.NEWEST), PageRequest.of(1, 20)).items().getFirst();

        assertThat(item.id()).isEqualTo(id(product));
        assertThat(item.name()).isEqualTo("商品 1");
        assertThat(item.sellableVariantIds()).hasSize(1);
    }

    // ============================== 補助 ==============================

    /**
     * C 側の一覧の絞り込みと並び順を作る
     */
    private static StoreProductQuery query(UUID categoryId, Long minPrice, Long maxPrice, StoreProductSort sort) {
        return new StoreProductQuery(categoryId, minPrice, maxPrice, sort);
    }

    /**
     * API の応答の ID（文字列）を UUID にする
     */
    private static UUID id(String value) {
        return UUID.fromString(value);
    }

    /**
     * 税モジュールの税込価格の計算（BR-1）
     */
    private long javaPriceInclTax(long price, BigDecimal rate) {
        return taxRateApi.priceInclTax(Money.of(price), rate).yen();
    }

    /**
     * 要求を送って 201 を確かめ、応答の本文の ID を返す
     */
    private String createdId(RequestBuilder request) throws Exception {
        var body = mockMvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /**
     * 税区分を登録し、期間のない税率を 1 つ設定して、その ID を返す
     */
    private String createTaxCategory(String name, String ratePercent) throws Exception {
        var id = createdId(post("/api/admin/tax-categories").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "%s"}""".formatted(name)));
        mockMvc.perform(put("/api/admin/tax-categories/" + id + "/rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0, "rates": [{"ratePercent": %s}]}""".formatted(ratePercent)))
                .andExpect(status().isNoContent());
        return id;
    }

    /**
     * カテゴリを登録して、その ID を返す。親がなければ最上位
     */
    private String createCategory(String name, String parentId) throws Exception {
        var parent = parentId == null ? "null" : "\"%s\"".formatted(parentId);
        return createdId(post("/api/admin/categories").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "%s", "parentId": %s}""".formatted(name, parent)));
    }

    /**
     * バリアントを 1 つ持つ商品を登録して公開し、時計を 1 分進める
     */
    private String publishedProduct(String taxCategoryId, long price, String... categoryIds) throws Exception {
        sequence++;
        var categories = String.join(", ", Arrays.stream(categoryIds).map("\"%s\""::formatted).toList());
        var id = createdId(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content("""
                {"productCode": "P-%d", "name": "商品 %d", "description": "", "taxCategoryId": "%s",
                 "priceExclTax": %d, "categoryIds": [%s]}""".formatted(sequence, sequence, taxCategoryId, price, categories)));
        mockMvc.perform(post("/api/admin/products/" + id + "/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"variantCode": "P-%d", "axisValues": [], "version": 0}""".formatted(sequence)))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/admin/products/" + id + "/publish")).andExpect(status().isNoContent());
        clock.setInstant(clock.instant().plus(Duration.ofMinutes(1)));
        return id;
    }
}
