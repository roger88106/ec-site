package com.ecsite.module.inventory.presentation;

import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.InventoryTestData;
import com.ecsite.support.security.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.support.data.InventoryTestData.expectQuantity;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 在庫 API の受け入れテスト
 *
 * <p>在庫はバリアントの登録と同じトランザクションの中で作られるが、在庫の API は商品とバリアントを読まないので、
 * ここでは商品を登録せず、リポジトリで直接作る。テストごとにロールバックする。
 *
 * <p>確認の期待値は、準備した物から取り出さない。複数のテストで使う値は定数にし、1 つのテストの中だけで使う在庫数などは、
 * 準備と確認に同じ数を書く。
 */
@IntegrationTest
@Transactional
class InventoryApiTest {

    // ============================== 定数 ==============================

    /**
     * API のパス
     */
    private static final String BASE = "/api/admin/inventory";

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 入荷の増減量
     */
    private static final int ARRIVAL_DELTA = 10;

    /**
     * 入荷のメモ
     */
    private static final String ARRIVAL_MEMO = "入荷分";

    /**
     * 理由「その他」のメモ
     */
    private static final String OTHER_MEMO = "サンプル出荷";

    /**
     * 履歴を確認する調整の、理由「その他」のメモ
     */
    private static final String HISTORY_MEMO = "メモ";

    /**
     * 運営者の調整の上限
     *
     * <p>1 回の増減量の絶対値と、調整後の在庫数の両方の上限。
     */
    private static final int MANUAL_LIMIT = 1_000_000;

    /**
     * 合計を確認する商品の、1 つ目のバリアントの在庫数
     */
    private static final int FIRST_QUANTITY = 3;

    /**
     * 合計を確認する商品の、2 つ目のバリアントの在庫数
     */
    private static final int SECOND_QUANTITY = 4;

    /**
     * 調整した運営者の姓
     */
    private static final String OPERATOR_FAMILY_NAME = "在庫";

    /**
     * 調整した運営者の名
     */
    private static final String OPERATOR_GIVEN_NAME = "一郎";

    /**
     * キャンセルした注文の注文番号
     */
    private static final String ORDER_NUMBER = "20261008-000001";

    // ============================== Bean ==============================

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

    /**
     * 在庫を作る商品の ID
     */
    private UUID productId;

    /**
     * 調整するバリアントの ID
     */
    private UUID variantId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        inventory = new InventoryTestData(stockItemRepository, clock);
        inventory.create(variantId, productId);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("在庫数の取得")
    class GetStock {

        @Test
        @DisplayName("AC-41 新しいバリアントの在庫数は 0")
        void returnsZeroForNewVariant() throws Exception {
            // 操作と確認：作っただけの在庫の在庫数が 0
            mockMvc.perform(get(BASE + "/variants/" + variantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.variantId").value(variantId.toString()))
                    .andExpect(jsonPath("$.quantity").value(0));
        }

        @Test
        @DisplayName("在庫が作られていないバリアントは 404")
        void returnsNotFound() throws Exception {
            // 操作と確認：NOT_FOUND
            mockMvc.perform(get(BASE + "/variants/" + UUID.randomUUID()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("在庫の調整")
    class Adjust {

        @Test
        @DisplayName("AC-42 増減量と理由で調整すると、本文で調整の結果を返す")
        void adjusts() throws Exception {
            // 操作：在庫数 0 に入荷の調整を行う
            var result = adjust(variantId, ARRIVAL_DELTA, "ARRIVAL", ARRIVAL_MEMO);

            // 確認：調整の結果が返り、Location は返さない
            expectArrivalResult(result);
        }

        @Test
        @DisplayName("AC-42 調整すると、在庫数が調整後の値になる")
        void changesQuantity() throws Exception {
            // 操作：在庫数 0 に入荷の調整を行う
            adjust(variantId, ARRIVAL_DELTA, "ARRIVAL", ARRIVAL_MEMO).andExpect(status().isCreated());

            // 確認：在庫数が増減量の分だけ増えている
            expectQuantity(mockMvc, variantId, ARRIVAL_DELTA);
        }

        @Test
        @DisplayName("AC-42 続けて調整すると、増減量が今の在庫数に加わる")
        void addsDeltas() throws Exception {
            // 準備：在庫数 10 に +5 を加える
            adjust(variantId, 10, "ARRIVAL", null).andExpect(status().isCreated());
            adjust(variantId, 5, "ARRIVAL", null).andExpect(status().isCreated());

            // 操作と確認：さらに +3 を加えると、調整後の在庫数は 18
            adjust(variantId, 3, "ARRIVAL", null).andExpect(jsonPath("$.quantityAfter").value(18));
        }

        @Test
        @DisplayName("増減量が 0 なら 400")
        void rejectsZeroDelta() throws Exception {
            // 操作と確認：INVALID_STOCK_DELTA
            adjust(variantId, 0, "ARRIVAL", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_STOCK_DELTA"));
        }

        @Test
        @DisplayName("在庫が作られていないバリアントへの調整は 404")
        void rejectsUnknownVariant() throws Exception {
            // 操作と確認：NOT_FOUND
            adjust(UUID.randomUUID(), 1, "ARRIVAL", null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("調整の理由とメモ")
    class ReasonAndMemo {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"ARRIVAL", "STOCKTAKE", "DAMAGE_OR_LOSS", "OTHER"})
        @DisplayName("AC-46 運営者が選べる理由のどれでも調整できる")
        void acceptsManualReasons(String reason) throws Exception {
            // 操作と確認：メモ付きで調整でき、選んだ理由が記録される
            adjust(variantId, 1, reason, OTHER_MEMO)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reason").value(reason));
        }

        @Test
        @DisplayName("AC-46 理由が決められた値でなければ 400")
        void rejectsUnknownReason() throws Exception {
            // 操作と確認：INVALID_REQUEST
            adjust(variantId, 1, "GIFT", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-46 理由がその他でメモがなければ 400")
        void requiresMemoForOther() throws Exception {
            // 操作と確認：STOCK_MEMO_REQUIRED
            adjust(variantId, 1, "OTHER", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("STOCK_MEMO_REQUIRED"));
        }

        @Test
        @DisplayName("AC-46 理由がその他でメモがあれば調整できる")
        void acceptsOtherWithMemo() throws Exception {
            // 操作と確認：201
            adjust(variantId, 1, "OTHER", OTHER_MEMO).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("AC-46 上限の 500 文字のメモで調整でき、メモが履歴に残る")
        void acceptsMemoAtMax() throws Exception {
            // 操作と確認：500 文字のメモの調整は 201 で、メモをそのまま返す
            adjust(variantId, 1, "OTHER", "あ".repeat(500))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.memo").value("あ".repeat(500)));
        }

        @Test
        @DisplayName("AC-46 上限を 1 文字超える 501 文字のメモは、入力の誤りの 400 で、在庫数は変わらない")
        void rejectsMemoOverMax() throws Exception {
            // 操作と確認：501 文字のメモの調整は 400 で、メモの欄を示す
            adjust(variantId, 1, "OTHER", "あ".repeat(501))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value("memo"));

            // 確認：在庫数が 0 のまま
            expectQuantity(mockMvc, variantId, 0);
        }

        @Test
        @DisplayName("サロゲートペアの文字を含む 500 文字のメモは、UTF-16 では 1000 でも、調整できる")
        void acceptsSurrogatePairMemoAtMax() throws Exception {
            // 操作と確認：サロゲートペアの文字 500 個（code point では 500）のメモの調整は 201 で、メモをそのまま返す
            adjust(variantId, 1, "OTHER", "𠮷".repeat(500))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.memo").value("𠮷".repeat(500)));
        }

        @Test
        @DisplayName("サロゲートペアの文字を含む 501 文字のメモは、入力の誤りの 400")
        void rejectsSurrogatePairMemoOverMax() throws Exception {
            // 操作と確認：サロゲートペアの文字 501 個のメモの調整は 400 で、メモの欄を示す
            adjust(variantId, 1, "OTHER", "𠮷".repeat(501))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value("memo"));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"ORDER", "ORDER_CANCELLATION"})
        @DisplayName("AC-107 注文に由来する理由は、運営者の調整では選べず 400")
        void rejectsOrderReasons(String reason) throws Exception {
            // 操作と確認：400
            adjust(variantId, 1, reason, null).andExpect(status().isBadRequest());
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"ORDER", "ORDER_CANCELLATION"})
        @DisplayName("AC-107 注文に由来する理由の調整では、在庫数は変わらない")
        void keepsQuantityForOrderReasons(String reason) throws Exception {
            // 操作：注文に由来する理由で調整を送る
            adjust(variantId, 1, reason, null);

            // 確認：在庫数が 0 のまま
            expectQuantity(mockMvc, variantId, 0);
        }
    }

    @Nested
    @DisplayName("調整後に負になる調整")
    class NegativeResult {

        @Test
        @DisplayName("AC-43 調整後に負になる調整は 400")
        void rejectsNegativeResult() throws Exception {
            // 準備：在庫数 5
            adjust(variantId, 5, "ARRIVAL", null).andExpect(status().isCreated());

            // 操作と確認：6 減らすと INSUFFICIENT_STOCK
            adjust(variantId, -6, "DAMAGE_OR_LOSS", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"));
        }

        @Test
        @DisplayName("AC-43 調整後に負になる調整では、在庫数は変わらない")
        void keepsQuantity() throws Exception {
            // 準備：在庫数 5
            adjust(variantId, 5, "ARRIVAL", null).andExpect(status().isCreated());

            // 操作：6 減らす調整を送る
            adjust(variantId, -6, "DAMAGE_OR_LOSS", null);

            // 確認：在庫数が 5 のまま
            expectQuantity(mockMvc, variantId, 5);
        }
    }

    @Nested
    @DisplayName("運営者の調整の上限")
    class ManualLimits {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {MANUAL_LIMIT + 1, -(MANUAL_LIMIT + 1), Integer.MAX_VALUE, Integer.MIN_VALUE})
        @DisplayName("AC-42 1 回の増減量が ±1,000,000 を超えれば 400")
        void rejectsDeltaOverOneMillion(int delta) throws Exception {
            // 操作と確認：上限を超える増減量は INVALID_REQUEST
            adjust(variantId, delta, "ARRIVAL", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {MANUAL_LIMIT + 1, -(MANUAL_LIMIT + 1), Integer.MAX_VALUE, Integer.MIN_VALUE})
        @DisplayName("AC-42 1 回の増減量が ±1,000,000 を超える調整では、在庫数は変わらない")
        void keepsQuantityWhenDeltaOverOneMillion(int delta) throws Exception {
            // 操作：上限を超える増減量の調整を送る
            adjust(variantId, delta, "ARRIVAL", null);

            // 確認：在庫数が 0 のまま
            expectQuantity(mockMvc, variantId, 0);
        }

        @Test
        @DisplayName("AC-42 調整後の在庫数が 1,000,000 ちょうどなら調整できる")
        void acceptsQuantityUpToOneMillion() throws Exception {
            // 操作と確認：在庫数 0 に上限ちょうどを加えると、調整後の在庫数が上限
            adjust(variantId, MANUAL_LIMIT, "ARRIVAL", null).andExpect(jsonPath("$.quantityAfter").value(MANUAL_LIMIT));
        }

        @Test
        @DisplayName("AC-42 1 回の増減量は、下限の -1,000,000 ちょうどまで調整できる")
        void acceptsDeltaAtNegativeLimit() throws Exception {
            // 準備：在庫数を上限ちょうどにする
            adjust(variantId, MANUAL_LIMIT, "ARRIVAL", null).andExpect(status().isCreated());

            // 操作と確認：-1,000,000 の調整は 201 で、調整後の在庫数が 0
            adjust(variantId, -MANUAL_LIMIT, "STOCKTAKE", null)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.quantityAfter").value(0));
        }

        @Test
        @DisplayName("AC-42 運営者の調整で在庫数が 1,000,000 を超えれば 400")
        void rejectsQuantityOverOneMillion() throws Exception {
            // 準備：在庫数を上限ちょうどにする
            adjust(variantId, MANUAL_LIMIT, "ARRIVAL", null).andExpect(status().isCreated());

            // 操作と確認：1 加えると STOCK_QUANTITY_EXCEEDED
            adjust(variantId, 1, "ARRIVAL", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("STOCK_QUANTITY_EXCEEDED"));
        }

        @Test
        @DisplayName("AC-42 運営者の調整で在庫数が 1,000,000 を超える調整では、在庫数は変わらない")
        void keepsQuantityWhenQuantityOverOneMillion() throws Exception {
            // 準備：在庫数を上限ちょうどにする
            adjust(variantId, MANUAL_LIMIT, "ARRIVAL", null).andExpect(status().isCreated());

            // 操作：1 加える調整を送る
            adjust(variantId, 1, "ARRIVAL", null);

            // 確認：在庫数が上限のまま
            expectQuantity(mockMvc, variantId, MANUAL_LIMIT);
        }

        @Test
        @DisplayName("注文のキャンセルで上限を超えた在庫への、運営者の減らす調整は、調整後も上限を超えるなら 400 で、在庫数は変わらない")
        void rejectsDecreaseThatLeavesQuantityOverOneMillion() throws Exception {
            // 準備：上限ちょうどの在庫を、注文で 10 減らし、運営者が 10 増やし、注文のキャンセルで 10 戻して 1,000,010 にする
            adjust(variantId, MANUAL_LIMIT, "ARRIVAL", null).andExpect(status().isCreated());
            var holderId = UUID.randomUUID();
            var orderId = UUID.randomUUID();
            var quantities = Map.of(variantId, 10);
            stockReservationApi.reserve(holderId, quantities, clock.instant().plusSeconds(600));
            stockReservationApi.commit(holderId, quantities, orderId, ORDER_NUMBER);
            adjust(variantId, 10, "ARRIVAL", null).andExpect(status().isCreated());
            stockReservationApi.restore(quantities, orderId, ORDER_NUMBER);
            expectQuantity(mockMvc, variantId, MANUAL_LIMIT + 10);

            // 操作と確認：-5 の調整（調整後 1,000,005）は STOCK_QUANTITY_EXCEEDED
            adjust(variantId, -5, "STOCKTAKE", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("STOCK_QUANTITY_EXCEEDED"));

            // 確認：在庫数が 1,000,010 のまま
            expectQuantity(mockMvc, variantId, MANUAL_LIMIT + 10);
        }

        @Test
        @DisplayName("注文のキャンセルで上限を超えた在庫への、運営者の減らす調整は、調整後が上限以下なら成功する")
        void acceptsDecreaseThatLeavesQuantityAtOneMillion() throws Exception {
            // 準備：上限ちょうどの在庫を、注文で 10 減らし、運営者が 10 増やし、注文のキャンセルで 10 戻して 1,000,010 にする
            adjust(variantId, MANUAL_LIMIT, "ARRIVAL", null).andExpect(status().isCreated());
            var holderId = UUID.randomUUID();
            var orderId = UUID.randomUUID();
            var quantities = Map.of(variantId, 10);
            stockReservationApi.reserve(holderId, quantities, clock.instant().plusSeconds(600));
            stockReservationApi.commit(holderId, quantities, orderId, ORDER_NUMBER);
            adjust(variantId, 10, "ARRIVAL", null).andExpect(status().isCreated());
            stockReservationApi.restore(quantities, orderId, ORDER_NUMBER);

            // 操作と確認：-10 の調整（調整後 1,000,000）は 201 で、調整後の在庫数が上限
            adjust(variantId, -10, "STOCKTAKE", null)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.quantityAfter").value(MANUAL_LIMIT));
        }
    }

    @Nested
    @DisplayName("調整履歴")
    class History {

        @Test
        @DisplayName("AC-45 履歴は新しい順に、日時・増減量・理由・調整後の在庫数を返す")
        void listsHistoryNewestFirst() throws Exception {
            // 準備：+10、-2、+1（その他）の順に調整する
            adjustThreeTimes();

            // 操作：1 ページ目（2 件）を取得する
            var page = mockMvc.perform(get(BASE + "/variants/" + variantId + "/adjustments").param("page", "1").param("size", "2"));

            // 確認：後の 2 件が新しい順に並ぶ
            expectNewestTwoAdjustments(page);
        }

        @Test
        @DisplayName("AC-45 履歴はページに分けて返す")
        void pagesHistory() throws Exception {
            // 準備：3 回調整する
            adjustThreeTimes();

            // 操作と確認：1 ページ目に、ページの情報が付く
            mockMvc.perform(get(BASE + "/variants/" + variantId + "/adjustments").param("page", "1").param("size", "2"))
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.totalItems").value(3))
                    .andExpect(jsonPath("$.totalPages").value(2));

            // 操作と確認：2 ページ目に、最も古い調整が残る
            mockMvc.perform(get(BASE + "/variants/" + variantId + "/adjustments").param("page", "2").param("size", "2"))
                    .andExpect(jsonPath("$.items[*].delta", contains(10)));
        }

        @Test
        @DisplayName("在庫が作られていないバリアントの履歴は 404")
        void returnsNotFound() throws Exception {
            // 操作と確認：NOT_FOUND
            mockMvc.perform(get(BASE + "/variants/" + UUID.randomUUID() + "/adjustments"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("商品ごとの在庫数の合計")
    class Summaries {

        @Test
        @DisplayName("AC-49 商品ごとに、バリアントの在庫数を合計する")
        void sumsQuantitiesByProduct() throws Exception {
            // 準備：商品の 2 つのバリアントに在庫を入れる
            stockTwoVariants();

            // 操作と確認：合計が 2 つのバリアントの在庫数の和
            mockMvc.perform(get(BASE + "/stock-summaries").param("productIds", productId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].productId", contains(productId.toString())))
                    .andExpect(jsonPath("$.items[*].totalQuantity", contains(FIRST_QUANTITY + SECOND_QUANTITY)));
        }

        @Test
        @DisplayName("AC-49 在庫がまだない商品の合計は 0")
        void returnsZeroForProductWithoutStock() throws Exception {
            // 準備：在庫のない商品
            var withoutStock = UUID.randomUUID();

            // 操作と確認：合計が 0
            mockMvc.perform(get(BASE + "/stock-summaries").param("productIds", withoutStock.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].productId", contains(withoutStock.toString())))
                    .andExpect(jsonPath("$.items[*].totalQuantity", contains(0)));
        }

        @Test
        @DisplayName("合計は、指定した商品の順に返す")
        void keepsRequestedOrder() throws Exception {
            // 準備：在庫のある商品と、在庫のない商品
            stockTwoVariants();
            var withoutStock = UUID.randomUUID();

            // 操作と確認：在庫のない商品を先に指定すると、その順で、それぞれの合計が返る
            mockMvc.perform(get(BASE + "/stock-summaries").param("productIds", withoutStock + "," + productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].productId", contains(withoutStock.toString(), productId.toString())))
                    .andExpect(jsonPath("$.items[*].totalQuantity", contains(0, FIRST_QUANTITY + SECOND_QUANTITY)));
        }

        @Test
        @DisplayName("商品の指定に空の要素があれば 400")
        void rejectsNullProductId() throws Exception {
            // 操作と確認：先頭の要素が空（null になる）なら、INVALID_REQUEST
            mockMvc.perform(get(BASE + "/stock-summaries").param("productIds", "," + productId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("商品を指定しなければ 400")
        void requiresProductIds() throws Exception {
            // 操作と確認：INVALID_REQUEST
            mockMvc.perform(get(BASE + "/stock-summaries"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }
    }

    @Nested
    @DisplayName("操作した運営者")
    class Operator {

        @Test
        @DisplayName("運営者の調整は、token の運営者の氏名の写しを応答に出す")
        void returnsOperatorName() throws Exception {
            // 操作と確認：応答に運営者の氏名が出る
            adjustAsNamedOperator().andExpect(jsonPath("$.operatorName").value(operatorName()));
        }

        @Test
        @DisplayName("運営者の調整は、token の運営者の氏名の写しを残し、履歴に出す")
        void recordsOperatorName() throws Exception {
            // 準備：氏名のある運営者が調整する
            adjustAsNamedOperator();

            // 操作と確認：履歴に運営者の氏名が出る
            mockMvc.perform(get(BASE + "/variants/" + variantId + "/adjustments"))
                    .andExpect(jsonPath("$.items[0].operatorName").value(operatorName()));
        }

        @Test
        @DisplayName("姓と名をつなぐと 255 文字を超える運営者の調整は、成功し、履歴の氏名を 255 文字に切り詰める")
        void truncatesLongOperatorName() throws Exception {
            // 準備：つなぐと 301 文字になる姓と名
            var familyName = "姓".repeat(200);
            var givenName = "名".repeat(100);

            // 操作と確認：調整が成功する
            adjustAs(familyName, givenName).andExpect(status().isCreated());

            // 確認：履歴の氏名は、先頭の 255 文字
            mockMvc.perform(get(BASE + "/variants/" + variantId + "/adjustments"))
                    .andExpect(jsonPath("$.items[0].operatorName").value("姓".repeat(200) + " " + "名".repeat(54)));
        }

        @Test
        @DisplayName("サロゲートペアの文字を含む氏名は、255 code point に切り詰め、文字の途中で切らない")
        void truncatesOperatorNameByCodePoint() throws Exception {
            // 準備：1 文字が UTF-16 で 2 つ分の文字を含み、つなぐと 301 code point になる姓と名
            var familyName = "𠮷".repeat(200);
            var givenName = "𠮷".repeat(100);

            // 操作と確認：調整が成功する
            adjustAs(familyName, givenName).andExpect(status().isCreated());

            // 確認：履歴の氏名は、先頭の 255 code point
            mockMvc.perform(get(BASE + "/variants/" + variantId + "/adjustments"))
                    .andExpect(jsonPath("$.items[0].operatorName")
                            .value("𠮷".repeat(200) + " " + "𠮷".repeat(54)));
        }

        @Test
        @DisplayName("注文に由来する調整は、運営者の操作でないので空")
        void leavesOrderAdjustmentEmpty() throws Exception {
            // 準備：注文のキャンセルで在庫を戻す
            stockReservationApi.restore(Map.of(variantId, 2), UUID.randomUUID(), ORDER_NUMBER);

            // 操作：履歴を取得する
            var history = mockMvc.perform(get(BASE + "/variants/" + variantId + "/adjustments"));

            // 確認（前提）：最新の履歴が注文のキャンセルの調整
            history.andExpect(jsonPath("$.items[0].reason").value("ORDER_CANCELLATION"));

            // 確認：運営者の氏名が空
            history.andExpect(jsonPath("$.items[0].operatorName").value(nullValue()));
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 在庫数 0 への入荷の調整の応答を確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>状態が 201</li>
     *   <li>Location を返さない</li>
     *   <li>ID が文字列</li>
     *   <li>増減量</li>
     *   <li>理由が「入荷」</li>
     *   <li>メモ</li>
     *   <li>調整後の在庫数が、在庫数 0 に増減量を加えた値</li>
     *   <li>調整日時が現在時刻</li>
     * </ul>
     *
     * @param result 調整の応答
     * @throws Exception 応答の読み取りに失敗したとき
     */
    private static void expectArrivalResult(ResultActions result) throws Exception {
        result.andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.delta").value(ARRIVAL_DELTA))
                .andExpect(jsonPath("$.reason").value("ARRIVAL"))
                .andExpect(jsonPath("$.memo").value(ARRIVAL_MEMO))
                .andExpect(jsonPath("$.quantityAfter").value(ARRIVAL_DELTA))
                .andExpect(jsonPath("$.adjustedAt").value(NOW.toString()));
    }

    /**
     * 3 回の調整（{@link #adjustThreeTimes}）の履歴の 1 ページ目（2 件）を確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態が 200</li>
     *   <li>増減量が新しい順（+1、-2）</li>
     *   <li>調整後の在庫数が新しい順（9、8）</li>
     *   <li>理由が新しい順（その他、破損・紛失）</li>
     *   <li>最新の調整のメモ</li>
     *   <li>2 番目の調整のメモがない</li>
     *   <li>最新の調整の日時が現在時刻</li>
     * </ul>
     *
     * @param page 履歴の 1 ページ目の応答
     * @throws Exception 応答の読み取りに失敗したとき
     */
    private static void expectNewestTwoAdjustments(ResultActions page) throws Exception {
        page.andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].delta", contains(1, -2)))
                .andExpect(jsonPath("$.items[*].quantityAfter", contains(9, 8)))
                .andExpect(jsonPath("$.items[*].reason", contains("OTHER", "DAMAGE_OR_LOSS")))
                .andExpect(jsonPath("$.items[0].memo").value(HISTORY_MEMO))
                .andExpect(jsonPath("$.items[1].memo").value(nullValue()))
                .andExpect(jsonPath("$.items[0].adjustedAt").value(NOW.toString()));
    }

    // ============================== 準備の補助 ==============================

    /**
     * 在庫を調整する。
     *
     * @param variantId 調整するバリアントの ID
     * @param delta     増減量
     * @param reason    理由
     * @param memo      メモ。なければ null（JSON の null で送る）
     * @return 調整の応答
     * @throws Exception API の呼び出しに失敗したとき
     */
    private ResultActions adjust(UUID variantId, int delta, String reason, String memo) throws Exception {
        var memoJson = memo == null ? "null" : "\"%s\"".formatted(memo);
        return mockMvc.perform(post(BASE + "/variants/" + variantId + "/adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"delta": %d, "reason": "%s", "memo": %s}""".formatted(delta, reason, memoJson)));
    }

    /**
     * 調整するバリアントを、+10（入荷）、-2（破損・紛失）、+1（その他）の順に調整する。
     *
     * <p>調整後の在庫数は、順に 10、8、9。
     *
     * @throws Exception API の呼び出しに失敗したとき
     */
    private void adjustThreeTimes() throws Exception {
        adjust(variantId, 10, "ARRIVAL", null).andExpect(status().isCreated());
        adjust(variantId, -2, "DAMAGE_OR_LOSS", null).andExpect(status().isCreated());
        adjust(variantId, 1, "OTHER", HISTORY_MEMO).andExpect(status().isCreated());
    }

    /**
     * 商品の 2 つのバリアントに、入荷で在庫を入れる。
     *
     * <p>1 つ目は調整するバリアント、2 つ目は新しく作るバリアント。
     *
     * @throws Exception API の呼び出しに失敗したとき
     */
    private void stockTwoVariants() throws Exception {
        var secondVariant = UUID.randomUUID();
        inventory.create(secondVariant, productId);
        adjust(variantId, FIRST_QUANTITY, "ARRIVAL", null).andExpect(status().isCreated());
        adjust(secondVariant, SECOND_QUANTITY, "ARRIVAL", null).andExpect(status().isCreated());
    }

    /**
     * 氏名のある運営者の token で、入荷の調整を行う。
     *
     * @return 調整の応答（201 を確認済み）
     * @throws Exception API の呼び出しに失敗したとき
     */
    private ResultActions adjustAsNamedOperator() throws Exception {
        return mockMvc.perform(post(BASE + "/variants/" + variantId + "/adjustments")
                        .with(TestTokens.administrator().name(OPERATOR_FAMILY_NAME, OPERATOR_GIVEN_NAME).bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"delta": 3, "reason": "ARRIVAL", "memo": null}"""))
                .andExpect(status().isCreated());
    }

    /**
     * 指定した姓と名の運営者の token で、入荷の調整を行う。
     *
     * @param familyName 姓
     * @param givenName  名
     * @return 調整の応答
     * @throws Exception API の呼び出しに失敗したとき
     */
    private ResultActions adjustAs(String familyName, String givenName) throws Exception {
        return mockMvc.perform(post(BASE + "/variants/" + variantId + "/adjustments")
                .with(TestTokens.administrator().name(familyName, givenName).bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"delta": 3, "reason": "ARRIVAL", "memo": null}"""));
    }

    /**
     * 調整した運営者の氏名の写し
     *
     * @return 姓と名を空白でつないだ氏名
     */
    private static String operatorName() {
        return OPERATOR_FAMILY_NAME + " " + OPERATOR_GIVEN_NAME;
    }
}
