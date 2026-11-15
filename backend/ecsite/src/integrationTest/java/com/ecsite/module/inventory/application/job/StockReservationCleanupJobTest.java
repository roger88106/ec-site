package com.ecsite.module.inventory.application.job;

import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.LogCapture;
import com.ecsite.MutableClock;
import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.inventory.application.StockReservationCommandService;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.ecsite.jooq.inventory.Tables.STOCK_RESERVATION;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 引当の片付けのジョブの統合テスト
 *
 * <p>ジョブはテストから呼ぶ。ほかのテストの引当も消えるので、自分の持ち主の引当だけを確認する。
 */
@IntegrationTest
class StockReservationCleanupJobTest {

    // ============================== 定数 ==============================

    /**
     * 引当を作る時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 引当の期限
     *
     * <p>引当を作る時刻の 15 分後。
     */
    private static final Instant EXPIRES_AT = NOW.plus(Duration.ofMinutes(15));

    /**
     * 片付けを動かす時刻
     *
     * <p>引当の期限から 1 日と 1 時間後。保存期間（1 日）を引くと、期限の 1 時間後より前の引当が消える。
     */
    private static final Instant CLEANUP_AT = EXPIRES_AT.plus(Duration.ofDays(1)).plus(Duration.ofHours(1));

    /**
     * 片付けで消える引当の期限の境
     *
     * <p>片付けを動かす時刻から保存期間（1 日）を引いた時刻。これより前に期限が切れた引当が消える。
     */
    private static final Instant EXPIRED_BEFORE = EXPIRES_AT.plus(Duration.ofHours(1));

    /**
     * 片付けのジョブが出したログ
     */
    @RegisterExtension
    final LogCapture logs = LogCapture.of(StockReservationCleanupJob.class);

    // ============================== Bean ==============================

    @Autowired
    StockReservationCleanupJob job;

    @Autowired
    StockReservationCommandService service;

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
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("保存期間")
    class Retention {

        @Test
        @DisplayName("AC-284 期限から保存期間（1 日）を過ぎた引当だけが消え、保存期間の中の引当と有効な引当は残る")
        void deletesReservationsExpiredBeforeRetention() {
            // 準備：保存期間を過ぎる引当、期限切れだが保存期間の中の引当、有効な引当
            var variant = inventory.stockNewVariant(UUID.randomUUID(), 10);
            var old = reserve(variant, EXPIRES_AT);
            var recent = reserve(variant, EXPIRES_AT.plus(Duration.ofHours(2)));
            var active = reserve(variant, EXPIRES_AT.plus(Duration.ofDays(2)));
            clock.setInstant(CLEANUP_AT);

            // 操作：片付けを動かす
            job.cleanUp();

            // 確認：保存期間を過ぎた引当だけが消えている
            assertThat(holdersWithReservations(List.of(old, recent, active))).containsExactlyInAnyOrder(recent, active);
        }

        @Test
        @DisplayName("AC-288 消した件数をログに出す")
        void logsDeletedCount() {
            // 準備：保存期間を過ぎる引当
            var variant = inventory.stockNewVariant(UUID.randomUUID(), 10);
            reserve(variant, EXPIRES_AT);
            clock.setInstant(CLEANUP_AT);
            // 巻き戻せないので、ほかのテストの期限の古い引当も消える。消す前に数えて、ログの件数と比べる
            var expectedDeleted = dsl.fetchCount(STOCK_RESERVATION, STOCK_RESERVATION.EXPIRES_AT.lt(EXPIRED_BEFORE));

            // 操作：片付けを動かす
            logs.clear();
            job.cleanUp();

            // 確認（前提）：消す引当がある
            assertThat(expectedDeleted).isPositive();

            // 確認：消す前に数えた件数がログに出る
            assertThat(logs.messages(Level.INFO)).contains("期限切れの引当を消した：" + expectedDeleted + " 件");
        }
    }

    @Nested
    @DisplayName("1 回に消す件数の上限")
    class BatchLimit {

        @Test
        @DisplayName("NFR-24 件数の上限を超える引当は、上限ずつ分けて、残りがなくなるまで消す")
        void deletesInBatchesUntilNoneLeft() {
            // 準備：期限切れの引当 5 つと、上限 2 件の片付け
            var variant = inventory.stockNewVariant(UUID.randomUUID(), 10);
            var holders = IntStream.range(0, 5).mapToObj(i -> reserve(variant, EXPIRES_AT)).toList();
            var smallBatch = new StockReservationCleanupJob(service,
                    new StockReservationCleanupProperties(Duration.ofHours(1), Duration.ofDays(1), 2), clock);
            clock.setInstant(EXPIRES_AT.plus(Duration.ofDays(2)));

            // 操作：片付けを 1 回動かす
            smallBatch.cleanUp();

            // 確認：5 つとも消えている
            assertThat(holdersWithReservations(holders)).isEmpty();
        }

        @Test
        @DisplayName("NFR-24 1 回の削除は、件数の上限までしか消さない")
        void deletesUpToLimitAtOnce() {
            // 準備：ほかのテストが使わない古い期限にし、消す条件に当たるのを自分の 3 つだけにする
            var longAgo = Instant.parse("2000-01-01T00:00:00Z");
            var variant = inventory.stockNewVariant(UUID.randomUUID(), 10);
            var holders = IntStream.range(0, 3).mapToObj(i -> reserve(variant, longAgo)).toList();

            // 操作：上限 2 件で 1 回消す
            var deleted = service.deleteExpired(longAgo.plusSeconds(1), 2);

            // 確認：2 件だけが消え、1 つ残る
            assertThat(deleted).isEqualTo(2);
            assertThat(holdersWithReservations(holders)).hasSize(1);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 指定した持ち主のうち、引当が残っているものを取得する。
     *
     * @param holders 持ち主 ID
     * @return 引当が残っている持ち主 ID
     */
    private List<UUID> holdersWithReservations(List<UUID> holders) {
        return dsl.selectDistinct(STOCK_RESERVATION.HOLDER_ID)
                .from(STOCK_RESERVATION)
                .where(STOCK_RESERVATION.HOLDER_ID.in(holders))
                .fetch(STOCK_RESERVATION.HOLDER_ID);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 新しい持ち主で、1 つの在庫を 1 つ引き当てる。
     *
     * @param variant   バリアント ID
     * @param expiresAt 引当の期限
     * @return 持ち主 ID
     */
    private UUID reserve(UUID variant, Instant expiresAt) {
        var holder = UUID.randomUUID();
        assertThat(api.reserve(holder, Map.of(variant, 1), expiresAt)).isEmpty();
        return holder;
    }
}
