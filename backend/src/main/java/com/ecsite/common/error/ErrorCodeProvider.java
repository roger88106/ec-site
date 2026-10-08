package com.ecsite.common.error;

/**
 * エラーコードプロバイダー
 */
public interface ErrorCodeProvider {

    /**
     * エラーコード取得
     *
     * @return {@link ErrorCode}
     */
    ErrorCode getCode();

}
