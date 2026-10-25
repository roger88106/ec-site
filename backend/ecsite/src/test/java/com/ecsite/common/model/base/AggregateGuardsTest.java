package com.ecsite.common.model.base;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        @DisplayName("戻すときに例外が出れば、名前だけを書いた（保存した状態は書かない）、復元の失敗のシステムの例外にする")
        void wrapsFailure() {
            var cause = new IllegalArgumentException("壊れた状態");

            assertThatThrownBy(() -> AggregateGuards.restore("tax category", () -> {
                throw cause;
            }))
                    .isInstanceOfSatisfying(SystemException.class, ex -> {
                        assertThat(ex.getErrorDetail().errorCode()).isEqualTo(CommonErrorCode.RESTORE_ERROR.getCode());
                        assertThat(ex.getMessage()).isEqualTo("tax category restore error.").doesNotContain("Snapshot");
                    })
                    .hasCause(cause);
        }
    }
}
