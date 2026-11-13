package com.ecsite;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.SystemException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 復元の失敗のテストの補助
 *
 * <p>DB の値を戻すときの失敗の単体テストで使う。
 * 列の制約が壊れた値を入れさせないので、Repository に、表ごとの行を返す jOOQ の mock を渡して失敗させる。
 */
public final class RestoreFailures {

    /**
     * 行を作る jOOQ の文脈
     *
     * <p>DB に接続しない。
     */
    public static final DSLContext RECORDS = DSL.using(SQLDialect.POSTGRES);

    /**
     * 表の名前 → 返す行
     */
    private final Map<String, Record> rowsByTable = new LinkedHashMap<>();

    /**
     * 表が返す行を追加する。
     *
     * <p>追加していない表は行を返さない。
     *
     * @param table 表の名前
     * @param row   返す行
     * @return この補助
     */
    public RestoreFailures with(String table, Record row) {
        rowsByTable.put(table, row);
        return this;
    }

    /**
     * 追加した行を返す jOOQ の文脈を作る。
     *
     * @return jOOQ の文脈
     */
    public DSLContext dsl() {
        return DSL.using(new MockConnection(context -> new MockResult[]{resultOf(context.sql())}), SQLDialect.POSTGRES);
    }

    /**
     * 例外のメッセージに入ると分かる値を作る。
     *
     * @return 値
     */
    public static String value() {
        return "secret-" + UUID.randomUUID();
    }

    /**
     * 処理が復元の失敗のシステムの例外を投げ、その例外を出力しても（原因と抑制した例外を含めて）値が出ないことを確認する。
     *
     * @param failing 失敗する処理
     * @param value   出てはいけない値
     */
    public static void assertValueIsHidden(ThrowingCallable failing, String value) {
        var thrown = catchThrowable(failing);
        var printed = new StringWriter();
        thrown.printStackTrace(new PrintWriter(printed));
        assertThat(printed.toString()).doesNotContain(value).contains("restore error");
        assertThat(thrown).isInstanceOfSatisfying(SystemException.class, ex ->
                assertThat(ex.getErrorDetail().errorCode()).isEqualTo(CommonErrorCode.RESTORE_ERROR.getCode()));
    }

    /**
     * SQL が読む表の行を返す。
     *
     * @param sql SQL
     * @return 結果
     */
    private MockResult resultOf(String sql) {
        return rowsByTable.entrySet().stream()
                .filter(entry -> sql.contains('"' + entry.getKey() + '"'))
                .findFirst()
                .map(entry -> {
                    var result = RECORDS.newResult(entry.getValue().fields());
                    result.add(entry.getValue());
                    return new MockResult(1, result);
                })
                .orElseGet(() -> new MockResult(0, RECORDS.newResult()));
    }
}
