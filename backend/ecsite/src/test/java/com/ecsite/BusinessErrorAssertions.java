package com.ecsite;

import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * エラーの確認の補助
 *
 * <p>処理が、業務のエラーやシステムのエラーで失敗することを、エラーコードで確認する。
 */
public final class BusinessErrorAssertions {

    /**
     * インスタンスを作らない。
     */
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

    /**
     * 処理が、指定したエラーコードのシステムのエラーで失敗することを確認する。
     *
     * @param callable  処理
     * @param errorCode 期待するエラーコード
     */
    public static void assertSystemError(ThrowingCallable callable, ErrorCodeProvider errorCode) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(SystemException.class, ex ->
                        assertThat(ex.getErrorDetail().errorCode().code()).isEqualTo(errorCode.getCode().code()));
    }
}
