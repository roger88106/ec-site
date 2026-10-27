package com.ecsite.common.model.base;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 集約のバージョンの比べ方と、集約を戻すときの失敗の扱いの単体テスト
 */
class AggregateGuardsTest {

    @Nested
    @DisplayName("バージョンの比べ方")
    class Versions {

        @Test
        @DisplayName("読んだ時点のバージョンと今のバージョンが同じなら、何もしない")
        void acceptsSameVersion() {
            assertThatCode(() -> AggregateGuards.requireVersion(3, 3)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("バージョンが違えば、更新の競合の業務のエラー")
        void rejectsDifferentVersion() {
            assertThatThrownBy(() -> AggregateGuards.requireVersion(4, 3))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorDetail().errorCode())
                            .isEqualTo(CommonErrorCode.CONCURRENT_MODIFICATION.getCode()));
        }
    }

    @Nested
    @DisplayName("集約を戻す")
    class Restore {

        @Test
        @DisplayName("戻せれば、戻した集約を返す")
        void returnsRestored() {
            assertThat(AggregateGuards.restore("category", () -> "restored")).isEqualTo("restored");
        }

        @Test
        @DisplayName("戻すときに例外が出れば、集約の名前と原因の例外の種類を書いた（保存した状態は書かない）、復元の失敗のシステムの例外にする")
        void wrapsFailure() {
            var cause = new IllegalArgumentException("壊れた状態");

            assertThatThrownBy(() -> AggregateGuards.restore("tax category", () -> {
                throw cause;
            }))
                    .isInstanceOfSatisfying(SystemException.class, ex -> {
                        assertThat(ex.getErrorDetail().errorCode()).isEqualTo(CommonErrorCode.RESTORE_ERROR.getCode());
                        assertThat(ex.getMessage())
                                .isEqualTo("tax category restore error: java.lang.IllegalArgumentException")
                                .doesNotContain("Snapshot");
                    });
        }

        @Test
        @DisplayName("投げる例外の連鎖（メッセージ、原因、抑制した例外）のどこにも、原因の例外のメッセージが残らない")
        void removesMessagesFromChain() {
            var secret = "secret-" + UUID.randomUUID();
            var failure = failureWithChain(secret);

            var thrown = catchThrowable(() -> AggregateGuards.restore("category", () -> {
                throw failure;
            }));

            assertThat(thrown).isInstanceOf(SystemException.class);
            assertNoMessageContains(thrown, secret, Collections.newSetFromMap(new IdentityHashMap<>()));
            var printed = new StringWriter();
            thrown.printStackTrace(new PrintWriter(printed));
            assertThat(printed.toString()).doesNotContain(secret).contains("category restore error");
        }

        @Test
        @DisplayName("原因、原因の原因、抑制した例外は、それぞれ元の例外の種類とスタックトレースを持つ（失敗した場所が分かる）")
        void keepsTypesAndStackTraces() {
            var failure = failureWithChain("secret-" + UUID.randomUUID());
            var rootCause = failure.getCause();
            var suppressed = failure.getSuppressed()[0];

            var thrown = catchThrowable(() -> AggregateGuards.restore("category", () -> {
                throw failure;
            }));

            var cause = thrown.getCause();
            assertThat(cause).isNotNull().isNotSameAs(failure);
            assertThat(cause.getMessage()).isEqualTo(IllegalArgumentException.class.getName());
            assertThat(cause.getStackTrace()).containsExactly(failure.getStackTrace());
            var causeOfCause = cause.getCause();
            assertThat(causeOfCause).isNotNull();
            assertThat(causeOfCause.getMessage()).isEqualTo(IllegalStateException.class.getName());
            assertThat(causeOfCause.getStackTrace()).containsExactly(rootCause.getStackTrace());
            assertThat(cause.getSuppressed()).singleElement().satisfies(s -> {
                assertThat(s.getMessage()).isEqualTo(UnsupportedOperationException.class.getName());
                assertThat(s.getStackTrace()).containsExactly(suppressed.getStackTrace());
                assertThat(s.getCause()).isNotNull();
                assertThat(s.getCause().getMessage()).isEqualTo(ArithmeticException.class.getName());
            });
        }

        @Test
        @DisplayName("原因の連鎖が輪になっていても、輪の手前で止めて、復元の失敗のシステムの例外にする")
        void stopsAtCyclicCause() {
            var first = new IllegalArgumentException("first");
            var second = new IllegalStateException("second", first);
            first.initCause(second);

            var thrown = catchThrowable(() -> AggregateGuards.restore("category", () -> {
                throw first;
            }));

            assertThat(thrown).isInstanceOf(SystemException.class);
            assertThat(thrown.getCause()).isNotNull();
            assertThat(thrown.getCause().getCause()).isNotNull();
            assertThat(thrown.getCause().getCause().getCause()).isNull();
        }

        /**
         * 値を入れたメッセージを持つ例外を作る。原因の原因と、原因を持つ抑制した例外を含む。
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
         * 例外と、その原因・抑制した例外を再帰的にたどり、どのメッセージにも値がないことを確かめる。
         *
         * @param throwable 確かめる例外
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
    }
}
