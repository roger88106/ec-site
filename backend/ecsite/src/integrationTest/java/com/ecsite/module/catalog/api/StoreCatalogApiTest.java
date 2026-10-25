package com.ecsite.module.catalog.api;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.common.model.value.Money;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.module.catalog.infrastructure.directory.TaxRateScheduleCache;
import com.ecsite.module.tax.api.TaxRateApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C 側向けの商品照会の統合テスト（T603、AC-52〜54）。<br />
 * 商品は B 側の API で作って公開する。新着順を確認するため、公開するごとに時計を 1 分進める。
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

    /**
     * 税区分・カテゴリ・商品を作る補助
     */
    private CatalogTestData catalog;

    private UUID standard;

    private UUID reduced;

    /**
     * テストごとのカテゴリ（一覧をこのカテゴリで絞って、他のテストの商品が見えないようにする）
     */
    private UUID category;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        catalog = new CatalogTestData(mockMvc, clock);
        taxRateScheduleCache.evict();
        standard = catalog.taxCategory("標準", "10");
        reduced = catalog.taxCategory("軽減", "8");
        category = catalog.category("カテゴリ " + UUID.randomUUID(), null);
    }

    @Test
    @DisplayName("BR-1 税込価格の計算が、Java（税モジュール）と SQL で一致する（端数の境界値を含む）")
    void priceInclTaxAgreesWithJava() throws Exception {
        var odd = catalog.taxCategory("端数", "7.25");
        long[] prices = {1, 9, 10, 11, 99, 100, 101, 999, 1234, 9999, 99_999, 1_000_001};
        var expected = new ArrayList<Long>();
        for (var taxCategory : List.of(standard, reduced, odd)) {
            var rate = taxRateApi.rateAt(taxCategory, NOW).orElseThrow();
            for (var price : prices) {
                catalog.product(taxCategory, "商品", price, List.of(category)).publish();
                expected.add(javaPriceInclTax(price, rate));
            }
        }

        var page = api.listProducts(query(category, null, null, StoreProductSort.NEWEST), PageRequest.of(1, 100));

        // 新着順なので、公開した順の逆
        var actual = new ArrayList<>(page.items().stream().map(summary -> summary.priceInclTax().yen()).toList());
        Collections.reverse(actual);
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("AC-53 税込価格の範囲で絞り込める。下限・上限ともその値を含む")
    void filtersByPriceRange() throws Exception {
        catalog.product(standard, "商品", 900, List.of(category)).publish();   // 990
        var lower = catalog.product(standard, "商品", 1000, List.of(category)).publish();  // 1100
        var upper = catalog.product(reduced, "商品", 2000, List.of(category)).publish();   // 2160
        catalog.product(standard, "商品", 2000, List.of(category)).publish();  // 2200

        var page = api.listProducts(query(category, 1100L, 2160L, StoreProductSort.PRICE_ASC), PageRequest.of(1, 20));

        assertThat(page.items()).extracting(StoreProductSummary::id).containsExactly(lower.id, upper.id);
        assertThat(page.items()).extracting(StoreProductSummary::priceExclTax).containsExactly(Money.of(1000), Money.of(2000));
    }

    @Test
    @DisplayName("AC-54 価格の順は税込価格で決まる。税抜と税込の順が逆になる場合も、税込の順に並ぶ")
    void sortsByPriceInclTax() throws Exception {
        // 税抜は 990 < 1000 だが、税込は 1089（10%）> 1080（8%）
        var cheaperExclTax = catalog.product(standard, "商品", 990, List.of(category)).publish();
        var cheaperInclTax = catalog.product(reduced, "商品", 1000, List.of(category)).publish();

        assertThat(api.listProducts(query(category, null, null, StoreProductSort.PRICE_ASC), PageRequest.of(1, 20)).items())
                .extracting(StoreProductSummary::id).containsExactly(cheaperInclTax.id, cheaperExclTax.id);
        assertThat(api.listProducts(query(category, null, null, StoreProductSort.PRICE_DESC), PageRequest.of(1, 20)).items())
                .extracting(StoreProductSummary::id).containsExactly(cheaperExclTax.id, cheaperInclTax.id);
    }

    @Test
    @DisplayName("AC-54 新着順は初回表示日時の新しい順。再公開しても順位は上がらない")
    void sortsByFirstShownAt() throws Exception {
        var first = catalog.product(standard, "商品", 1000, List.of(category)).publish();
        var second = catalog.product(standard, "商品", 1000, List.of(category)).publish();
        first.unpublish();
        clock.setInstant(clock.instant().plus(Duration.ofMinutes(1)));
        first.publish();

        assertThat(api.listProducts(query(category, null, null, StoreProductSort.NEWEST), PageRequest.of(1, 20)).items())
                .extracting(StoreProductSummary::id).containsExactly(second.id, first.id);
    }

    @Test
    @DisplayName("並びの基準が同じ商品は ID の順になり、ページをまたいでも重複・欠落しない")
    void pagesWithoutDuplicatesOrGaps() throws Exception {
        var ids = new ArrayList<UUID>();
        for (int i = 0; i < 7; i++) {
            ids.add(catalog.product(standard, "商品", 1000, List.of(category)).publish().id);
        }

        var collected = new ArrayList<UUID>();
        for (int page = 1; page <= 3; page++) {
            api.listProducts(query(category, null, null, StoreProductSort.PRICE_ASC), PageRequest.of(page, 3)).items()
                    .forEach(item -> collected.add(item.id()));
        }

        // PostgreSQL の uuid の順は文字列の辞書順（Java の UUID#compareTo は符号付きで比べるので使えない）
        assertThat(collected).doesNotHaveDuplicates()
                .containsExactlyElementsOf(ids.stream().sorted(Comparator.comparing(UUID::toString)).toList());
    }

    @Test
    @DisplayName("AC-51 表示されない商品（非公開、販売終了）は含まれない")
    void excludesInvisibleProducts() throws Exception {
        var visible = catalog.product(standard, "商品", 1000, List.of(category)).publish();
        var unpublished = catalog.product(standard, "商品", 1000, List.of(category)).publish();
        unpublished.unpublish();
        var discontinued = catalog.product(standard, "商品", 1000, List.of(category)).publish();
        discontinued.discontinue();

        var page = api.listProducts(query(category, null, null, StoreProductSort.NEWEST), PageRequest.of(1, 20));

        assertThat(page.items()).extracting(StoreProductSummary::id).containsExactly(visible.id);
        assertThat(page.totalItems()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-52 カテゴリで絞り込める。子孫のカテゴリの商品も含む")
    void filtersByCategoryIncludingDescendants() throws Exception {
        var clothing = catalog.category("衣類", null);
        var tops = catalog.category("トップス", clothing);
        var tShirts = catalog.category("T シャツ", tops);
        var food = catalog.category("食品", null);
        var inClothing = catalog.product(standard, "商品", "", 1000, List.of(clothing)).publish().id;
        var inTShirts = catalog.product(standard, "商品", "", 1000, List.of(tShirts)).publish().id;
        catalog.product(standard, "商品", "", 1000, List.of(food)).publish();

        var page = api.listProducts(query(clothing, null, null, StoreProductSort.NEWEST), PageRequest.of(1, 20));

        assertThat(page.items()).extracting(StoreProductSummary::id).containsExactly(inTShirts, inClothing);
    }

    @Test
    @DisplayName("一覧の各商品は、販売中のバリアントの ID を持つ（在庫の有無の判定に使う）")
    void returnsSellableVariantIds() throws Exception {
        var product = catalog.product(standard, "商品 1", 1000, List.of(category)).publish();

        var item = api.listProducts(query(category, null, null, StoreProductSort.NEWEST), PageRequest.of(1, 20)).items().getFirst();

        assertThat(item.id()).isEqualTo(product.id);
        assertThat(item.name()).isEqualTo("商品 1");
        assertThat(item.sellableVariantIds()).hasSize(1);
    }

    // ============================== 補助 ==============================

    /**
     * C 側の一覧の絞り込みと並び順を作る
     */
    private static StoreProductQuery query(UUID categoryId, Long minPrice, Long maxPrice, StoreProductSort sort) {
        return new StoreProductQuery(categoryId, minPrice == null ? null : Money.of(minPrice),
                maxPrice == null ? null : Money.of(maxPrice), sort);
    }

    /**
     * 税モジュールの税込価格の計算（BR-1）
     */
    private long javaPriceInclTax(long price, BigDecimal rate) {
        return taxRateApi.priceInclTax(Money.of(price), rate).yen();
    }
}
