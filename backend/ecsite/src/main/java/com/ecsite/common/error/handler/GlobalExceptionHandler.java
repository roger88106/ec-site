package com.ecsite.common.error.handler;

import com.ecsite.common.error.ErrorType;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * グローバルエラーハンドラー
 *
 * <p>例外ごとの入口。トレース ID を決めてログを出し、応答は {@link ErrorResponseFactory} で作る。
 * アプリの例外に加え、Spring MVC の標準の例外（{@link ResponseEntityExceptionHandler} が扱うもの）も同じ形にする。
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * エラーレスポンスのファクトリー
     */
    private final ErrorResponseFactory responses;

    /**
     * 入力検証のエラーメッセージ
     */
    private final ValidationErrorTranslator validationErrors;

    /**
     * トレース ID の解決
     */
    private final TraceIdResolver traceIdResolver;

    // ============================== アプリの例外 ==============================

    /**
     * ビジネス例外を応答にする。
     *
     * @param ex      ビジネス例外
     * @param request リクエスト
     * @return 4xx + エラー詳細
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusinessException(BusinessException ex, WebRequest request) {
        var traceId = traceIdResolver.resolve();
        log.debug("業務エラー。errorCode={}, traceId={}", ex.getErrorDetail().errorCode().code(), traceId);
        return responses.of(ex.getErrorDetail(), traceId, request);
    }

    /**
     * システム例外を応答にする。
     *
     * <p>外部のサービスに接続できないエラーは、エラーコードとメッセージを返す（502）。
     * それ以外は想定外のエラーにする。
     *
     * @param ex      システム例外
     * @param request リクエスト
     * @return 502 または 500 + エラー詳細
     */
    @ExceptionHandler(SystemException.class)
    public ResponseEntity<Object> handleSystemException(SystemException ex, WebRequest request) {
        // 外部のサービスに接続できないエラーだけは、やり直せることを示すため、エラーコードとメッセージを返す
        if (ex.getErrorDetail().errorCode().type() != ErrorType.EXTERNAL_SERVICE_ERROR) {
            return handleUnexpected(ex, request);
        }
        var traceId = traceIdResolver.resolve();
        log.warn("外部のサービスに接続できない。errorCode={}, traceId={}", ex.getErrorDetail().errorCode().code(), traceId, ex);
        return responses.of(ex.getErrorDetail(), traceId, request);
    }

    /**
     * 全ての例外を応答にする。
     *
     * @param ex      その他の例外
     * @param request リクエスト
     * @return 500 + エラー詳細
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllException(Exception ex, WebRequest request) {
        return handleUnexpected(ex, request);
    }

    // ============================== 入力検証 ==============================

    /**
     * リクエスト本文の検証例外を応答にする。
     *
     * <p>{@code @Valid} で拒否された項目ごとのメッセージを {@code errors} に列挙する。
     *
     * @param ex      検証例外
     * @param headers 応答のヘッダー
     * @param status  応答の HTTP ステータス
     * @param request リクエスト
     * @return 400 + エラー詳細
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        return handleInvalidRequest(status, headers, validationErrors.fieldErrors(ex.getBindingResult().getAllErrors()), request);
    }

    /**
     * メソッド引数の検証例外を応答にする。
     *
     * <p>{@code @RequestParam} などに付いた制約（生成インターフェースの {@code @Max} など）で拒否された場合に発生する。
     *
     * @param ex      検証例外
     * @param headers 応答のヘッダー
     * @param status  応答の HTTP ステータス
     * @param request リクエスト
     * @return 400 + エラー詳細
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            @NonNull HandlerMethodValidationException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        return handleInvalidRequest(status, headers, validationErrors.fieldErrors(ex), request);
    }

    /**
     * メソッド引数の制約違反を応答にする。
     *
     * <p>{@code @Validated} の Controller で発生する。生成インターフェースは {@code @Validated} 付きなので、{@code @RequestParam} の制約は Spring MVC ではなく
     * メソッド検証の AOP で検査され、{@link HandlerMethodValidationException} ではなくこの例外になる。
     *
     * @param ex      制約違反
     * @param request リクエスト
     * @return 400 + エラー詳細
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolationException(ConstraintViolationException ex, WebRequest request) {
        return handleInvalidRequest(HttpStatus.BAD_REQUEST, HttpHeaders.EMPTY, validationErrors.fieldErrors(ex), request);
    }

    // ============================== Spring MVC の標準の例外 ==============================

    /**
     * Spring MVC の標準の例外を、アプリのエラーレスポンスの形にする。
     *
     * <p>{@link ResponseEntityExceptionHandler} が扱う例外（型の不一致、必須パラメータの欠落、存在しないパス、
     * 許可されていないメソッドなど）は、全てここを通る。HTTP ステータスとヘッダーは Spring MVC の決めたものを使う。
     *
     * @param ex         例外
     * @param body       応答の本文（使わない）
     * @param headers    応答のヘッダー
     * @param statusCode 応答の HTTP ステータス
     * @param request    リクエスト
     * @return 4xx または 500 + エラー詳細
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            @NonNull Exception ex, @Nullable Object body, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode statusCode, @NonNull WebRequest request) {
        if (statusCode.is5xxServerError()) {
            return handleUnexpected(ex, request);
        }
        var traceId = traceIdResolver.resolve();
        log.debug("リクエストの誤り。status={}, exception={}, traceId={}", statusCode.value(), ex.toString(), traceId);
        return responses.standard(statusCode, headers, traceId, request);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 入力検証エラーのログを出し、応答を作る。
     */
    private ResponseEntity<Object> handleInvalidRequest(
            HttpStatusCode status, HttpHeaders headers, List<FieldValidationError> errors, WebRequest request) {
        var traceId = traceIdResolver.resolve();
        log.debug("入力検証エラー。errors={}, traceId={}", errors, traceId);
        return responses.invalidRequest(status, headers, errors, traceId, request);
    }

    /**
     * 想定外のエラーのログを出し、応答（500）を作る。
     *
     * <p>原因はログにだけ出す。ログと応答に同じトレース ID を出す。
     */
    private ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        var traceId = traceIdResolver.resolve();
        log.error("システムエラーが発生しました。traceId={}", traceId, ex);
        return responses.unexpected(traceId, request);
    }
}
