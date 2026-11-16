package com.ecsite.common.persistence;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchRuntimeException;

/**
 * DB の制約違反を業務のエラーに変換する部品の検証
 *
 * <p>API の規約に対応する。
 * テストの中でテーブルを作る。テストごとにロールバックするので残らない。
 */
@IntegrationTest
@Transactional
class ConstraintViolationTranslatorTest {

    // ============================== 定数 ==============================

    /**
     * 一意制約の名前
     */
    private static final String UNIQUE_CONSTRAINT = "constraint_check_child_code_key";

    /**
     * 外部キー制約の名前
     */
    private static final String FOREIGN_KEY_CONSTRAINT = "constraint_check_child_parent_id_fkey";

    // ============================== Bean ==============================

    /**
     * テーブルを作り、制約に違反する jOOQ
     */
    @Autowired
    DSLContext dsl;

    /**
     * テストの対象（制約名 → 業務のエラー）
     */
    private final ConstraintViolationTranslator translator = ConstraintViolationTranslator.of(Map.of(
            UNIQUE_CONSTRAINT, () -> BusinessException.of(CommonErrorCode.CONCURRENT_MODIFICATION),
            FOREIGN_KEY_CONSTRAINT, () -> BusinessException.of(CommonErrorCode.NOT_FOUND)
    ));

    /**
     * 一意制約と外部キー制約を持つテーブルを作り、コードが A の行を入れる
     */
    @BeforeEach
    void createTables() {
        dsl.execute("CREATE TABLE common.constraint_check_parent (id int PRIMARY KEY)");
        dsl.execute("""
                CREATE TABLE common.constraint_check_child (
                    id        int PRIMARY KEY,
                    code      text UNIQUE,
                    parent_id int REFERENCES common.constraint_check_parent (id)
                )""");
        dsl.execute("INSERT INTO common.constraint_check_child (id, code) VALUES (1, 'A')");
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("一意制約の違反は、制約名に対応する業務のエラーになる")
    void translatesUniqueViolation() {
        // 準備：同じコードの行を入れて、一意制約に違反する
        var ex = catchRuntimeException(() ->
                dsl.execute("INSERT INTO common.constraint_check_child (id, code) VALUES (2, 'A')"));

        // 操作：変換する
        var translated = translator.translate(ex);

        // 確認：一意制約に対応づけた業務のエラー
        assertBusinessErrorOf(translated, CommonErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    @DisplayName("外部キー制約の違反は、制約名に対応する業務のエラーになる")
    void translatesForeignKeyViolation() {
        // 準備：ない親を指す行を入れて、外部キー制約に違反する
        var ex = catchRuntimeException(() ->
                dsl.execute("INSERT INTO common.constraint_check_child (id, parent_id) VALUES (2, 99)"));

        // 操作：変換する
        var translated = translator.translate(ex);

        // 確認：外部キー制約に対応づけた業務のエラー
        assertBusinessErrorOf(translated, CommonErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("対応が登録されていない制約の違反は、システムエラーになる")
    void unknownConstraintBecomesSystemError() {
        // 準備：同じ ID の行を入れて、対応のない主キーの制約に違反する
        var ex = catchRuntimeException(() ->
                dsl.execute("INSERT INTO common.constraint_check_child (id) VALUES (1)"));

        // 操作：変換する
        var translated = translator.translate(ex);

        // 確認：元の例外を原因に持つシステムエラー
        assertSystemErrorCausedBy(translated, ex);
    }

    @Test
    @DisplayName("制約違反以外の例外は、システムエラーになる")
    void otherExceptionBecomesSystemError() {
        // 準備：ないテーブルを読んで失敗する
        var ex = catchRuntimeException(() -> dsl.execute("SELECT * FROM common.no_such_table"));

        // 操作：変換する
        var translated = translator.translate(ex);

        // 確認：元の例外を原因に持つシステムエラー
        assertSystemErrorCausedBy(translated, ex);
    }

    // ============================== 確認の補助 ==============================

    /**
     * 変換した例外が、業務のエラーであることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>業務の例外</li>
     *   <li>エラーコードが期待するもの</li>
     * </ul>
     *
     * @param translated 変換した例外
     * @param errorCode  期待するエラーコード
     */
    private static void assertBusinessErrorOf(RuntimeException translated, ErrorCodeProvider errorCode) {
        assertThat(translated).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) translated).getErrorDetail().errorCode()).isEqualTo(errorCode.getCode());
    }

    /**
     * 変換した例外が、元の例外を原因に持つシステムエラーであることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>システムの例外</li>
     *   <li>エラーコードが共通のシステムエラー</li>
     *   <li>原因が元の例外</li>
     * </ul>
     *
     * @param translated 変換した例外
     * @param cause      元の例外
     */
    private static void assertSystemErrorCausedBy(RuntimeException translated, RuntimeException cause) {
        assertThat(translated).isInstanceOf(SystemException.class);
        assertThat(((SystemException) translated).getErrorDetail().errorCode()).isEqualTo(CommonErrorCode.SYSTEM_ERROR.getCode());
        assertThat(translated).hasCause(cause);
    }
}
