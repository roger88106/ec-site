package com.ecsite.common.error;

import com.ecsite.common.persistence.ConstraintViolationTranslator;
import com.ecsite.support.config.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.ecsite.support.http.ProblemAssertions.problem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DB の競合の応答の統合テスト
 *
 * <p>DB のロック待ちのタイムアウト、デッドロックなど、制約の違反でない DB の例外が、種類ごとに決めた応答になることを確認する。
 * ロック待ちと文の実行のタイムアウトは、テストを速くするため短くする。
 * 実際のデッドロックは起こしにくいので、原因に PostgreSQL のエラー（SQLState を指定）を持つ例外を投げて確認する。
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
        "ecsite.db.lock-timeout=500ms",
        "ecsite.db.statement-timeout=1s"
})
@Import(DbConflictResponseTest.DbConflictTestController.class)
class DbConflictResponseTest {

    // ============================== 定数 ==============================

    /**
     * テスト用の Controller のパス
     */
    private static final String BASE = "/api/test-db-conflicts";

    /**
     * ロック待ちのリクエストの結果を待つ上限（秒）。ロック待ちのタイムアウト（500 ミリ秒）より十分に長くする
     */
    private static final long WAIT_LIMIT_SECONDS = 10;

    /**
     * ロックを取る行を持つテーブル
     */
    private static final String LOCK_TABLE = "common.db_conflict_check";

    // 期待するメッセージ（messages.properties）。メッセージを変えたら、ここも合わせる
    /**
     * ロック取得の失敗のメッセージ
     */
    private static final String RESOURCE_BUSY = "ほかの処理が終わるのを待てませんでした。しばらくしてから、もう一度お試しください。";

    /**
     * 同時更新のメッセージ
     */
    private static final String CONCURRENT_MODIFICATION = "他の人が先に更新しました。再読み込みしてください。";

    /**
     * システムエラーのメッセージ
     */
    private static final String SYSTEM_ERROR = "予期しないエラーが発生しました。";

    /**
     * 原因の例外のメッセージに入れる、行の値（ログに出てはならない）
     */
    private static final String ROW_VALUE = "secret-row-value";

    // ============================== Bean ==============================

    /**
     * API を呼ぶ MockMvc
     */
    @Autowired
    MockMvc mockMvc;

    /**
     * テーブルの作成と、別のトランザクションの用意に使う jOOQ
     */
    @Autowired
    DSLContext dsl;

    /**
     * 別のトランザクションの接続を取る
     */
    @Autowired
    DataSource dataSource;

    /**
     * ロックを取る行のあるテーブルを作る。
     */
    @BeforeEach
    void createTable() {
        dsl.execute("CREATE TABLE " + LOCK_TABLE + " (id int PRIMARY KEY)");
        dsl.execute("INSERT INTO " + LOCK_TABLE + " (id) VALUES (1)");
    }

    /**
     * テーブルを消す。
     */
    @AfterEach
    void dropTable() {
        dsl.execute("DROP TABLE IF EXISTS " + LOCK_TABLE);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("ロック取得の失敗")
    class LockNotAvailable {

        @Test
        @DisplayName("DC-556-3 別のトランザクションが持つ行ロックを待ってタイムアウトしたリクエストは、503 と RESOURCE_BUSY になる")
        void lockWaitTimeoutBecomesResourceBusy() throws Exception {
            // 準備：別のトランザクションが、行ロックを持ったままにする
            try (Connection holder = dataSource.getConnection()) {
                holder.setAutoCommit(false);
                holder.createStatement().execute("SELECT id FROM " + LOCK_TABLE + " WHERE id = 1 FOR UPDATE");

                // 操作：同じ行の行ロックを取るリクエストを送る。ロック待ちに上限がないと戻らないので、別のスレッドで送り、待つ時間に上限を置く
                var request = CompletableFuture.supplyAsync(() -> {
                    try {
                        return mockMvc.perform(get(BASE + "/row-lock"));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
                try {
                    var result = request.get(WAIT_LIMIT_SECONDS, TimeUnit.SECONDS);

                    // 確認：待ちがタイムアウトして 503
                    result.andExpect(status().isServiceUnavailable())
                            .andExpectAll(problem("RESOURCE_BUSY", RESOURCE_BUSY));
                } finally {
                    // ロックを放す（戻らなかったリクエストも終わらせる）
                    holder.rollback();
                }
            }
        }

        @Test
        @DisplayName("DC-556-3 ロックを持つトランザクションが終われば、同じリクエストは通る")
        void succeedsWhenNoOtherTransactionHoldsLock() throws Exception {
            // 操作と確認：ほかにロックを持つ人がいなければ 200
            mockMvc.perform(get(BASE + "/row-lock"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("DC-556-3 制約の違反の変換を通る書き込みでも、ロック取得の失敗は 503 と RESOURCE_BUSY になる")
        void lockNotAvailableThroughTranslator() throws Exception {
            // 操作と確認：変換を通った例外（システムエラーに包まれる）でも、原因の SQLState で判定して 503
            mockMvc.perform(get(BASE + "/translated/55P03"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpectAll(problem("RESOURCE_BUSY", RESOURCE_BUSY));
        }
    }

    @Nested
    @DisplayName("デッドロック")
    class Deadlock {

        @Test
        @DisplayName("DC-556-4 デッドロック（40P01）は、409 と CONCURRENT_MODIFICATION になる")
        void deadlockBecomesConflict() throws Exception {
            // 操作と確認：読み込みなど、変換を通らない経路でも 409
            mockMvc.perform(get(BASE + "/raw/40P01"))
                    .andExpect(status().isConflict())
                    .andExpectAll(problem("CONCURRENT_MODIFICATION", CONCURRENT_MODIFICATION));
        }

        @Test
        @DisplayName("DC-556-4 制約の違反の変換を通る書き込みでも、デッドロックは 409 と CONCURRENT_MODIFICATION になる")
        void deadlockThroughTranslator() throws Exception {
            // 操作と確認：変換を通った例外でも 409
            mockMvc.perform(get(BASE + "/translated/40P01"))
                    .andExpect(status().isConflict())
                    .andExpectAll(problem("CONCURRENT_MODIFICATION", CONCURRENT_MODIFICATION));
        }
    }

    @Nested
    @DisplayName("競合として扱わない DB のエラー")
    class NotConflicts {

        @Test
        @DisplayName("DC-556-4 直列化の失敗（40001）は、500 のままである")
        void serializationFailureStaysSystemError() throws Exception {
            // 操作と確認：SYSTEM_ERROR の 500
            mockMvc.perform(get(BASE + "/raw/40001"))
                    .andExpect(status().isInternalServerError())
                    .andExpectAll(problem("SYSTEM_ERROR", SYSTEM_ERROR));
        }

        @Test
        @DisplayName("DC-556-4 制約の違反の変換を通る書き込みでも、直列化の失敗は 500 のままである")
        void serializationFailureThroughTranslator() throws Exception {
            // 操作と確認：SYSTEM_ERROR の 500
            mockMvc.perform(get(BASE + "/translated/40001"))
                    .andExpect(status().isInternalServerError())
                    .andExpectAll(problem("SYSTEM_ERROR", SYSTEM_ERROR));
        }

        @Test
        @DisplayName("DC-556-6 文の実行のタイムアウトで失敗したエラーは、500 のままである")
        void statementTimeoutStaysSystemError() throws Exception {
            // 操作と確認：実際に文の実行のタイムアウト（57014）を起こすと、SYSTEM_ERROR の 500
            mockMvc.perform(get(BASE + "/statement-timeout"))
                    .andExpect(status().isInternalServerError())
                    .andExpectAll(problem("SYSTEM_ERROR", SYSTEM_ERROR));
        }

        @Test
        @DisplayName("DC-556-6 原因に 57014 を持つ例外は、500 のままである")
        void queryCanceledStaysSystemError() throws Exception {
            // 操作と確認：SYSTEM_ERROR の 500
            mockMvc.perform(get(BASE + "/raw/57014"))
                    .andExpect(status().isInternalServerError())
                    .andExpectAll(problem("SYSTEM_ERROR", SYSTEM_ERROR));
        }

        @Test
        @DisplayName("DC-556-4 DB の競合でない PostgreSQL のエラーは、500 のままである")
        void otherPostgresErrorStaysSystemError() throws Exception {
            // 操作と確認：SYSTEM_ERROR の 500
            mockMvc.perform(get(BASE + "/raw/23505"))
                    .andExpect(status().isInternalServerError())
                    .andExpectAll(problem("SYSTEM_ERROR", SYSTEM_ERROR));
        }
    }

    @Nested
    @DisplayName("ログ")
    class Logging {

        @Test
        @DisplayName("DC-556-5 デッドロックは、警告のログにエラーコード・トレース ID・SQLState を出し、原因の例外のメッセージとスタックトレースを出さない")
        void deadlockLog(CapturedOutput output) throws Exception {
            // 操作：デッドロックを起こす
            var traceId = traceIdOf(mockMvc.perform(get(BASE + "/raw/40P01")));

            // 確認：警告で、エラーコード・トレース ID・SQLState が出て、行の値と例外のクラスは出ない
            assertConflictLog(output.getAll(), "CONCURRENT_MODIFICATION", traceId, "40P01");
        }

        @Test
        @DisplayName("DC-556-5 ロック取得の失敗は、警告のログにエラーコード・トレース ID・SQLState を出し、原因の例外のメッセージとスタックトレースを出さない")
        void lockNotAvailableLog(CapturedOutput output) throws Exception {
            // 操作：ロック取得の失敗を起こす
            var traceId = traceIdOf(mockMvc.perform(get(BASE + "/translated/55P03")));

            // 確認：警告で、エラーコード・トレース ID・SQLState が出て、行の値と例外のクラスは出ない
            assertConflictLog(output.getAll(), "RESOURCE_BUSY", traceId, "55P03");
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * エラーの応答の本文から traceId を読む。
     *
     * @param result 応答
     * @return traceId
     */
    private static String traceIdOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.traceId");
    }

    /**
     * DB の競合のログの行を確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>警告のレベルで、エラーコード・トレース ID・SQLState が出る</li>
     *   <li>原因の例外のメッセージ（行の値を含む）が出ない</li>
     *   <li>原因の例外のクラスとスタックトレースが出ない</li>
     * </ul>
     *
     * @param log       捕まえたログ
     * @param errorCode 期待するエラーコード
     * @param traceId   応答のトレース ID
     * @param sqlState  期待する SQLState
     */
    private static void assertConflictLog(String log, String errorCode, String traceId, String sqlState) {
        var line = log.lines()
                .filter(l -> l.contains("traceId=" + traceId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("トレース ID を含むログの行がない：" + log));
        assertThat(line).contains("WARN", "errorCode=" + errorCode, "sqlState=" + sqlState);
        assertThat(log).doesNotContain(ROW_VALUE).doesNotContain("PSQLException").doesNotContain("\tat ");
    }

    // ============================== テスト用の Controller ==============================

    /**
     * DB の例外を発生させる Controller
     */
    @RestController
    @RequestMapping(BASE)
    static class DbConflictTestController {

        /**
         * 制約の違反の変換
         */
        private static final ConstraintViolationTranslator TRANSLATOR = ConstraintViolationTranslator.of(Map.of());

        /**
         * 行ロックを取る jOOQ
         */
        private final DSLContext dsl;

        /**
         * トランザクションを張る
         */
        private final TransactionTemplate tx;

        DbConflictTestController(DSLContext dsl, PlatformTransactionManager transactionManager) {
            this.dsl = dsl;
            this.tx = new TransactionTemplate(transactionManager);
        }

        /**
         * 行ロックを取る読み込み
         */
        @GetMapping("/row-lock")
        void rowLock() {
            tx.executeWithoutResult(status ->
                    dsl.fetch("SELECT id FROM " + LOCK_TABLE + " WHERE id = 1 FOR UPDATE"));
        }

        /**
         * 文の実行のタイムアウトを超える読み込み
         */
        @GetMapping("/statement-timeout")
        void statementTimeout() {
            tx.executeWithoutResult(status -> dsl.fetch("SELECT pg_sleep(5)"));
        }

        /**
         * 原因に PostgreSQL のエラーを持つ例外を、そのまま投げる（変換を通らない読み込みの形）
         */
        @GetMapping("/raw/{sqlState}")
        void raw(@PathVariable String sqlState) {
            throw dbException(sqlState);
        }

        /**
         * 原因に PostgreSQL のエラーを持つ例外を、制約の違反の変換を通して投げる（書き込みの形）
         */
        @GetMapping("/translated/{sqlState}")
        void translated(@PathVariable String sqlState) {
            TRANSLATOR.run(() -> {
                throw dbException(sqlState);
            });
        }

        private static ConcurrencyFailureException dbException(String sqlState) {
            // PSQLState の列挙に 55P03 がないので、SQLState を指定した PSQLException を作る
            var cause = new PSQLException("ERROR: failed Detail: Key (code)=(" + ROW_VALUE + ") conflicts", PSQLState.UNKNOWN_STATE) {
                @Override
                public String getSQLState() {
                    return sqlState;
                }
            };
            return new CannotAcquireLockException("DB のエラー", cause);
        }
    }
}
