package com.ecsite.common.error.exception;

import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorDetail;
import lombok.NonNull;

import java.util.List;

/**
 * ビジネス例外 ※HTTP 400系
 */
public class BusinessException extends BaseException {

    /**
     * {@code BusinessException}の新しいインスタンスを作成して返します。
     *
     * @param errorCodeProvider エラーコードプロバイダー
     * @return 指定されたパラメータで初期化された新しい{@code BusinessException}インスタンス
     */
    public static BusinessException of(@NonNull ErrorCodeProvider errorCodeProvider) {
        return new BusinessException(ErrorDetail.of(errorCodeProvider.getCode()));
    }

    /**
     * {@code BusinessException}の新しいインスタンスを作成して返します。
     *
     * @param errorCodeProvider エラーコードプロバイダー
     * @param messageParams     メッセージパラメータ
     * @return 指定されたパラメータで初期化された新しい{@code BusinessException}インスタンス
     */
    public static BusinessException of(@NonNull ErrorCodeProvider errorCodeProvider, List<Object> messageParams) {
        return new BusinessException(ErrorDetail.of(errorCodeProvider.getCode(), messageParams));
    }

    /**
     * ビジネス例外
     *
     * @param errorDetail エラー明細
     */
    private BusinessException(ErrorDetail errorDetail) {
        super(errorDetail.errorCode().code(), null, errorDetail);
    }
}
