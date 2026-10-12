package com.ecsite.module.catalog.api;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.module.catalog.infrastructure.directory.TaxRateScheduleCache;
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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    private String taxCategoryId;

    private int sequence;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        taxRateScheduleCache.evict();
        taxCategoryId = createdId(post("/api/admin/tax-categories").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "標準"}"""));
        mockMvc.perform(put("/api/admin/tax-categories/" + taxCategoryId + "/rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0, "rates": [{"ratePercent": 10}]}"""))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("AC-58 商品名または説明にキーワードを含む商品が、新着順で返る。英字の大文字・小文字は区別しない")
    void searchesNameAndDescription() throws Exception {
        var byName = publishedProduct("Organic Cotton T シャツ", "");
        var byDescription = publishedProduct("T シャツ", "オーガニック COTTON を使用");
        publishedProduct("パンツ", "ストレッチ素材");

        var page = api.searchProducts("cotton", PageRequest.of(1, 20));

        assertThat(page.items()).extracting(StoreProductSummary::id).containsExactly(id(byDescription), id(byName));
        assertThat(page.items().getFirst().priceInclTax()).isEqualTo(1100);
    }

    @Test
    @DisplayName("AC-59 C 側に表示されない商品（非公開、販売終了）は検索されない")
    void excludesInvisibleProducts() throws Exception {
        var visible = publishedProduct("綿のシャツ", "");
        var unpublished = publishedProduct("綿のパンツ", "");
        mockMvc.perform(put("/api/admin/products/" + unpublished + "/unpublish")).andExpect(status().isNoContent());
        var discontinued = publishedProduct("綿の靴下", "");
        mockMvc.perform(put("/api/admin/products/" + discontinued + "/discontinue")).andExpect(status().isNoContent());

        var page = api.searchProducts("綿", PageRequest.of(1, 20));

        assertThat(page.items()).extracting(StoreProductSummary::id).containsExactly(id(visible));
        assertThat(page.totalItems()).isEqualTo(1);
    }

    @Test
    @DisplayName("キーワード中の % と _ は、ただの文字として扱う")
    void escapesWildcards() throws Exception {
        var percent = publishedProduct("綿 100% のシャツ", "");
        publishedProduct("綿 100 のシャツ", "");

        assertThat(api.searchProducts("100%", PageRequest.of(1, 20)).items())
                .extracting(StoreProductSummary::id).containsExactly(id(percent));
        assertThat(api.searchProducts("_", PageRequest.of(1, 20)).items()).isEmpty();
    }

    @Test
    @DisplayName("AC-61 ページ単位で取得できる")
    void pages() throws Exception {
        var ids = new String[5];
        for (int i = 0; i < 5; i++) {
            ids[i] = publishedProduct("シャツ " + i, "");
        }

        var page = api.searchProducts("シャツ", PageRequest.of(2, 2));

        assertThat(page.items()).extracting(StoreProductSummary::id).containsExactly(id(ids[2]), id(ids[1]));
        assertThat(page.totalItems()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    // ============================== 補助 ==============================

    /**
     * API の応答の ID（文字列）を UUID にする
     */
    private static UUID id(String value) {
        return UUID.fromString(value);
    }

    /**
     * 要求を送って 201 を確かめ、応答の本文の ID を返す
     */
    private String createdId(RequestBuilder request) throws Exception {
        var body = mockMvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /**
     * バリアントを 1 つ持つ商品を登録して公開し、時計を 1 分進める
     */
    private String publishedProduct(String name, String description) throws Exception {
        sequence++;
        var id = createdId(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content("""
                {"productCode": "S-%d", "name": "%s", "description": "%s", "taxCategoryId": "%s", "priceExclTax": 1000}"""
                .formatted(sequence, name, description, taxCategoryId)));
        mockMvc.perform(post("/api/admin/products/" + id + "/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"variantCode": "S-%d", "axisValues": [], "version": 0}""".formatted(sequence)))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/admin/products/" + id + "/publish")).andExpect(status().isNoContent());
        clock.setInstant(clock.instant().plus(Duration.ofMinutes(1)));
        return id;
    }
}
