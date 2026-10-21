package com.ecsite;

import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.exception.BusinessException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 業務のエラーを確認するための補助。
 */
public final class BusinessErrorAssertions {

    private BusinessErrorAssertions() {
    }

    /**
     * 処理が、指定したエラーコードの業務のエラーで失敗することを確認する。
     *
     * @param callable  処理
     * @param errorCode 期待するエラーコード
     */
    public static void assertBusinessError(ThrowingCallable callable, ErrorCodeProvider errorCode) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorDetail().errorCode().code()).isEqualTo(errorCode.getCode().code()));
    }
}
