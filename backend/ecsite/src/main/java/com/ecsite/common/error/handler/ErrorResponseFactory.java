package com.ecsite.common.error.handler;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.ErrorCode;
import com.ecsite.common.error.ErrorDetail;
import com.ecsite.common.message.MessageResolver;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.util.List;

/**
 * エラーレスポンスのファクトリー
 *
 * <p>RFC 9457（Problem Details）に次の項目を加えた応答を作る。ログは出さない（呼ぶ側の handler が出す）。
 * <ul>
 *     <li>{@code errorCode}：分岐に使うコード</li>
 *     <li>{@code message}：画面にそのまま出せる日本語の文字列</li>
 *     <li>{@code traceId}：すべてのエラーで返す</li>
 *     <li>{@code errors}：入力検証エラーのときだけ</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
class ErrorResponseFactory {

    /**
     * ProblemDetail の追加の項目：エラーコード
     */
    private static final String PROPERTY_ERROR_CODE = "errorCode";

    /**
     * ProblemDetail の追加の項目：エラーメッセージ
     */
    private static final String PROPERTY_MESSAGE = "message";

    /**
     * ProblemDetail の追加の項目：トレース ID
     */
    private static final String PROPERTY_TRACE_ID = "traceId";

    /**
     * ProblemDetail の追加の項目：項目ごとの入力検証のエラー
     */
    private static final String PROPERTY_ERRORS = "errors";

    /**
     * メッセージの解決
     */
    private final MessageResolver messageResolver;

    /**
     * アプリの例外のエラー詳細から応答を作る。
     *
     * <p>HTTP ステータスはエラーの種類で決める。
     *
     * @param errorDetail エラー詳細
     * @param traceId     トレース ID
     * @param request     リクエスト
     * @return エラーレスポンス
     */
    ResponseEntity<Object> of(ErrorDetail errorDetail, String traceId, WebRequest request) {
        var errorCode = errorDetail.errorCode();
        return create(errorCode.type().status(), HttpHeaders.EMPTY, errorCode, messageResolver.getMessage(errorDetail),
                null, traceId, request);
    }

    /**
     * 入力検証エラーの応答を作る。
     *
     * @param status  HTTP ステータス（Spring MVC が決めたもの）
     * @param headers HTTP ヘッダー（Spring MVC が付けたもの）
     * @param errors  項目ごとの入力検証のエラー
     * @param traceId トレース ID
     * @param request リクエスト
     * @return エラーレスポンス
     */
    ResponseEntity<Object> invalidRequest(HttpStatusCode status, HttpHeaders headers, List<FieldValidationError> errors,
                                          String traceId, WebRequest request) {
        var errorCode = CommonErrorCode.INVALID_REQUEST.getCode();
        return create(status, headers, errorCode, messageResolver.getMessage(errorCode.message()), errors, traceId, request);
    }

    /**
     * Spring MVC の標準の例外（4xx）の応答を作る。
     *
     * <p>エラーコードは HTTP ステータスで決める（404・405 のほかは入力の誤り）。
     *
     * @param status  HTTP ステータス（Spring MVC が決めたもの）
     * @param headers HTTP ヘッダー（405 の Allow など、Spring MVC が付けたもの）
     * @param traceId トレース ID
     * @param request リクエスト
     * @return エラーレスポンス
     */
    ResponseEntity<Object> standard(HttpStatusCode status, HttpHeaders headers, String traceId, WebRequest request) {
        var errorCode = switch (status.value()) {
            case 404 -> CommonErrorCode.NOT_FOUND.getCode();
            case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED.getCode();
            default -> CommonErrorCode.INVALID_REQUEST.getCode();
        };
        return create(status, headers, errorCode, messageResolver.getMessage(errorCode.message()), null, traceId, request);
    }

    /**
     * 想定外のエラー（500）の応答を作る。
     *
     * <p>原因は応答に出さず、共通のシステムエラーを返す。
     *
     * @param traceId トレース ID
     * @param request リクエスト
     * @return エラーレスポンス
     */
    ResponseEntity<Object> unexpected(String traceId, WebRequest request) {
        var errorCode = CommonErrorCode.SYSTEM_ERROR.getCode();
        return create(HttpStatus.INTERNAL_SERVER_ERROR, HttpHeaders.EMPTY, errorCode,
                messageResolver.getMessage(errorCode.message()), null, traceId, request);
    }

// ============================== プライベートメソッド ==============================

    /**
     * エラーレスポンスを作る。
     *
     * @param status    HTTP ステータス
     * @param headers   HTTP ヘッダー
     * @param errorCode エラーコード
     * @param message   画面にそのまま出せるメッセージ
     * @param errors    項目ごとの入力検証のエラー。入力検証エラー以外は null
     * @param traceId   トレース ID
     * @param request   リクエスト
     * @return エラーレスポンス
     */
    private static ResponseEntity<Object> create(HttpStatusCode status, HttpHeaders headers, ErrorCode errorCode, String message,
                                                 @Nullable List<FieldValidationError> errors, String traceId, WebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        HttpStatus httpStatus = HttpStatus.resolve(status.value());
        if (httpStatus != null) {
            detail.setTitle(httpStatus.getReasonPhrase());
        }
        if (request instanceof ServletWebRequest servletWebRequest) {
            detail.setInstance(URI.create(servletWebRequest.getRequest().getRequestURI()));
        }

        detail.setProperty(PROPERTY_ERROR_CODE, errorCode.code());
        detail.setProperty(PROPERTY_MESSAGE, message);
        detail.setProperty(PROPERTY_TRACE_ID, traceId);
        if (errors != null) {
            detail.setProperty(PROPERTY_ERRORS, errors);
        }

        return ResponseEntity.status(status).headers(headers).body(detail);
    }
}
