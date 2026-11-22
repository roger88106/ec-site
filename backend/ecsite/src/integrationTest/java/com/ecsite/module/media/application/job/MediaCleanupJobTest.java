package com.ecsite.module.media.application.job;

import com.ecsite.common.model.id.AssetId;
import com.ecsite.module.media.application.MediaService;
import com.ecsite.module.media.application.port.ObjectStorage;
import com.ecsite.module.media.application.port.PendingAssetRepository;
import com.ecsite.module.media.domain.PendingAsset;
import com.ecsite.support.LogCapture;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.ecsite.jooq.media.Tables.PENDING_ASSET;
import static com.ecsite.support.async.AsyncEvents.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 仮のアセットの片付けのジョブの統合テスト
 *
 * <p>保存期間を過ぎた仮のアセットの記録と配信のファイルの削除、保存期間の中のものが残ること、1 回の実行で件数の上限までだけ削除すること、
 * 削除した件数のログを確認する。間隔と保存期間の設定は {@code CleanupJobSettingsTest} が確認する。
 *
 * <p>ジョブはテストから呼ぶ。配信のファイルはコミットの後のイベントで削除するので、このテストはロールバックしない。
 * 表の全体から保存期間を過ぎたものを削除するので、確認するのはこのテストで作った仮のアセットだけにする。
 */
@IntegrationTest
class MediaCleanupJobTest {

    // ============================== 定数 ==============================

    /**
     * 仮のアセットを用意した時刻
     *
     * <p>日本時間の 2026-10-08 00:30。
     */
    private static final Instant PREPARED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 保存期間の既定（1 日）
     */
    private static final Duration RETENTION = Duration.ofDays(1);

    /**
     * 片付ける時刻
     *
     * <p>PREPARED_AT から保存期間と 1 マイクロ秒の後（PREPARED_AT に用意したものは保存期間を過ぎている）。
     */
    private static final Instant CLEANUP_AT = PREPARED_AT.plus(RETENTION).plus(1, ChronoUnit.MICROS);

    /**
     * 保存期間の境界に当たる用意した時刻
     *
     * <p>CLEANUP_AT から保存期間の前（保存期間を過ぎていない）。
     */
    private static final Instant PREPARED_AT_BOUNDARY = CLEANUP_AT.minus(RETENTION);

    /**
     * 他のテストが残す仮のアセット（テストの時計の 2026 年）より古い、用意した時刻
     *
     * <p>件数の上限の確認で、このテストの仮のアセットが先に選ばれるようにする。
     */
    private static final Instant LONG_AGO = Instant.parse("1990-01-01T00:00:00Z");

    /**
     * 配信のファイルの中身
     */
    private static final byte[] CONTENT = "pending".getBytes(StandardCharsets.US_ASCII);

    /**
     * 小さくした件数の上限
     */
    private static final int SMALL_LIMIT = 2;

    /**
     * 1 回の実行で削除する件数の上限の既定
     */
    private static final int DEFAULT_LIMIT = 500;

    /**
     * 配信の URL を呼ぶ HttpClient
     */
    private final HttpClient http = HttpClient.newHttpClient();

    /**
     * ジョブが出したログ
     */
    @RegisterExtension
    final LogCapture logs = LogCapture.of(MediaCleanupJob.class);

    // ============================== Bean ==============================

    /**
     * テストするジョブ（設定の既定）
     */
    @Autowired
    MediaCleanupJob job;

    @Autowired
    MediaService service;

    @Autowired
    PendingAssetRepository pendingAssets;

    @Autowired
    ObjectStorage storage;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    MutableClock clock;

    @Autowired
    DSLContext dsl;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("保存期間を過ぎた仮のアセットの削除")
    class DeleteExpired {

        @Test
        @DisplayName("DC-489-4 保存期間を過ぎた仮のアセットは、記録が削除され、コミットの後に配信のファイルが削除される")
        void deletesExpiredPendingAssetAndFile() throws Exception {
            // 準備：保存期間を過ぎる仮のアセットと、その配信のファイル
            var expired = pendingWithFile(PREPARED_AT);

            // 操作：保存期間の後に片付ける
            clock.setInstant(CLEANUP_AT);
            job.cleanUp();

            // 確認：記録がない
            assertThat(exists(expired)).isFalse();

            // 確認：コミットの後に、配信のファイルが削除される
            await().atMost(TIMEOUT).untilAsserted(() -> assertThat(status(expired)).isEqualTo(404));
        }

        @Test
        @DisplayName("DC-489-4 保存期間の境界の時刻に用意した仮のアセットは、記録も配信のファイルも残る")
        void keepsPendingAssetWithinRetention() throws Exception {
            // 準備：保存期間を過ぎるものと、保存期間の境界の時刻に用意したもの
            var expired = pendingWithFile(PREPARED_AT);
            var kept = pendingWithFile(PREPARED_AT_BOUNDARY);

            // 操作：片付け、保存期間を過ぎたもののファイルが削除されるまで待つ（イベントの処理を終えてから確認する）
            clock.setInstant(CLEANUP_AT);
            job.cleanUp();
            await().atMost(TIMEOUT).untilAsserted(() -> assertThat(status(expired)).isEqualTo(404));

            // 確認：保存期間の中のものは、記録も配信のファイルも残る
            assertThat(exists(kept)).isTrue();
            assertThat(status(kept)).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("件数の上限")
    class Limit {

        @Test
        @DisplayName("DC-489-4 1 回の実行では、用意した時刻の古い順に件数の上限までだけ削除し、残りは次の回に削除する")
        void deletesUpToLimitPerRun() {
            // 準備：他のテストが残すものより古い時刻に用意した 3 つの仮のアセットと、件数の上限を 2 にしたジョブ
            var oldest = pendingWithFile(LONG_AGO);
            var middle = pendingWithFile(LONG_AGO.plus(Duration.ofHours(1)));
            var newest = pendingWithFile(LONG_AGO.plus(Duration.ofHours(2)));
            var smallBatch = new MediaCleanupJob(service, new MediaCleanupProperties(Duration.ofHours(1), RETENTION, SMALL_LIMIT), clock);
            clock.setInstant(CLEANUP_AT);

            // 操作：1 回片付ける
            smallBatch.cleanUp();

            // 確認：古い 2 つだけが削除され、最も新しいものが残る
            assertThat(remaining(List.of(oldest, middle, newest))).containsExactly(newest.id().value());

            // 操作：もう 1 回片付ける
            smallBatch.cleanUp();

            // 確認：残りも削除される
            assertThat(remaining(List.of(newest))).isEmpty();
        }
    }

    @Nested
    @DisplayName("ログ")
    class Logging {

        @Test
        @DisplayName("AC-288 削除した件数をログに出す")
        void logsDeletedCount() {
            // 準備：保存期間を過ぎる 3 つの仮のアセット
            var expired = IntStream.range(0, 3).mapToObj(i -> pendingWithFile(LONG_AGO)).toList();
            clock.setInstant(CLEANUP_AT);

            // 準備：削除する対象（保存期間を過ぎた全ての仮のアセット）を数える（他のテストがコミットした仮のアセットも対象になりうる）
            var targets = dsl.fetchCount(PENDING_ASSET, PENDING_ASSET.PREPARED_AT.lt(CLEANUP_AT.minus(RETENTION)));

            // 確認（前提）：このテストの 3 つが対象に入り、対象は 1 回の実行の上限に収まる
            assertThat(targets).isBetween(expired.size(), DEFAULT_LIMIT);

            // 操作：片付ける
            logs.clear();
            job.cleanUp();

            // 確認（前提）：3 つとも削除されている
            assertThat(remaining(expired)).isEmpty();

            // 確認：削除した件数（数えた対象の数）を INFO のログに 1 回出している
            assertThat(logs.messages(Level.INFO)).containsExactly("保存期間を過ぎた仮のアセットを消した：" + targets + " 件");
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 仮のアセットの記録があるかを返す。
     *
     * @param pending 仮のアセット
     * @return あれば true
     */
    private boolean exists(PendingAsset pending) {
        return dsl.fetchExists(PENDING_ASSET, PENDING_ASSET.ID.eq(pending.id().value()));
    }

    /**
     * 指定した仮のアセットのうち、記録が残っているものの ID を返す。
     *
     * @param pendings 仮のアセット
     * @return 残っている仮のアセットの ID
     */
    private List<UUID> remaining(List<PendingAsset> pendings) {
        return dsl.select(PENDING_ASSET.ID).from(PENDING_ASSET)
                .where(PENDING_ASSET.ID.in(pendings.stream().map(pending -> pending.id().value()).toList()))
                .fetch(PENDING_ASSET.ID);
    }

    /**
     * 仮のアセットの配信のファイルを、認証なしで取得したときの HTTP ステータスを返す。
     *
     * @param pending 仮のアセット
     * @return HTTP ステータス
     * @throws IOException          リクエストを送信できなかった
     * @throws InterruptedException 待つ間に割り込まれた
     */
    private int status(PendingAsset pending) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(storage.publicUrl(pending.storageKey())).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 配信のファイルを置き、仮のアセットを記録する（用意の変換を通さない）。
     *
     * @param preparedAt 用意した時刻
     * @return 仮のアセット
     */
    private PendingAsset pendingWithFile(Instant preparedAt) {
        var id = new AssetId(UUID.randomUUID());
        var pending = new PendingAsset(id, "assets/" + id.value() + ".webp", preparedAt);
        storage.putPublished(pending.storageKey(), CONTENT, "image/webp");
        transactionTemplate.executeWithoutResult(status -> pendingAssets.insert(pending));
        return pending;
    }
}
