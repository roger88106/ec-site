package com.ecsite.common.scheduling;

import com.ecsite.LogCapture;
import com.ecsite.LogCapture.LogEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 期限切れのデータの片付けの共通の処理の単体テスト
 *
 * <p>削除の処理は差し替え、ロガーはテストのものを渡して、繰り返しの回数とログの出し方を確認する。
 */
class ExpiredDataCleanupTest {

    // ============================== 定数 ==============================

    /**
     * 1 回に削除する件数の上限
     */
    private static final int BATCH_SIZE = 3;

    /**
     * ログの文に入れる、片付けるデータの名詞
     */
    private static final String NOUN = "期限切れのカート";

    /**
     * ログの文に入れる、片付けるデータの別の名詞
     *
     * <p>名詞を固定の文にしていないことを、2 通りの名詞で確認する。
     */
    private static final String OTHER_NOUN = "期限切れの引当";

    // ============================== 対象 ==============================

    /**
     * 処理に渡すロガー
     */
    private final Logger log = LoggerFactory.getLogger(ExpiredDataCleanupTest.class);

    /**
     * 渡したロガーに出たログ
     */
    @RegisterExtension
    final LogCapture logs = LogCapture.of(ExpiredDataCleanupTest.class);

    // ============================== テスト ==============================

    @Test
    @DisplayName("削除した件数が上限と等しい間は繰り返し、上限に満たなくなったら止める")
    void repeatsWhileDeletedCountEqualsBatchSize() {
        // 準備：上限ちょうどを 2 回、上限に満たない件数を 1 回返す削除
        var results = new ArrayDeque<>(List.of(BATCH_SIZE, BATCH_SIZE, BATCH_SIZE - 1));
        var calls = new AtomicInteger();

        // 操作：片付ける
        ExpiredDataCleanup.run(log, NOUN, BATCH_SIZE, () -> {
            calls.incrementAndGet();
            return results.removeFirst();
        });

        // 確認：上限に満たなかった 3 回目で止まる
        assertThat(calls).hasValue(3);
    }

    @Test
    @DisplayName("1 回目に上限に満たなければ、1 回で止める")
    void stopsAfterFirstCallWhenBelowBatchSize() {
        // 準備：毎回 1 件だけ削除する処理
        var calls = new AtomicInteger();

        // 操作：片付ける
        ExpiredDataCleanup.run(log, NOUN, BATCH_SIZE, () -> {
            calls.incrementAndGet();
            return 1;
        });

        // 確認：1 回だけ呼ばれる
        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("削除した件数の合計が 1 件以上なら、渡した名詞からログの文を作り、info で渡したロガーに出す")
    void logsTotalAtInfoWhenSomethingWasDeleted() {
        // 準備：3 件、3 件、2 件を削除する処理
        var results = new ArrayDeque<>(List.of(3, 3, 2));

        // 操作：片付ける
        ExpiredDataCleanup.run(log, NOUN, BATCH_SIZE, results::removeFirst);

        // 確認：合計の 8 件を書いた info のログが 1 件だけ
        assertThat(logs.entries()).containsExactly(new LogEntry(Level.INFO, NOUN + "を消した：8 件", ""));
    }

    @Test
    @DisplayName("削除した件数が 0 件なら、debug で出す")
    void logsZeroAtDebugWhenNothingWasDeleted() {
        // 操作：何も消さない削除で片付ける
        ExpiredDataCleanup.run(log, OTHER_NOUN, BATCH_SIZE, () -> 0);

        // 確認：渡した名詞と 0 件を書いた debug のログが 1 件だけ
        assertThat(logs.entries()).containsExactly(new LogEntry(Level.DEBUG, OTHER_NOUN + "を消した：0 件", ""));
    }
}
