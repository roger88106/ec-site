package com.ecsite.module.catalog.presentation;

import com.ecsite.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static com.ecsite.jooq.tax.Tables.TAX_CATEGORY;
import static com.ecsite.jooq.tax.Tables.TAX_RATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品の同時登録のテスト（T309、AC-14）。<br />
 * 別々のトランザクションで同時に登録するため、ロールバックせず、作ったデータを後で消す。
 */
@IntegrationTest
@AutoConfigureMockMvc
class ProductConcurrencyTest {

    private static final int REQUESTS = 2;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DSLContext dsl;

    private String taxCategoryId;

    private String productCode;

    @BeforeEach
    void setUp() throws Exception {
        var body = mockMvc.perform(post("/api/admin/tax-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "同時登録 %s"}""".formatted(UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        taxCategoryId = JsonPath.read(body, "$.id");
        mockMvc.perform(put("/api/admin/tax-categories/" + taxCategoryId + "/rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0, "rates": [{"ratePercent": 10}]}"""))
                .andExpect(status().isNoContent());
        productCode = "CONCURRENT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    @AfterEach
    void cleanUp() {
        dsl.deleteFrom(PRODUCT).where(PRODUCT.PRODUCT_CODE.eq(productCode)).execute();
        dsl.deleteFrom(TAX_RATE).where(TAX_RATE.TAX_CATEGORY_ID.eq(UUID.fromString(taxCategoryId))).execute();
        dsl.deleteFrom(TAX_CATEGORY).where(TAX_CATEGORY.ID.eq(UUID.fromString(taxCategoryId))).execute();
    }

    @Test
    @DisplayName("AC-14 同時に同じ商品品番で登録しても、1 つだけ登録され、残りは 409（500 にならない）")
    void rejectsConcurrentDuplicatedProductCode() throws Exception {
        var ready = new CountDownLatch(REQUESTS);
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(REQUESTS);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < REQUESTS; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return mockMvc.perform(post("/api/admin/products")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"productCode": "%s", "name": "T シャツ", "description": "",
                                             "taxCategoryId": "%s", "priceExclTax": 1000}""".formatted(productCode, taxCategoryId)))
                            .andReturn().getResponse().getStatus();
                }));
            }
            ready.await();
            start.countDown();

            var statuses = new ArrayList<Integer>();
            for (var result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            // try-with-resources（close）は全部の処理の終わりを待つので、途中で失敗すると start を待つスレッドが残って止まる。
            // 割り込んで止める shutdownNow を使う
            executor.shutdownNow();
        }
    }
}
