package com.ecsite.common.error;

import com.ecsite.common.persistence.ConstraintViolationTranslator;
import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DB のエラーの詳細（行の値）がログに出ないことの統合テスト
 *
 * <p>対応を登録していない制約の違反を HTTP のリクエストの経路で起こし、想定外のエラーのログ（原因の例外のメッセージとスタックトレースを含む）に、
 * PostgreSQL のエラーの詳細（{@code Detail: Key (code)=(値)}）の行の値が出ないことを確認する。
 * JDBC ドライバーの設定 {@code logServerErrorDetail=false} が、アプリの接続に付いていることの確認にもなる。
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
@Import(DbErrorDetailLogTest.UniqueViolationController.class)
class DbErrorDetailLogTest {

    // ============================== 定数 ==============================

    /**
     * テスト用の Controller のパス
     */
    private static final String BASE = "/api/test-db-error-detail";

    /**
     * 一意制約を持つテーブル
     */
    private static final String TABLE = "common.db_error_detail_check";

    /**
     * 行の値（ログに出てはならない）
     */
    private static final String ROW_VALUE = "secret-row-value";

    // ============================== Bean ==============================

    /**
     * API を呼ぶ MockMvc
     */
    @Autowired
    MockMvc mockMvc;

    /**
     * テーブルの作成に使う jOOQ
     */
    @Autowired
    DSLContext dsl;

    /**
     * 一意制約を持つテーブルを作り、行の値を入れる。
     */
    @BeforeEach
    void createTable() {
        dsl.execute("CREATE TABLE " + TABLE + " (code text CONSTRAINT db_error_detail_check_code_key UNIQUE)");
        dsl.execute("INSERT INTO " + TABLE + " (code) VALUES ('" + ROW_VALUE + "')");
    }

    /**
     * テーブルを消す。
     */
    @AfterEach
    void dropTable() {
        dsl.execute("DROP TABLE IF EXISTS " + TABLE);
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("DC-556-11 対応を登録していない一意制約の違反（変換を通る書き込み）の想定外のエラーのログに、行の値が出ない")
    void translatedUniqueViolationLogHasNoRowValue(CapturedOutput output) throws Exception {
        // 操作：対応のない一意制約の違反を、制約の違反の変換を通して起こす
        mockMvc.perform(get(BASE + "/translated")).andExpect(status().isInternalServerError());

        // 確認：想定外のエラーのログはあるが、行の値は出ない
        assertThat(output.getAll()).contains("システムエラーが発生しました。").contains("db_error_detail_check_code_key");
        assertThat(output.getAll()).doesNotContain(ROW_VALUE);
    }

    @Test
    @DisplayName("DC-556-11 一意制約の違反（変換を通らない経路）の想定外のエラーのログに、行の値が出ない")
    void rawUniqueViolationLogHasNoRowValue(CapturedOutput output) throws Exception {
        // 操作：一意制約の違反を、変換を通さずに起こす
        mockMvc.perform(get(BASE + "/raw")).andExpect(status().isInternalServerError());

        // 確認：想定外のエラーのログはあるが、行の値は出ない
        assertThat(output.getAll()).contains("システムエラーが発生しました。").contains("db_error_detail_check_code_key");
        assertThat(output.getAll()).doesNotContain(ROW_VALUE);
    }

    // ============================== テスト用の Controller ==============================

    /**
     * 一意制約の違反を起こす Controller
     */
    @RestController
    @RequestMapping(BASE)
    static class UniqueViolationController {

        /**
         * 制約の違反の変換（対応を登録しない）
         */
        private static final ConstraintViolationTranslator TRANSLATOR = ConstraintViolationTranslator.of(Map.of());

        /**
         * 一意制約の違反を起こす jOOQ
         */
        private final DSLContext dsl;

        UniqueViolationController(DSLContext dsl) {
            this.dsl = dsl;
        }

        /**
         * 変換を通して、一意制約の違反を起こす
         */
        @GetMapping("/translated")
        void translated() {
            TRANSLATOR.run(this::insertDuplicate);
        }

        /**
         * 変換を通さずに、一意制約の違反を起こす
         */
        @GetMapping("/raw")
        void raw() {
            insertDuplicate();
        }

        private void insertDuplicate() {
            // 値はバインド変数で渡す（SQL の文に値を埋めると、エラーの詳細と関係なく、例外の文に値が出るため）
            dsl.execute("INSERT INTO " + TABLE + " (code) VALUES (?)", ROW_VALUE);
        }
    }
}
