package com.ecsite.common.persistence;

import com.ecsite.IntegrationTest;
import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
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
 * DB の制約違反を業務のエラーに変換する部品の検証（T011、constitution 9）。<br />
 * テストの中でテーブルを作る。テストごとにロールバックするので残らない。
 */
@IntegrationTest
@Transactional
class ConstraintViolationTranslatorTest {

    @Autowired
    DSLContext dsl;

    /**
     * 制約名 → 業務のエラー
     */
    private final ConstraintViolationTranslator translator = ConstraintViolationTranslator.of(Map.of(
            "constraint_check_child_code_key", () -> BusinessException.of(CommonErrorCode.CONCURRENT_MODIFICATION),
            "constraint_check_child_parent_id_fkey", () -> BusinessException.of(CommonErrorCode.NOT_FOUND)
    ));

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

    @Test
    @DisplayName("一意制約の違反は、制約名に対応する業務のエラーになる")
    void translatesUniqueViolation() {
        var ex = catchRuntimeException(() ->
                dsl.execute("INSERT INTO common.constraint_check_child (id, code) VALUES (2, 'A')"));

        var translated = translator.translate(ex);

        assertThat(translated).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) translated).getErrorDetail().errorCode())
                .isEqualTo(CommonErrorCode.CONCURRENT_MODIFICATION.getCode());
    }

    @Test
    @DisplayName("外部キー制約の違反は、制約名に対応する業務のエラーになる")
    void translatesForeignKeyViolation() {
        var ex = catchRuntimeException(() ->
                dsl.execute("INSERT INTO common.constraint_check_child (id, parent_id) VALUES (2, 99)"));

        var translated = translator.translate(ex);

        assertThat(translated).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) translated).getErrorDetail().errorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("対応が登録されていない制約の違反は、システムエラーになる")
    void unknownConstraintBecomesSystemError() {
        var ex = catchRuntimeException(() ->
                dsl.execute("INSERT INTO common.constraint_check_child (id) VALUES (1)"));

        assertThat(translator.translate(ex))
                .isInstanceOf(SystemException.class)
                .hasCause(ex);
    }

    @Test
    @DisplayName("制約違反以外の例外は、システムエラーになる")
    void otherExceptionBecomesSystemError() {
        var ex = catchRuntimeException(() -> dsl.execute("SELECT * FROM common.no_such_table"));

        assertThat(translator.translate(ex))
                .isInstanceOf(SystemException.class)
                .hasCause(ex);
    }
}
