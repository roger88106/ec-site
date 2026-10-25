package com.ecsite.module.inventory.presentation;

import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.TestTokens;
import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 在庫 API の受け入れテスト。<br />
 * 在庫はバリアント登録のイベントで作られるが、イベントはコミット後に動くので、ここではリポジトリで直接作る。
 * 現在時刻は 2026-10-08 00:00（日本時間）に固定する。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class InventoryApiTest {

    /**
     * API のパス
     */
    private static final String BASE = "/api/admin/inventory";

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    StockItemRepository stockItemRepository;

    @Autowired
    StockReservationApi stockReservationApi;

    /**
     * 在庫を作る補助
     */
    private InventoryTestData inventory;

    private UUID productId;

    private UUID variantId;

    @BeforeEach
    void setUp() {
        clock.setInstant(NOW);
        productId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        inventory = new InventoryTestData(stockItemRepository, clock);
        inventory.create(variantId, productId);
    }

    @Nested
    @DisplayName("在庫数の取得")
    class GetStock {

        @Test
        @DisplayName("AC-41 新しいバリアントの在庫数は 0")
        void returnsZeroForNewVariant() throws Exception {
            mockMvc.perform(get(BASE + "/variants/" + variantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.variantId").value(variantId.toString()))
                    .andExpect(jsonPath("$.quantity").value(0));
        }

        @Test
        @DisplayName("在庫がまだないバリアントは 404")
        void returnsNotFound() throws Exception {
            mockMvc.perform(get(BASE + "/variants/" + UUID.randomUUID()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("AC-42、43、46 在庫の調整")
    class Adjust {

        @Test
        @DisplayName("AC-42 増減量と理由で調整でき、本文で結果が返る（Location は返さない）")
        void adjusts() throws Exception {
            adjust(variantId, 10, "ARRIVAL", "入荷分")
                    .andExpect(status().isCreated())
                    .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                    .andExpect(jsonPath("$.id").isString())
                    .andExpect(jsonPath("$.delta").value(10))
                    .andExpect(jsonPath("$.reason").value("ARRIVAL"))
                    .andExpect(jsonPath("$.memo").value("入荷分"))
                    .andExpect(jsonPath("$.quantityAfter").value(10))
                    .andExpect(jsonPath("$.adjustedAt").value("2026-10-07T15:00:00Z"));

            mockMvc.perform(get(BASE + "/variants/" + variantId)).andExpect(jsonPath("$.quantity").value(10));
        }

        @Test
        @DisplayName("AC-42 増減量を続けて加えると、調整後の在庫数が加えた値になる（在庫数 10 に +5 と +3 を行うと 18）")
        void addsDeltas() throws Exception {
            adjust(variantId, 10, "ARRIVAL", null).andExpect(status().isCreated());
            adjust(variantId, 5, "ARRIVAL", null).andExpect(status().isCreated());
            adjust(variantId, 3, "ARRIVAL", null).andExpect(jsonPath("$.quantityAfter").value(18));
        }

        @Test
        @DisplayName("AC-43 調整後に負になる調整は 400 で、在庫数は変わらない")
        void rejectsNegativeResult() throws Exception {
            adjust(variantId, 5, "ARRIVAL", null).andExpect(status().isCreated());

            adjust(variantId, -6, "DAMAGE_OR_LOSS", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"));

            mockMvc.perform(get(BASE + "/variants/" + variantId)).andExpect(jsonPath("$.quantity").value(5));
        }

        @Test
        @DisplayName("AC-46 理由がその他でメモがなければ 400")
        void requiresMemoForOther() throws Exception {
            adjust(variantId, 1, "OTHER", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("STOCK_MEMO_REQUIRED"));
            adjust(variantId, 1, "OTHER", "サンプル出荷").andExpect(status().isCreated());
        }

        @Test
        @DisplayName("AC-46 理由が決められた値でなければ 400")
        void rejectsUnknownReason() throws Exception {
            adjust(variantId, 1, "GIFT", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-107 注文に由来する理由は、運営者の調整では選べず 400")
        void rejectsOrderReasons() throws Exception {
            adjust(variantId, 1, "ORDER", null).andExpect(status().isBadRequest());
            adjust(variantId, 1, "ORDER_CANCELLATION", null).andExpect(status().isBadRequest());

            mockMvc.perform(get(BASE + "/variants/" + variantId)).andExpect(jsonPath("$.quantity").value(0));
        }

        @Test
        @DisplayName("AC-42 1 回の増減量が ±1,000,000 を超えれば 400 で、在庫数は変わらない")
        void rejectsDeltaOverOneMillion() throws Exception {
            adjust(variantId, 1_000_001, "ARRIVAL", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
            adjust(variantId, Integer.MAX_VALUE, "ARRIVAL", null).andExpect(status().isBadRequest());

            mockMvc.perform(get(BASE + "/variants/" + variantId)).andExpect(jsonPath("$.quantity").value(0));
        }

        @Test
        @DisplayName("AC-42 運営者の調整で在庫数が 1,000,000 を超えれば 400 で、在庫数は変わらない。1,000,000 ちょうどは調整できる")
        void rejectsQuantityOverOneMillion() throws Exception {
            adjust(variantId, 1_000_000, "ARRIVAL", null).andExpect(jsonPath("$.quantityAfter").value(1_000_000));

            adjust(variantId, 1, "ARRIVAL", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("STOCK_QUANTITY_EXCEEDED"));

            mockMvc.perform(get(BASE + "/variants/" + variantId)).andExpect(jsonPath("$.quantity").value(1_000_000));
        }

        @Test
        @DisplayName("増減量が 0 なら 400")
        void rejectsZeroDelta() throws Exception {
            adjust(variantId, 0, "ARRIVAL", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_STOCK_DELTA"));
        }

        @Test
        @DisplayName("在庫がまだないバリアントへの調整は 404")
        void rejectsUnknownVariant() throws Exception {
            adjust(UUID.randomUUID(), 1, "ARRIVAL", null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("AC-45 調整履歴")
    class History {

        @Test
        @DisplayName("AC-45 新しい順に、日時・増減量・理由・調整後の在庫数をページに分けて返す")
        void listsHistoryNewestFirst() throws Exception {
            adjust(variantId, 10, "ARRIVAL", null).andExpect(status().isCreated());
            adjust(variantId, -2, "DAMAGE_OR_LOSS", null).andExpect(status().isCreated());
            adjust(variantId, 1, "OTHER", "メモ").andExpect(status().isCreated());

            mockMvc.perform(get(BASE + "/variants/" + variantId + "/adjustments").param("page", "1").param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].delta", contains(1, -2)))
                    .andExpect(jsonPath("$.items[*].quantityAfter", contains(9, 8)))
                    .andExpect(jsonPath("$.items[*].reason", contains("OTHER", "DAMAGE_OR_LOSS")))
                    .andExpect(jsonPath("$.items[0].memo").value("メモ"))
                    .andExpect(jsonPath("$.items[1].memo").value(nullValue()))
                    .andExpect(jsonPath("$.items[0].adjustedAt").value("2026-10-07T15:00:00Z"))
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.totalItems").value(3))
                    .andExpect(jsonPath("$.totalPages").value(2));

            mockMvc.perform(get(BASE + "/variants/" + variantId + "/adjustments").param("page", "2").param("size", "2"))
                    .andExpect(jsonPath("$.items[*].delta", contains(10)));
        }

        @Test
        @DisplayName("在庫がまだないバリアントの履歴は 404")
        void returnsNotFound() throws Exception {
            mockMvc.perform(get(BASE + "/variants/" + UUID.randomUUID() + "/adjustments"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("AC-49 商品ごとの在庫数の合計")
    class Summaries {

        @Test
        @DisplayName("商品ごとに在庫数を合計し、指定した順に返す。在庫がまだない商品は 0")
        void sumsQuantitiesByProduct() throws Exception {
            var secondVariant = UUID.randomUUID();
            inventory.create(secondVariant, productId);
            adjust(variantId, 3, "ARRIVAL", null).andExpect(status().isCreated());
            adjust(secondVariant, 4, "ARRIVAL", null).andExpect(status().isCreated());
            var withoutStock = UUID.randomUUID();

            mockMvc.perform(get(BASE + "/stock-summaries").param("productIds", withoutStock + "," + productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].productId", contains(withoutStock.toString(), productId.toString())))
                    .andExpect(jsonPath("$.items[*].totalQuantity", contains(0, 7)));
        }

        @Test
        @DisplayName("商品を指定しなければ 400")
        void requiresProductIds() throws Exception {
            mockMvc.perform(get(BASE + "/stock-summaries"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }
    }

    @Nested
    @DisplayName("AC-142 操作した運営者")
    class Operator {

        @Test
        @DisplayName("AC-142 運営者の調整は、token の運営者の氏名の写しを残し、応答と履歴に出す")
        void recordsOperatorName() throws Exception {
            mockMvc.perform(post(BASE + "/variants/" + variantId + "/adjustments")
                            .with(TestTokens.administrator().name("在庫", "一郎").bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"delta": 3, "reason": "ARRIVAL", "memo": null}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.operatorName").value("在庫 一郎"));

            mockMvc.perform(get(BASE + "/variants/" + variantId + "/adjustments"))
                    .andExpect(jsonPath("$.items[0].operatorName").value("在庫 一郎"));
        }

        @Test
        @DisplayName("AC-142 注文に由来する調整は、運営者の操作でないので空")
        void leavesOrderAdjustmentEmpty() throws Exception {
            stockReservationApi.restore(Map.of(variantId, 2), UUID.randomUUID(), "20261008-000001");

            mockMvc.perform(get(BASE + "/variants/" + variantId + "/adjustments"))
                    .andExpect(jsonPath("$.items[0].reason").value("ORDER_CANCELLATION"))
                    .andExpect(jsonPath("$.items[0].operatorName").value(nullValue()));
        }
    }

    // ============================== 補助 ==============================

    /**
     * 在庫を調整する。メモがなければ null で送る
     */
    private ResultActions adjust(UUID variantId, int delta, String reason, String memo) throws Exception {
        var memoJson = memo == null ? "null" : "\"%s\"".formatted(memo);
        return mockMvc.perform(post(BASE + "/variants/" + variantId + "/adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"delta": %d, "reason": "%s", "memo": %s}""".formatted(delta, reason, memoJson)));
    }
}
