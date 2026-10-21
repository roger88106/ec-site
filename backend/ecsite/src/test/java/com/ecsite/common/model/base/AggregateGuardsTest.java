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
 * 集約の版の比べ方と、集約を戻すときの失敗の扱いの単体テスト（spec 013 の DC-137）
 */
class AggregateGuardsTest {

    @Nested
    @DisplayName("版の比べ方")
    class Versions {

        @Test
        @DisplayName("読んだ時点の版と今の版が同じなら、何もしない")
        void acceptsSameVersion() {
            assertThatCode(() -> AggregateGuards.requireVersion(3, 3)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("版が違えば、更新の競合の業務のエラー")
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
            assertThat(AggregateGuards.restore("category", "snapshot", () -> "restored")).isEqualTo("restored");
        }

        @Test
        @DisplayName("戻すときに例外が出れば、名前と保存した状態を書いた、復元の失敗のシステムの例外にする")
        void wrapsFailure() {
            var cause = new IllegalArgumentException("壊れた状態");

            assertThatThrownBy(() -> AggregateGuards.restore("tax category", "Snapshot[id=1]", () -> {
                throw cause;
            }))
                    .isInstanceOfSatisfying(SystemException.class, ex -> {
                        assertThat(ex.getErrorDetail().errorCode()).isEqualTo(CommonErrorCode.RESTORE_ERROR.getCode());
                        assertThat(ex.getMessage()).isEqualTo("tax category restore error.\nsnapshot: Snapshot[id=1]\n");
                    })
                    .hasCause(cause);
        }
    }
}
