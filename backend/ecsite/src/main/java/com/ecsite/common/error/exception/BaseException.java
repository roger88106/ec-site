package com.ecsite.common.error.exception;

import com.ecsite.common.error.ErrorDetail;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * 独自の例外の基底クラス
 */
@Getter
public abstract class BaseException extends RuntimeException {

    /**
     * エラー詳細情報
     */
    private final ErrorDetail errorDetail;

    /**
     * コンストラクタ。
     *
     * @param debugMessage デバッグ用メッセージ
     * @param cause        原因となった例外
     * @param errorDetail  エラー詳細情報
     */
    protected BaseException(String debugMessage, @Nullable Throwable cause, ErrorDetail errorDetail) {
        super(debugMessage, cause);

        this.errorDetail = errorDetail;
    }

}
