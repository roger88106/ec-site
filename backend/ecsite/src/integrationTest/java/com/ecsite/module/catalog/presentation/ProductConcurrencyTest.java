package com.ecsite.module.catalog.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.Concurrently;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 商品の同時登録のテスト
 *
 * <p>別々のトランザクションで同時に登録するため、ロールバックせず、作ったデータも消さない。
 */
@IntegrationTest
class ProductConcurrencyTest {

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 同時に登録するリクエストの数
     */
    private static final int REQUESTS = 2;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    /**
     * テストで使う税区分の ID
     */
    private String taxCategoryId;

    /**
     * テストごとに変える商品品番
     */
    private String productCode;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        taxCategoryId = new CatalogTestData(mockMvc, clock)
                .taxCategory("同時登録 " + UUID.randomUUID().toString().substring(0, 8), "10").toString();
        productCode = "CONCURRENT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    @Test
    @DisplayName("AC-14 同時に同じ商品品番で登録しても、1 つだけ登録され、残りは 409（500 にならない）")
    void rejectsConcurrentDuplicatedProductCode() throws Exception {
        var statuses = Concurrently.run(REQUESTS, i -> mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productCode": "%s", "name": "T シャツ", "description": "",
                                 "taxCategoryId": "%s", "priceExclTax": 1000}""".formatted(productCode, taxCategoryId)))
                .andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    }
}
