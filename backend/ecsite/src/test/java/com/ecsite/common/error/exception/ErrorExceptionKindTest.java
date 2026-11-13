package com.ecsite.common.error.exception;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 業務の例外とシステムの例外の、エラーの区分の単体テスト
 */
class ErrorExceptionKindTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(ErrorType.class)
    @DisplayName("エラーの種類は、応答が 5xx ならシステムの区分、それ以外は業務の区分")
    void systemIffServerError(ErrorType type) {
        assertThat(type.system()).isEqualTo(type.status().is5xxServerError());
    }

    @Nested
    @DisplayName("業務の例外を作る")
    class OfBusiness {

        @Test
        @DisplayName("業務の区分のエラーコードなら作れる")
        void createsWithBusinessCode() {
            var ex = BusinessException.of(CommonErrorCode.NOT_FOUND);

            assertThat(ex.getErrorDetail().errorCode()).isEqualTo(CommonErrorCode.NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("システムの区分のエラーコードは、プログラムの誤りとして IllegalArgumentException にする")
        void rejectsSystemCode() {
            assertThatThrownBy(() -> BusinessException.of(CommonErrorCode.SYSTEM_ERROR))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SYSTEM_ERROR");
            assertThatThrownBy(() -> BusinessException.of(CommonErrorCode.RESTORE_ERROR, List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("RESTORE_ERROR");
        }
    }

    @Nested
    @DisplayName("システムの例外を作る")
    class OfSystem {

        @Test
        @DisplayName("システムの区分のエラーコードなら作れる")
        void createsWithSystemCode() {
            var ex = SystemException.of("復元の失敗", null, CommonErrorCode.RESTORE_ERROR);

            assertThat(ex.getErrorDetail().errorCode()).isEqualTo(CommonErrorCode.RESTORE_ERROR.getCode());
        }

        @Test
        @DisplayName("エラーコードを指定しなければ、共通のシステムエラーにする")
        void defaultsToSystemError() {
            var ex = SystemException.of("予期しないエラー", null);

            assertThat(ex.getErrorDetail().errorCode()).isEqualTo(CommonErrorCode.SYSTEM_ERROR.getCode());
        }

        @Test
        @DisplayName("業務の区分のエラーコードは、プログラムの誤りとして IllegalArgumentException にする")
        void rejectsBusinessCode() {
            assertThatThrownBy(() -> SystemException.of("誤り", null, CommonErrorCode.CONCURRENT_MODIFICATION))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CONCURRENT_MODIFICATION");
        }
    }
}
