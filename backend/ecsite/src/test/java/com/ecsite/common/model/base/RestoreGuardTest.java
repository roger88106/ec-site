package com.ecsite.common.model.base;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.SystemException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;

import static com.ecsite.support.BusinessErrorAssertions.assertSystemError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 保存した値の復元の失敗の扱いの単体テスト
 */
class RestoreGuardTest {

    // ============================== 定数 ==============================

    /**
     * 集約の名前
     */
    private static final String AGGREGATE = "category";

    // ============================== テスト ==============================

    @Nested
    @DisplayName("戻せたとき")
    class Restored {

        @Test
        @DisplayName("戻せれば、戻した集約を返す")
        void returnsRestored() {
            // 操作と確認：戻した値をそのまま返す
            assertThat(RestoreGuard.restore(AGGREGATE, () -> "restored")).isEqualTo("restored");
        }
    }

    @Nested
    @DisplayName("戻すときに例外が出たとき")
    class Failed {

        @Test
        @DisplayName("復元の失敗のシステムの例外にする")
        void wrapsFailureAsRestoreError() {
            // 準備：保存した状態を書いたメッセージの例外
            var cause = new IllegalArgumentException("壊れた状態");

            // 操作と確認：復元の失敗のエラーコードのシステムの例外
            assertThatThrownBy(() -> RestoreGuard.restore("tax category", () -> {
                throw cause;
            }))
                    .isInstanceOfSatisfying(SystemException.class, ex ->
                            assertThat(ex.getErrorDetail().errorCode()).isEqualTo(CommonErrorCode.RESTORE_ERROR.getCode()));
        }

        @Test
        @DisplayName("例外のメッセージには、集約の名前と原因の例外の種類だけを書き、保存した状態は書かない")
        void writesNameAndCauseTypeOnly() {
            // 準備：保存した状態を書いたメッセージの例外
            var cause = new IllegalArgumentException("壊れた状態");

            // 操作と確認：集約の名前と原因の例外の種類だけのメッセージ
            assertThatThrownBy(() -> RestoreGuard.restore("tax category", () -> {
                throw cause;
            }))
                    .isInstanceOfSatisfying(SystemException.class, ex -> assertThat(ex.getMessage())
                            .isEqualTo("tax category restore error: java.lang.IllegalArgumentException")
                            .doesNotContain("Snapshot"));
        }

        @Test
        @DisplayName("投げる例外の連鎖（メッセージ、原因、抑制した例外）のどこにも、原因の例外のメッセージが残らない")
        void removesMessagesFromChain() {
            // 準備：原因・抑制した例外のメッセージにも値を入れた例外
            var secret = "secret-" + UUID.randomUUID();
            var failure = failureWithChain(secret);

            // 操作：戻すときに投げる
            var thrown = restoreThrowing(failure);

            // 確認：システムの例外で、連鎖のどのメッセージにも値がない
            assertThat(thrown).isInstanceOf(SystemException.class);
            assertNoMessageContains(thrown, secret, Collections.newSetFromMap(new IdentityHashMap<>()));
        }

        @Test
        @DisplayName("出力したスタックトレースに、原因の例外のメッセージが出ない")
        void removesMessagesFromPrintedStackTrace() {
            // 準備：原因・抑制した例外のメッセージにも値を入れた例外
            var secret = "secret-" + UUID.randomUUID();
            var failure = failureWithChain(secret);

            // 操作：戻すときに投げた例外のスタックトレースを出力する
            var printed = new StringWriter();
            restoreThrowing(failure).printStackTrace(new PrintWriter(printed));

            // 確認：値がなく、復元の失敗であることは出る
            assertThat(printed.toString()).doesNotContain(secret).contains(AGGREGATE + " restore error");
        }
    }

    @Nested
    @DisplayName("既に復元のエラーになった例外が出たとき")
    class AlreadyRestoreError {

        @Test
        @DisplayName("入れ子の復元で内側が失敗すれば、外側は包み直さず同じ例外を投げる")
        void rethrowsSameInstance() {
            // 準備：内側の復元で失敗した、復元のエラーの例外
            var inner = catchThrowable(() -> RestoreGuard.restore("inner", () -> {
                throw new IllegalArgumentException("壊れた状態");
            }));

            // 操作：外側の復元の中で、その例外が出る
            var thrown = catchThrowable(() -> RestoreGuard.restore("outer", () -> {
                throw (RuntimeException) inner;
            }));

            // 確認：同じインスタンス
            assertThat(thrown).isSameAs(inner);
        }

        @Test
        @DisplayName("外側の名前のメッセージにならず、内側のメッセージと原因が残る")
        void keepsInnerMessageAndCause() {
            // 準備と操作：入れ子の復元で内側が失敗する
            var thrown = catchThrowable(() -> RestoreGuard.restore("outer", () ->
                    RestoreGuard.restore("inner", () -> {
                        throw new IllegalArgumentException("壊れた状態");
                    })));

            // 確認：内側の名前と原因の種類のメッセージ、原因の写し
            assertThat(thrown).isInstanceOfSatisfying(SystemException.class, ex -> {
                assertThat(ex.getMessage()).isEqualTo("inner restore error: java.lang.IllegalArgumentException");
                assertThat(ex.getErrorDetail().errorCode()).isEqualTo(CommonErrorCode.RESTORE_ERROR.getCode());
            });
            assertThat(thrown.getCause()).isNotNull();
            assertThat(thrown.getCause().getMessage()).isEqualTo(IllegalArgumentException.class.getName());
        }

        @Test
        @DisplayName("復元のエラーでないシステムの例外は、復元のエラーに変換する")
        void convertsOtherSystemException() {
            // 準備：復元のエラーでないエラーコード（共通のシステムエラー）のシステムの例外
            var other = SystemException.of("other", null);

            // 操作と確認：復元のエラーの別の例外になり、原因の種類がメッセージに出る
            var thrown = restoreThrowing(other);
            assertThat(thrown).isNotSameAs(other).isInstanceOfSatisfying(SystemException.class, ex -> {
                assertThat(ex.getErrorDetail().errorCode()).isEqualTo(CommonErrorCode.RESTORE_ERROR.getCode());
                assertThat(ex.getMessage()).isEqualTo(AGGREGATE + " restore error: " + SystemException.class.getName());
            });
        }
    }

    @Nested
    @DisplayName("原因の例外の写し")
    class CopiedChain {

        @Test
        @DisplayName("原因は、元の例外の種類とスタックトレースを持つ写し")
        void copiesCause() {
            // 準備：原因の原因と抑制した例外を持つ例外
            var failure = failureWithChain("secret-" + UUID.randomUUID());

            // 操作：戻すときに投げる
            var cause = restoreThrowing(failure).getCause();

            // 確認：元の例外そのものでない写しで、種類とスタックトレースが元の例外と同じ
            assertThat(cause).isNotSameAs(failure);
            assertCopyOf(cause, failure);
        }

        @Test
        @DisplayName("原因の原因は、元の例外の種類とスタックトレースを持つ")
        void copiesCauseOfCause() {
            // 準備：原因の原因と抑制した例外を持つ例外
            var failure = failureWithChain("secret-" + UUID.randomUUID());

            // 操作：戻すときに投げる
            var cause = restoreThrowing(failure).getCause();

            // 確認：原因の原因の種類とスタックトレースが、元の原因と同じ
            assertThat(cause).isNotNull();
            assertCopyOf(cause.getCause(), failure.getCause());
        }

        @Test
        @DisplayName("抑制した例外は、元の例外の種類とスタックトレースと、原因の種類を持つ")
        void copiesSuppressed() {
            // 準備：原因の原因と抑制した例外を持つ例外
            var failure = failureWithChain("secret-" + UUID.randomUUID());
            var suppressed = failure.getSuppressed()[0];

            // 操作：戻すときに投げる
            var cause = restoreThrowing(failure).getCause();

            // 確認：抑制した例外が 1 件で、元の抑制した例外の写し。その原因の種類も元と同じ
            assertThat(cause).isNotNull();
            assertThat(cause.getSuppressed()).singleElement().satisfies(s -> {
                assertCopyOf(s, suppressed);
                assertThat(s.getCause()).isNotNull();
                assertThat(s.getCause().getMessage()).isEqualTo(ArithmeticException.class.getName());
            });
        }
    }

    @Nested
    @DisplayName("輪になった原因の連鎖")
    class CyclicCause {

        @Test
        @DisplayName("原因の連鎖が輪になっていても、復元の失敗のシステムの例外にする")
        void wrapsCyclicFailureAsRestoreError() {
            // 準備：互いを原因にする 2 つの例外
            var first = cyclicFailure();

            // 操作と確認：復元の失敗のエラーコードのシステムの例外
            assertSystemError(() -> RestoreGuard.restore(AGGREGATE, () -> {
                throw first;
            }), CommonErrorCode.RESTORE_ERROR);
        }

        @Test
        @DisplayName("原因の連鎖が輪になっていれば、輪の手前で止める")
        void stopsAtCyclicCause() {
            // 準備：互いを原因にする 2 つの例外
            var first = cyclicFailure();

            // 操作：戻すときに投げる
            var thrown = restoreThrowing(first);

            // 確認：システムの例外で、原因が 2 段までで終わる
            assertThat(thrown).isInstanceOf(SystemException.class);
            assertThat(thrown.getCause()).isNotNull();
            assertThat(thrown.getCause().getCause()).isNotNull();
            assertThat(thrown.getCause().getCause().getCause()).isNull();
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 例外が、元の例外の写しであることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>写しがある</li>
     *   <li>メッセージが、元の例外の種類の名前</li>
     *   <li>スタックトレースが、元の例外と同じ</li>
     * </ul>
     *
     * @param copy     確認する写し
     * @param original 元の例外
     */
    private static void assertCopyOf(Throwable copy, Throwable original) {
        assertThat(copy).isNotNull();
        assertThat(copy.getMessage()).isEqualTo(original.getClass().getName());
        assertThat(copy.getStackTrace()).containsExactly(original.getStackTrace());
    }

    /**
     * 例外と、その原因・抑制した例外を再帰的にたどり、どのメッセージにも値がないことを確認する。
     *
     * @param throwable 確認する例外
     * @param secret    含まれてはいけない値
     * @param visited   たどった例外（輪になった連鎖で止まるため）
     */
    private static void assertNoMessageContains(Throwable throwable, String secret, Set<Throwable> visited) {
        if (!visited.add(throwable)) {
            return;
        }
        assertThat(throwable.getMessage()).doesNotContain(secret);
        assertThat(throwable.getLocalizedMessage()).doesNotContain(secret);
        assertThat(throwable.toString()).doesNotContain(secret);
        if (throwable.getCause() != null) {
            assertNoMessageContains(throwable.getCause(), secret, visited);
        }
        for (var suppressed : throwable.getSuppressed()) {
            assertNoMessageContains(suppressed, secret, visited);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 戻すときに例外を投げ、復元の失敗の扱いが投げた例外を返す。
     *
     * @param failure 戻すときに投げる例外
     * @return 投げた例外
     */
    private static Throwable restoreThrowing(RuntimeException failure) {
        return catchThrowable(() -> RestoreGuard.restore(AGGREGATE, () -> {
            throw failure;
        }));
    }

    /**
     * 値を入れたメッセージを持つ例外を作る。
     *
     * <p>原因の原因（{@link IllegalStateException}）を持つ。
     * 抑制した例外（{@link UnsupportedOperationException}）は、原因（{@link ArithmeticException}）を持つ。
     *
     * @param secret メッセージに入れる値
     * @return 復元で投げる例外
     */
    private static IllegalArgumentException failureWithChain(String secret) {
        var rootCause = new IllegalStateException("root " + secret);
        var failure = new IllegalArgumentException("failure " + secret, rootCause);
        failure.addSuppressed(new UnsupportedOperationException("suppressed " + secret,
                new ArithmeticException("suppressed cause " + secret)));
        return failure;
    }

    /**
     * 互いを原因にする 2 つの例外を作る。
     *
     * @return 輪の始めの例外
     */
    private static IllegalArgumentException cyclicFailure() {
        var first = new IllegalArgumentException("first");
        var second = new IllegalStateException("second", first);
        first.initCause(second);
        return first;
    }
}
