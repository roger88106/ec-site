package com.ecsite.module.inventory.infrastructure.persistence;

import com.ecsite.Concurrently;
import com.ecsite.IntegrationTest;
import com.ecsite.TestTokens;
import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.inventory.application.model.view.StockAdjustmentView;
import com.ecsite.module.inventory.application.port.StockQueryRepository;
import com.ecsite.module.inventory.domain.InventoryErrorCode;
import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.StockAdjustmentReason;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在庫の調整の統合テスト
 *
 * <p>並行調整を確認するため、調整ごとにコミットする。ロールバックしない。
 */
@IntegrationTest
class JooqStockItemRepositoryTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 理由「その他」の調整に付けるメモ
     */
    private static final String MEMO = "メモ";

    // ============================== Bean ==============================

    @Autowired
    StockItemRepository repository;

    @Autowired
    StockQueryRepository queryRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    /**
     * 調整するバリアントの ID
     */
    private UUID variantId;

    @BeforeEach
    void createStock() {
        variantId = UUID.randomUUID();
        repository.createIfAbsent(variantId, UUID.randomUUID());
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("在庫数の調整")
    class Adjust {

        @Test
        @DisplayName("AC-42 増減量を加え、調整後の在庫数を返す")
        void adjusts() {
            // 操作と確認：+10 で 10
            assertThat(adjust(10, StockAdjustmentReason.ARRIVAL)).isEqualTo(10);

            // 操作と確認：-3 で 7
            assertThat(adjust(-3, StockAdjustmentReason.DAMAGE_OR_LOSS)).isEqualTo(7);
        }

        @Test
        @DisplayName("AC-42 調整すると、在庫数が調整後の値になる")
        void changesQuantity() {
            // 操作：+10 と -3 を行う
            adjust(10, StockAdjustmentReason.ARRIVAL);
            adjust(-3, StockAdjustmentReason.DAMAGE_OR_LOSS);

            // 確認：在庫数が 7
            assertThat(queryRepository.findQuantity(variantId)).contains(7);
        }

        @Test
        @DisplayName("AC-43 在庫数ちょうどを減らすと 0 になる")
        void adjustsToZero() {
            // 準備：在庫数 5
            adjust(5, StockAdjustmentReason.ARRIVAL);

            // 操作と確認：5 減らすと 0
            assertThat(adjust(-5, StockAdjustmentReason.STOCKTAKE)).isZero();
        }

        @Test
        @DisplayName("在庫がまだないバリアントへの調整は見つからない")
        void rejectsUnknownVariant() {
            // 準備：運営者の調整
            var adjustment = StockAdjustment.create(1, StockAdjustmentReason.ARRIVAL, null, TestTokens.OPERATOR, NOW);

            // 操作と確認：在庫のないバリアントへの調整は NOT_FOUND
            assertBusinessError(() -> transactionTemplate.executeWithoutResult(
                    status -> repository.adjust(UUID.randomUUID(), adjustment)), CommonErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("調整後に負になる調整")
    class NegativeResult {

        @Test
        @DisplayName("AC-43 調整後に負になる調整は拒否される")
        void rejectsNegativeResult() {
            // 準備：在庫数 5
            adjust(5, StockAdjustmentReason.ARRIVAL);

            // 操作と確認：6 減らすと INSUFFICIENT_STOCK
            assertBusinessError(() -> adjust(-6, StockAdjustmentReason.STOCKTAKE), InventoryErrorCode.INSUFFICIENT_STOCK);
        }

        @Test
        @DisplayName("AC-43 調整後に負になる調整では、在庫数は変わらない")
        void keepsQuantity() {
            // 準備：在庫数 5
            adjust(5, StockAdjustmentReason.ARRIVAL);

            // 操作と確認：6 減らす調整を試みると、INSUFFICIENT_STOCK で拒否される
            assertBusinessError(() -> adjust(-6, StockAdjustmentReason.STOCKTAKE), InventoryErrorCode.INSUFFICIENT_STOCK);

            // 確認：在庫数が 5 のまま
            assertThat(queryRepository.findQuantity(variantId)).contains(5);
        }

        @Test
        @DisplayName("AC-43 調整後に負になる調整では、履歴は残らない")
        void keepsHistory() {
            // 準備：在庫数 5
            adjust(5, StockAdjustmentReason.ARRIVAL);

            // 操作と確認：6 減らす調整を試みると、INSUFFICIENT_STOCK で拒否される
            assertBusinessError(() -> adjust(-6, StockAdjustmentReason.STOCKTAKE), InventoryErrorCode.INSUFFICIENT_STOCK);

            // 確認：履歴が準備の 1 件のまま
            assertThat(history().items()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("同時の調整")
    class ConcurrentAdjust {

        @Test
        @DisplayName("AC-44 同じバリアントへの調整が同時に行われても、すべて反映される")
        void appliesConcurrentAdjustments() throws Exception {
            // 準備：在庫数 10
            adjust(10, StockAdjustmentReason.ARRIVAL);

            // 操作：+5 と +3 を 10 回ずつ同時に行う
            adjustConcurrently();

            // 確認：10 + (5 + 3) × 10 = 90
            assertThat(queryRepository.findQuantity(variantId)).contains(90);
        }

        @Test
        @DisplayName("AC-44 同時の調整でも、各調整の調整後の在庫数は重ならずに記録される")
        void recordsEachQuantityAfter() throws Exception {
            // 準備：在庫数 10
            adjust(10, StockAdjustmentReason.ARRIVAL);

            // 操作：+5 と +3 を 10 回ずつ同時に行う
            adjustConcurrently();

            // 確認：調整後の在庫数に重なりがない
            assertThat(history(PageRequest.of(1, 100)).items()).extracting(StockAdjustmentView::quantityAfter).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("AC-43、44 同時に減らしても在庫数は負にならず、在庫数の分だけが成功し、残りは在庫不足で拒否される")
        void decrementsConcurrentlyWithoutGoingNegative() throws Exception {
            // 準備：在庫数 5
            adjust(5, StockAdjustmentReason.ARRIVAL);

            // 操作：1 個減らす調整を 10 回同時に行い、成功と在庫不足を数える
            var results = Concurrently.run(10, i -> {
                try {
                    adjust(-1, StockAdjustmentReason.DAMAGE_OR_LOSS);
                    return "成功";
                } catch (BusinessException e) {
                    return e.getErrorDetail().errorCode().code();
                }
            });

            // 確認：5 回成功し、5 回は INSUFFICIENT_STOCK で、在庫数は 0
            assertThat(results).filteredOn("成功"::equals).hasSize(5);
            assertThat(results).filteredOn(InventoryErrorCode.INSUFFICIENT_STOCK.getCode().code()::equals).hasSize(5);
            assertThat(queryRepository.findQuantity(variantId)).contains(0);
        }
    }

    @Nested
    @DisplayName("調整履歴")
    class History {

        @Test
        @DisplayName("AC-45 履歴は、同じ日時なら記録した順の新しい順に並ぶ")
        void listsHistoryNewestFirst() {
            // 準備：同じ日時で +10、-2、+1 の順に調整する
            adjustThreeTimes();

            // 操作：履歴を取得する
            var page = history();

            // 確認：3 件が、記録した順の逆に並ぶ
            assertThat(page.totalItems()).isEqualTo(3);
            assertThat(page.items()).extracting(StockAdjustmentView::delta).containsExactly(1, -2, 10);
        }

        @Test
        @DisplayName("AC-45 履歴は、調整日時の新しい順に並ぶ")
        void listsHistoryByAdjustedAt() {
            // 準備：今の調整の後に、1 分前の日時の調整を記録する
            adjust(10, StockAdjustmentReason.ARRIVAL);
            adjustAt(1, NOW.minusSeconds(60));

            // 操作：履歴を取得する
            var page = history();

            // 確認：記録した順でなく、日時の新しい順に並ぶ
            assertThat(page.items()).extracting(StockAdjustmentView::delta).containsExactly(10, 1);
        }

        @Test
        @DisplayName("AC-45 履歴は、調整ごとの調整後の在庫数を持つ")
        void recordsQuantityAfter() {
            // 準備：+10、-2、+1 の順に調整する
            adjustThreeTimes();

            // 操作：履歴を取得する
            var page = history();

            // 確認：新しい順に 9、8、10
            assertThat(page.items()).extracting(StockAdjustmentView::quantityAfter).containsExactly(9, 8, 10);
        }

        @Test
        @DisplayName("AC-45 履歴は、理由・メモ・調整日時を持つ")
        void recordsReasonMemoAndAdjustedAt() {
            // 準備：+10、-2、+1（その他）の順に調整する
            adjustThreeTimes();

            // 操作：履歴を取得する
            var latest = history().items().getFirst();

            // 確認：最新の調整の理由・メモ・日時
            assertThat(latest.reason()).isEqualTo(StockAdjustmentReason.OTHER);
            assertThat(latest.memo()).isEqualTo(MEMO);
            assertThat(latest.adjustedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("AC-45 履歴をページに分けて取得できる")
        void pagesHistory() {
            // 準備：+1 から +5 まで順に調整する
            for (int i = 1; i <= 5; i++) {
                adjust(i, StockAdjustmentReason.ARRIVAL);
            }

            // 操作：2 件ずつの 2 ページ目を取得する
            var second = history(PageRequest.of(2, 2));

            // 確認：全 5 件のうち、新しい方から 3 件目と 4 件目
            assertThat(second.totalItems()).isEqualTo(5);
            assertThat(second.items()).extracting(StockAdjustmentView::delta).containsExactly(3, 2);
        }

        @Test
        @DisplayName("在庫がまだないバリアントの履歴は見つからない")
        void returnsEmptyHistoryForUnknownVariant() {
            // 操作と確認：空
            assertThat(queryRepository.findAdjustments(UUID.randomUUID(), PageRequest.of(1, 20))).isEmpty();
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 調整をコミットするトランザクションで行う。
     *
     * <p>メモは「その他」のときだけ付ける。
     *
     * @param delta  増減量
     * @param reason 理由
     * @return 調整後の在庫数
     */
    private int adjust(int delta, StockAdjustmentReason reason) {
        var memo = reason == StockAdjustmentReason.OTHER ? MEMO : null;
        var adjustment = StockAdjustment.create(delta, reason, memo, TestTokens.OPERATOR, NOW);
        return transactionTemplate.execute(status -> repository.adjust(variantId, adjustment));
    }

    /**
     * 指定した日時の入荷の調整を、コミットするトランザクションで行う。
     *
     * @param delta      増減量
     * @param adjustedAt 調整日時
     */
    private void adjustAt(int delta, Instant adjustedAt) {
        var adjustment = StockAdjustment.create(delta, StockAdjustmentReason.ARRIVAL, null, TestTokens.OPERATOR, adjustedAt);
        transactionTemplate.execute(status -> repository.adjust(variantId, adjustment));
    }

    /**
     * 同じ日時で、+10（入荷）、-2（破損・紛失）、+1（その他）の順に調整する。
     *
     * <p>調整後の在庫数は、順に 10、8、9。
     */
    private void adjustThreeTimes() {
        adjust(10, StockAdjustmentReason.ARRIVAL);
        adjust(-2, StockAdjustmentReason.DAMAGE_OR_LOSS);
        adjust(1, StockAdjustmentReason.OTHER);
    }

    /**
     * +5 と +3 の入荷を 10 回ずつ、同時に行う。
     *
     * @throws Exception 調整が失敗したとき、または上限の時間までに終わらないとき
     */
    private void adjustConcurrently() throws Exception {
        var deltas = IntStream.range(0, 10).boxed().flatMap(i -> Stream.of(5, 3)).toList();
        Concurrently.run(deltas.size(), i -> adjust(deltas.get(i), StockAdjustmentReason.ARRIVAL));
    }

    /**
     * 調整履歴の最初のページ（20 件）を取得する。
     *
     * @return 調整履歴
     */
    private PageResult<StockAdjustmentView> history() {
        return history(PageRequest.of(1, 20));
    }

    /**
     * 調整履歴を、指定したページで取得する。
     *
     * @param request ページ
     * @return 調整履歴
     */
    private PageResult<StockAdjustmentView> history(PageRequest request) {
        return queryRepository.findAdjustments(variantId, request).orElseThrow();
    }
}
