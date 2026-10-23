package com.ecsite.module.inventory.infrastructure.persistence;

import com.ecsite.IntegrationTest;
import com.ecsite.TestTokens;
import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.inventory.application.model.view.StockAdjustmentView;
import com.ecsite.module.inventory.application.port.StockQueryRepository;
import com.ecsite.module.inventory.domain.InventoryErrorCode;
import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.StockAdjustmentReason;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static com.ecsite.jooq.inventory.Tables.STOCK_ITEM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在庫の調整の統合テスト（T406）。<br />
 * 並行調整（AC-44）を確認するため、調整ごとにコミットする。ロールバックしないので、作ったデータを後で消す。
 */
@IntegrationTest
class JooqStockItemRepositoryTest {

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    StockItemRepository repository;

    @Autowired
    StockQueryRepository queryRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    DSLContext dsl;

    private UUID productId;

    private UUID variantId;

    @BeforeEach
    void createStock() {
        productId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        repository.createIfAbsent(variantId, productId);
    }

    @AfterEach
    void cleanUp() {
        dsl.deleteFrom(STOCK_ITEM).where(STOCK_ITEM.PRODUCT_ID.eq(productId)).execute();
    }

    @Test
    @DisplayName("AC-42 増減量を加え、調整後の在庫数を返す")
    void adjusts() {
        assertThat(adjust(10, StockAdjustmentReason.ARRIVAL)).isEqualTo(10);
        assertThat(adjust(-3, StockAdjustmentReason.DAMAGE_OR_LOSS)).isEqualTo(7);

        assertThat(queryRepository.findQuantity(variantId)).contains(7);
    }

    @Test
    @DisplayName("AC-43 調整後に負になる調整は拒否され、在庫数も履歴も変わらない")
    void rejectsNegativeResult() {
        adjust(5, StockAdjustmentReason.ARRIVAL);

        assertBusinessError(() -> adjust(-6, StockAdjustmentReason.STOCKTAKE), InventoryErrorCode.INSUFFICIENT_STOCK);

        assertThat(queryRepository.findQuantity(variantId)).contains(5);
        assertThat(history().items()).hasSize(1);
    }

    @Test
    @DisplayName("AC-43 在庫数ちょうどを減らすと 0 になる")
    void adjustsToZero() {
        adjust(5, StockAdjustmentReason.ARRIVAL);

        assertThat(adjust(-5, StockAdjustmentReason.STOCKTAKE)).isZero();
    }

    @Test
    @DisplayName("在庫がまだないバリアントへの調整は見つからない")
    void rejectsUnknownVariant() {
        var adjustment = StockAdjustment.create(1, StockAdjustmentReason.ARRIVAL, null, TestTokens.OPERATOR, NOW);

        assertBusinessError(() -> transactionTemplate.executeWithoutResult(
                status -> repository.adjust(UUID.randomUUID(), adjustment)), CommonErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("AC-44 同じバリアントへの調整が同時に行われても、すべて反映される")
    void appliesConcurrentAdjustments() throws Exception {
        adjust(10, StockAdjustmentReason.ARRIVAL);
        // +5 と +3 を 10 回ずつ同時に行う：10 + (5 + 3) × 10 = 90
        var deltas = new ArrayList<Integer>();
        for (int i = 0; i < 10; i++) {
            deltas.add(5);
            deltas.add(3);
        }

        var ready = new CountDownLatch(deltas.size());
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(deltas.size());
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (var delta : deltas) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return adjust(delta, StockAdjustmentReason.ARRIVAL);
                }));
            }
            ready.await();
            start.countDown();
            for (var result : results) {
                result.get();
            }
        } finally {
            // try-with-resources（close）は全部の処理の終わりを待つので、途中で失敗すると start を待つスレッドが残って止まる。
            // 割り込んで止める shutdownNow を使う
            executor.shutdownNow();
        }

        assertThat(queryRepository.findQuantity(variantId)).contains(90);
        // 各調整の調整後の在庫数は、重ならずに 1 回ずつ記録される
        assertThat(history(PageRequest.of(1, 100)).items()).extracting(StockAdjustmentView::quantityAfter).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("AC-45 履歴は新しい順で、調整後の在庫数が記録される。同じ日時なら記録した順の新しい順")
    void listsHistoryNewestFirst() {
        adjust(10, StockAdjustmentReason.ARRIVAL);
        adjust(-2, StockAdjustmentReason.DAMAGE_OR_LOSS);
        adjust(1, StockAdjustmentReason.OTHER);

        var page = history();

        assertThat(page.totalItems()).isEqualTo(3);
        assertThat(page.items()).extracting(StockAdjustmentView::delta).containsExactly(1, -2, 10);
        assertThat(page.items()).extracting(StockAdjustmentView::quantityAfter).containsExactly(9, 8, 10);
        assertThat(page.items().getFirst().reason()).isEqualTo(StockAdjustmentReason.OTHER);
        assertThat(page.items().getFirst().memo()).isEqualTo("メモ");
        assertThat(page.items().getFirst().adjustedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("AC-45 履歴をページに分けて取得できる")
    void pagesHistory() {
        for (int i = 1; i <= 5; i++) {
            adjust(i, StockAdjustmentReason.ARRIVAL);
        }

        var second = history(PageRequest.of(2, 2));

        assertThat(second.totalItems()).isEqualTo(5);
        assertThat(second.items()).extracting(StockAdjustmentView::delta).containsExactly(3, 2);
    }

    @Test
    @DisplayName("在庫がまだないバリアントの履歴は見つからない")
    void returnsEmptyHistoryForUnknownVariant() {
        assertThat(queryRepository.findAdjustments(UUID.randomUUID(), PageRequest.of(1, 20))).isEmpty();
    }

    // ============================== 補助 ==============================

    /**
     * 調整をコミットするトランザクションで行う。メモは「その他」のときだけ付ける
     */
    private int adjust(int delta, StockAdjustmentReason reason) {
        var memo = reason == StockAdjustmentReason.OTHER ? "メモ" : null;
        var adjustment = StockAdjustment.create(delta, reason, memo, TestTokens.OPERATOR, NOW);
        return transactionTemplate.execute(status -> repository.adjust(variantId, adjustment));
    }

    /**
     * 調整履歴の最初のページ（20 件）
     */
    private PageResult<StockAdjustmentView> history() {
        return history(PageRequest.of(1, 20));
    }

    /**
     * 調整履歴を、指定したページで取得する
     */
    private PageResult<StockAdjustmentView> history(PageRequest request) {
        return queryRepository.findAdjustments(variantId, request).orElseThrow();
    }
}
