package com.ecsite.module.inventory.application;

import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.jooq.inventory.Tables.STOCK_RESERVATION;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 引当の片付けのジョブの統合テスト。<br />
 * ジョブはテストから呼ぶ。ほかのテストの引当も消えるので、自分の持ち主の引当だけを確認する。
 */
@IntegrationTest
@Import(TestClockConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class StockReservationCleanupJobTest {

    /**
     * 引当を作る時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 引当の期限（15 分後）
     */
    private static final Instant EXPIRES_AT = NOW.plus(Duration.ofMinutes(15));

    @Autowired
    StockReservationCleanupJob job;

    @Autowired
    StockReservationService service;

    @Autowired
    StockReservationApi api;

    @Autowired
    StockItemRepository stockItemRepository;

    /**
     * 在庫を作る補助
     */
    private InventoryTestData inventory;

    @Autowired
    MutableClock clock;

    @Autowired
    DSLContext dsl;

    @BeforeEach
    void setUp() {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        clock.setInstant(NOW);
    }

    @Test
    @DisplayName("AC-284 期限から保存期間（1 日）より前の引当は消え、その後の引当と有効な引当は残る。消した件数をログに出す（AC-288）")
    void deletesReservationsExpiredBeforeRetention(CapturedOutput output) {
        var variant = stock(10);
        var old = reserve(variant, EXPIRES_AT);
        var recent = reserve(variant, EXPIRES_AT.plus(Duration.ofHours(2)));
        var active = reserve(variant, EXPIRES_AT.plus(Duration.ofDays(2)));

        // 1 つ目の期限から 1 日と 1 時間後：1 つ目だけが保存期間を過ぎ、2 つ目は期限切れだが保存期間の中
        clock.setInstant(EXPIRES_AT.plus(Duration.ofDays(1)).plus(Duration.ofHours(1)));
        job.cleanUp();

        assertThat(holdersWithReservations(List.of(old, recent, active))).containsExactlyInAnyOrder(recent, active);
        assertThat(output).containsPattern("期限切れの引当を消した：[1-9][0-9]* 件");
    }

    @Test
    @DisplayName("NFR-24 件数の上限を超える引当は、上限ずつ分けて、残りがなくなるまで消す")
    void deletesInBatchesUntilNoneLeft() {
        var variant = stock(10);
        var holders = new ArrayList<UUID>();
        for (int i = 0; i < 5; i++) {
            holders.add(reserve(variant, EXPIRES_AT));
        }
        var smallBatch = new StockReservationCleanupJob(service,
                new StockReservationCleanupProperties(Duration.ofHours(1), Duration.ofDays(1), 2), clock);

        clock.setInstant(EXPIRES_AT.plus(Duration.ofDays(2)));
        smallBatch.cleanUp();

        assertThat(holdersWithReservations(holders)).isEmpty();
    }

    @Test
    @DisplayName("NFR-24 1 回の削除は、件数の上限までしか消さない")
    void deletesUpToLimitAtOnce() {
        // ほかのテストが使わない古い期限にし、消す条件に当たるのを自分の 3 つだけにする
        var longAgo = Instant.parse("2000-01-01T00:00:00Z");
        var variant = stock(10);
        var holders = new ArrayList<UUID>();
        for (int i = 0; i < 3; i++) {
            holders.add(reserve(variant, longAgo));
        }

        var deleted = service.deleteExpired(longAgo.plusSeconds(1), 2);

        assertThat(deleted).isEqualTo(2);
        assertThat(holdersWithReservations(holders)).hasSize(1);
    }

    /**
     * 新しい持ち主で、1 つの在庫を 1 つ引き当てる。
     *
     * @return 持ち主 ID
     */
    private UUID reserve(UUID variant, Instant expiresAt) {
        var holder = UUID.randomUUID();
        assertThat(api.reserve(holder, Map.of(variant, 1), expiresAt)).isEmpty();
        return holder;
    }

    /**
     * 指定した持ち主のうち、引当が残っているもの
     */
    private List<UUID> holdersWithReservations(List<UUID> holders) {
        return dsl.selectDistinct(STOCK_RESERVATION.HOLDER_ID)
                .from(STOCK_RESERVATION)
                .where(STOCK_RESERVATION.HOLDER_ID.in(holders))
                .fetch(STOCK_RESERVATION.HOLDER_ID);
    }

    /**
     * 在庫を作る。
     *
     * @return バリアント ID
     */
    private UUID stock(int quantity) {
        var variantId = UUID.randomUUID();
        inventory.stock(variantId, UUID.randomUUID(), quantity);
        return variantId;
    }
}
