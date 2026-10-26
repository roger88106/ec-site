package com.ecsite.module.catalog.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品の同時登録のテスト。<br />
 * 別々のトランザクションで同時に登録するため、ロールバックせず、作ったデータも消さない。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class ProductConcurrencyTest {

    private static final int REQUESTS = 2;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    /**
     * テストで使う税区分の ID
     */
    private String taxCategoryId;

    private String productCode;

    @BeforeEach
    void setUp() throws Exception {
        taxCategoryId = new CatalogTestData(mockMvc, clock)
                .taxCategory("同時登録 " + UUID.randomUUID().toString().substring(0, 8), "10").toString();
        productCode = "CONCURRENT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
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
