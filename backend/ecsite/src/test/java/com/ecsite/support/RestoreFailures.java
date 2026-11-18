package com.ecsite.support;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.SystemException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final Map<String, List<Record>> rowsByTable = new LinkedHashMap<>();

    /**
     * 表の名前 → 読むと失敗する DB の例外
     */
    private final Map<String, SQLException> failuresByTable = new LinkedHashMap<>();

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
        rowsByTable.put(table, List.of(row));
        return this;
    }

    /**
     * 表が返す複数の行を追加する。
     *
     * <p>1 つの表が複数の行を返す場面（子の表の上限を超える件数など）に使う。
     *
     * @param table 表の名前
     * @param rows  返す行（同じ列を持つこと）
     * @return この補助
     */
    public RestoreFailures withRows(String table, List<Record> rows) {
        rowsByTable.put(table, List.copyOf(rows));
        return this;
    }

    /**
     * 表を読むと DB の失敗になるようにする。
     *
     * <p>失敗は jOOQ の DataAccessException になる。
     *
     * @param table 表の名前
     * @return この補助
     */
    public RestoreFailures failingOn(String table) {
        failuresByTable.put(table, new SQLException("simulated database failure"));
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
     * 処理が復元の失敗になり、値が出ず、原因が狙った検査の例外であることを確認する。
     *
     * <p>壊した行が、狙った条件以外の理由で失敗しても通らないようにする。原因は、元の例外の種類だけを持つ例外に置き換えてあるので、
     * そのメッセージ（元の種類の完全修飾名）で比べる。
     *
     * @param failing   失敗する処理
     * @param value     出てはいけない値
     * @param causeType 復元の失敗の原因になるはずの例外の種類
     */
    public static void assertValueIsHidden(ThrowingCallable failing, String value, Class<? extends Throwable> causeType) {
        assertValueIsHidden(failing, value);
        var thrown = catchThrowable(failing);
        assertThat(thrown.getCause()).hasMessage(causeType.getName());
    }

    /**
     * 処理が DB の失敗のまま出ることを確認する。
     *
     * <p>復元のエラーに変わらないこと（子の表を読む失敗を、復元の失敗として扱わないこと）を確認する。
     *
     * @param failing 失敗する処理
     */
    public static void assertDatabaseFailurePropagates(ThrowingCallable failing) {
        assertThat(catchThrowable(failing)).isInstanceOf(DataAccessException.class).isNotInstanceOf(SystemException.class);
    }

    /**
     * SQL が読む表の行を返す。
     *
     * @param sql SQL
     * @return 結果
     */
    private MockResult resultOf(String sql) throws SQLException {
        for (var failure : failuresByTable.entrySet()) {
            if (sql.contains('"' + failure.getKey() + '"')) {
                throw failure.getValue();
            }
        }
        return rowsByTable.entrySet().stream()
                .filter(entry -> sql.contains('"' + entry.getKey() + '"'))
                .findFirst()
                .map(entry -> {
                    var rows = entry.getValue();
                    var result = RECORDS.newResult(rows.getFirst().fields());
                    result.addAll(rows);
                    return new MockResult(rows.size(), result);
                })
                .orElseGet(() -> new MockResult(0, RECORDS.newResult()));
    }
}
