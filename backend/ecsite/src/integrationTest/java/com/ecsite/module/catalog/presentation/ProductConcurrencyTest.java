package com.ecsite.module.catalog.presentation;

import com.ecsite.support.MutableClock;
import com.ecsite.support.async.Concurrently;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 商品の同時登録のテスト
 *
 * <p>別々のトランザクションで同時に登録するため、ロールバックせず、作ったデータも消さない。
 * 商品品番はテストごとに変えるので、他のテストのデータと重ならない。
 *
 * <p>同時の登録は {@link Concurrently} で始め、上限の時間までに終わらなければ失敗にする。
 */
@IntegrationTest
class ProductConcurrencyTest {

    // ============================== 定数 ==============================

    /**
     * 同時に登録するリクエストの数
     *
     * <p>2 本では重ならないことがあるので、重なりやすい数にする。
     */
    private static final int REQUESTS = 10;

    // ============================== Bean ==============================

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
        taxCategoryId = new CatalogTestData(mockMvc, clock)
                .taxCategory("同時登録 " + UUID.randomUUID().toString().substring(0, 8), "10").toString();
        productCode = "CONCURRENT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("AC-14 同時に同じ商品品番で登録しても、1 つだけ登録され、残りは 409（500 にならない）")
    void rejectsConcurrentDuplicatedProductCode() throws Exception {
        // 操作：同じ品番の登録を、同時に送る
        var statuses = Concurrently.run(REQUESTS, i -> mockMvc.perform(post(ProductRequests.BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productCode": "%s", "name": "T シャツ", "description": "",
                                 "taxCategoryId": "%s", "priceExclTax": 1000}""".formatted(productCode, taxCategoryId)))
                .andReturn().getResponse().getStatus());

        // 確認：1 つだけが 201 で、残りは全て 409
        assertThat(statuses).containsExactlyInAnyOrderElementsOf(expectedStatuses());
    }

    // ============================== 確認の補助 ==============================

    /**
     * 期待するステータスの一覧を作る。
     *
     * @return 201 が 1 つと、409 が残りの数
     */
    private static List<Integer> expectedStatuses() {
        var expected = new ArrayList<Integer>();
        expected.add(201);
        expected.addAll(Collections.nCopies(REQUESTS - 1, 409));
        return expected;
    }
}
