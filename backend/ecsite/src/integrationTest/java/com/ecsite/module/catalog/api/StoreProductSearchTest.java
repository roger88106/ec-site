package com.ecsite.module.catalog.api;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.common.model.value.Money;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.module.catalog.infrastructure.directory.TaxRateScheduleCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * キーワード検索の統合テスト（T605、AC-58〜61）。<br />
 * 商品は B 側の API で作って公開する。公開するごとに時計を 1 分進める（並びは新着順で固定）。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class StoreProductSearchTest {

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    StoreCatalogApi api;

    @Autowired
    TaxRateScheduleCache taxRateScheduleCache;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    private UUID taxCategoryId;

    private CatalogTestData catalog;

    /**
     * テストごとの印（商品名に入れて、検索を自分の商品に絞る。他の語と重ならない英数字）
     */
    private String mark;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        mark = "mk" + UUID.randomUUID().toString().replace("-", "");
        taxRateScheduleCache.evict();
        catalog = new CatalogTestData(mockMvc, clock);
        taxCategoryId = catalog.taxCategory("標準", "10");
    }

    @Test
    @DisplayName("AC-58 商品名または説明にキーワードを含む商品が、新着順で返る。英字の大文字・小文字は区別しない")
    void searchesNameAndDescription() throws Exception {
        var byName = catalog.product(taxCategoryId, "Organic " + mark + "Cotton T シャツ", "", 1000, List.of()).publish();
        var byDescription = catalog.product(taxCategoryId, "T シャツ", "オーガニック " + mark + "COTTON を使用", 1000, List.of()).publish();
        catalog.product(taxCategoryId, "パンツ", "ストレッチ素材", 1000, List.of()).publish();

        var page = api.searchProducts(mark + "cotton", PageRequest.of(1, 20));

        assertThat(page.items()).extracting(StoreProductSummary::id).containsExactly(byDescription.id, byName.id);
        assertThat(page.items().getFirst().priceInclTax()).isEqualTo(Money.of(1100));
    }

    @Test
    @DisplayName("AC-59 C 側に表示されない商品（非公開、販売終了）は検索されない")
    void excludesInvisibleProducts() throws Exception {
        var visible = catalog.product(taxCategoryId, mark + "綿のシャツ", "", 1000, List.of()).publish();
        var unpublished = catalog.product(taxCategoryId, mark + "綿のパンツ", "", 1000, List.of()).publish();
        unpublished.unpublish();
        var discontinued = catalog.product(taxCategoryId, mark + "綿の靴下", "", 1000, List.of()).publish();
        discontinued.discontinue();

        var page = api.searchProducts(mark + "綿", PageRequest.of(1, 20));

        assertThat(page.items()).extracting(StoreProductSummary::id).containsExactly(visible.id);
        assertThat(page.totalItems()).isEqualTo(1);
    }

    @Test
    @DisplayName("キーワード中の % と _ は、ただの文字として扱う")
    void escapesWildcards() throws Exception {
        var percent = catalog.product(taxCategoryId, "綿 " + mark + "100% のシャツ", "", 1000, List.of()).publish();
        catalog.product(taxCategoryId, "綿 " + mark + "100 のシャツ", "", 1000, List.of()).publish();

        assertThat(api.searchProducts(mark + "100%", PageRequest.of(1, 20)).items())
                .extracting(StoreProductSummary::id).containsExactly(percent.id);
        assertThat(api.searchProducts(mark + "_", PageRequest.of(1, 20)).items()).isEmpty();
    }

    @Test
    @DisplayName("AC-61 ページ単位で取得できる")
    void pages() throws Exception {
        var ids = new CatalogTestData.TestProduct[5];
        for (int i = 0; i < 5; i++) {
            ids[i] = catalog.product(taxCategoryId, mark + "シャツ " + i, "", 1000, List.of()).publish();
        }

        var page = api.searchProducts(mark + "シャツ", PageRequest.of(2, 2));

        assertThat(page.items()).extracting(StoreProductSummary::id).containsExactly(ids[2].id, ids[1].id);
        assertThat(page.totalItems()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
    }
}
