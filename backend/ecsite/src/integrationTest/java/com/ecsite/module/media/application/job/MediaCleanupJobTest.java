package com.ecsite.module.media.application.job;

import com.ecsite.module.media.application.MediaService;
import com.ecsite.module.media.application.port.ObjectStorage;
import com.ecsite.module.media.application.port.PendingAssetRepository;
import com.ecsite.module.media.domain.PendingAsset;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
 * <p>保存期間を過ぎた仮のアセットの記録と配信のファイルの削除、保存期間の中のものが残ること、件数の上限ごとの削除を確認する。
 * 間隔と保存期間の設定は {@code CleanupJobSettingsTest} が確認する。
 *
 * <p>ジョブはテストから呼ぶ。配信のファイルはコミットの後のイベントで消すので、このテストはロールバックしない。
 * 表の全体から保存期間を過ぎたものを消すので、確認するのはこのテストで作った仮のアセットだけにする。
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
     * 保存期間のちょうど終わりに当たる、用意した時刻
     *
     * <p>CLEANUP_AT から保存期間の前（保存期間を過ぎていない）。
     */
    private static final Instant PREPARED_AT_BOUNDARY = CLEANUP_AT.minus(RETENTION);

    /**
     * 配信のファイルの中身
     */
    private static final byte[] CONTENT = "pending".getBytes(StandardCharsets.US_ASCII);

    /**
     * 小さくした件数の上限
     */
    private static final int SMALL_LIMIT = 2;

    /**
     * 配信の URL を呼ぶ HttpClient
     */
    private final HttpClient http = HttpClient.newHttpClient();

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
        @DisplayName("DC-489-4 保存期間を過ぎた仮のアセットは、記録が消え、コミットの後に配信のファイルが消える")
        void deletesExpiredPendingAssetAndFile() throws Exception {
            // 準備：保存期間を過ぎる仮のアセットと、その配信のファイル
            var expired = pendingWithFile(PREPARED_AT);

            // 操作：保存期間の後に片付ける
            clock.setInstant(CLEANUP_AT);
            job.cleanUp();

            // 確認：記録がない
            assertThat(exists(expired)).isFalse();

            // 確認：コミットの後に、配信のファイルが消える
            await().atMost(TIMEOUT).untilAsserted(() -> assertThat(status(expired)).isEqualTo(404));
        }

        @Test
        @DisplayName("DC-489-4 保存期間のちょうど終わりに用意した仮のアセットは、記録も配信のファイルも残る")
        void keepsPendingAssetWithinRetention() throws Exception {
            // 準備：保存期間を過ぎるものと、保存期間のちょうど終わりに用意したもの
            var expired = pendingWithFile(PREPARED_AT);
            var kept = pendingWithFile(PREPARED_AT_BOUNDARY);

            // 操作：片付け、保存期間を過ぎたもののファイルが消えるまで待つ（イベントの処理を終えてから確認する）
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
        @DisplayName("DC-489-4 件数の上限を超える仮のアセットは、上限ずつ分けて、残りがなくなるまで消す")
        void deletesInBatchesUntilNoneLeft() throws Exception {
            // 準備：保存期間を過ぎる 5 つの仮のアセットと、件数の上限を 2 にしたジョブ
            var expired = IntStream.range(0, 5).mapToObj(i -> pendingWithFile(PREPARED_AT)).toList();
            var smallBatch = new MediaCleanupJob(service, new MediaCleanupProperties(Duration.ofHours(1), RETENTION, SMALL_LIMIT), clock);

            // 操作：保存期間の後に片付ける
            clock.setInstant(CLEANUP_AT);
            smallBatch.cleanUp();

            // 確認：5 つとも記録がない
            assertThat(remaining(expired)).isEmpty();

            // 確認：コミットの後に、5 つとも配信のファイルが消える
            for (var pending : expired) {
                await().atMost(TIMEOUT).untilAsserted(() -> assertThat(status(pending)).isEqualTo(404));
            }
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
        return dsl.fetchExists(PENDING_ASSET, PENDING_ASSET.ID.eq(pending.id()));
    }

    /**
     * 指定した仮のアセットのうち、記録が残っているものの ID を返す。
     *
     * @param pendings 仮のアセット
     * @return 残っている仮のアセットの ID
     */
    private List<UUID> remaining(List<PendingAsset> pendings) {
        return dsl.select(PENDING_ASSET.ID).from(PENDING_ASSET)
                .where(PENDING_ASSET.ID.in(pendings.stream().map(PendingAsset::id).toList()))
                .fetch(PENDING_ASSET.ID);
    }

    /**
     * 仮のアセットの配信のファイルを、認証なしで取得したときの HTTP ステータスを返す。
     *
     * @param pending 仮のアセット
     * @return HTTP ステータス
     * @throws IOException          送れない
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
        var id = UUID.randomUUID();
        var pending = new PendingAsset(id, "assets/" + id + ".webp", preparedAt);
        storage.putPublished(pending.storageKey(), CONTENT, "image/webp");
        transactionTemplate.executeWithoutResult(status -> pendingAssets.insert(pending));
        return pending;
    }
}
