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

    /**
     * 件数の上限
     */
    private static final int BATCH_SIZE = 3;

    /**
     * 処理に渡すロガー
     */
    private final Logger log = LoggerFactory.getLogger(ExpiredDataCleanupTest.class);

    /**
     * 渡したロガーに出たログ
     */
    @RegisterExtension
    final LogCapture logs = LogCapture.of(ExpiredDataCleanupTest.class);

    @Test
    @DisplayName("削除した件数が上限と等しい間は繰り返し、上限に満たなくなったら止める")
    void repeatsWhileDeletedCountEqualsBatchSize() {
        var results = new ArrayDeque<>(List.of(3, 3, 2));
        var calls = new AtomicInteger();

        ExpiredDataCleanup.run(log, "期限切れのカート", BATCH_SIZE, () -> {
            calls.incrementAndGet();
            return results.removeFirst();
        });

        assertThat(calls).hasValue(3);
    }

    @Test
    @DisplayName("1 回目に上限に満たなければ、1 回で止める")
    void stopsAfterFirstCallWhenBelowBatchSize() {
        var calls = new AtomicInteger();

        ExpiredDataCleanup.run(log, "期限切れのカート", BATCH_SIZE, () -> {
            calls.incrementAndGet();
            return 1;
        });

        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("削除した件数の合計が 1 件以上なら、渡した名詞からログの文を作り、info で渡したロガーに出す")
    void logsTotalAtInfoWhenSomethingWasDeleted() {
        var results = new ArrayDeque<>(List.of(3, 3, 2));

        ExpiredDataCleanup.run(log, "期限切れのカート", BATCH_SIZE, results::removeFirst);

        assertThat(logs.entries()).containsExactly(new LogEntry(Level.INFO, "期限切れのカートを消した：8 件", ""));
    }

    @Test
    @DisplayName("削除した件数が 0 件なら、debug で出す")
    void logsZeroAtDebugWhenNothingWasDeleted() {
        ExpiredDataCleanup.run(log, "期限切れの引当", BATCH_SIZE, () -> 0);

        assertThat(logs.entries()).containsExactly(new LogEntry(Level.DEBUG, "期限切れの引当を消した：0 件", ""));
    }
}
