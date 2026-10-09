package com.ecsite.common.error.handler;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.ErrorCode;
import com.ecsite.common.error.ErrorDetail;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.common.message.MessageHelper;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

/**
 * グローバルエラーハンドラー。<br />
 * すべてのエラーを RFC 9457（Problem Details）に次の項目を加えた形で返す（constitution 9）。
 * <ul>
 *     <li>{@code errorCode}：フロントが分岐に使うコード</li>
 *     <li>{@code message}：画面にそのまま出せる日本語の文字列</li>
 *     <li>{@code traceId}：すべてのエラーで返す</li>
 *     <li>{@code errors}：入力検証エラーのときだけ</li>
 * </ul>
 * アプリの例外に加え、Spring MVC の標準の例外（{@link ResponseEntityExceptionHandler} が扱うもの）も同じ形に変換する。
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * ProblemDetail追加パラメータ：エラーコード
     */
    private static final String PROPERTY_ERROR_CODE = "errorCode";
    /**
     * ProblemDetail追加パラメータ：エラーメッセージ
     */
    private static final String PROPERTY_MESSAGE = "message";
    /**
     * ProblemDetail追加パラメータ：トレース ID
     */
    private static final String PROPERTY_TRACE_ID = "traceId";
    /**
     * ProblemDetail追加パラメータ：フィールド検証エラー一覧
     */
    private static final String PROPERTY_ERRORS = "errors";

    /**
     * メッセージヘルパー
     */
    private final MessageHelper messageHelper;

    /**
     * トレース ID の解決
     */
    private final TraceIdResolver traceIdResolver;

    // ============================== アプリの例外 ==============================

    /**
     * ビジネス例外のハンドラー
     *
     * @param ex      {@link BusinessException} ビジネス例外
     * @param request リクエスト
     * @return Http 4XX + 例外明細
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusinessException(BusinessException ex, WebRequest request) {
        ErrorDetail errorDetail = ex.getErrorDetail();

        // 公開してはいけないエラーは、システムエラーとして返す
        if (errorDetail.errorCode().exposure() == ErrorCode.ErrorExposure.DOWNGRADE_REQUIRED) {
            return handleUnexpected(ex, request);
        }

        HttpStatus status = resolveHttpStatus(errorDetail.errorCode());
        String traceId = traceIdResolver.resolve();
        log.debug("業務エラー。errorCode={}, traceId={}", errorDetail.errorCode().code(), traceId);

        return buildResponse(status, HttpHeaders.EMPTY, errorDetail.errorCode(),
                messageHelper.getMessage(errorDetail), null, traceId, request);
    }

    /**
     * システム例外のハンドラー
     *
     * @param ex      {@link SystemException} システム例外
     * @param request リクエスト
     * @return Http 500 + 例外明細
     */
    @ExceptionHandler(SystemException.class)
    public ResponseEntity<Object> handleSystemException(SystemException ex, WebRequest request) {
        return handleUnexpected(ex, request);
    }

    /**
     * 全ての例外のハンドラー
     *
     * @param ex      {@link Exception} その他例外
     * @param request リクエスト
     * @return Http 500 + 例外明細
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllException(Exception ex, WebRequest request) {
        return handleUnexpected(ex, request);
    }

    // ============================== 入力検証 ==============================

    /**
     * リクエスト本文の検証例外のハンドラー<br />
     * {@code @Valid} で拒否されたフィールドごとのメッセージを {@code errors} に列挙する。
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        List<FieldValidationError> errors = toFieldErrors(ex.getBindingResult().getAllErrors().stream());
        return handleInvalidRequest(status, headers, errors, request);
    }

    /**
     * メソッド引数の検証例外のハンドラー<br />
     * {@code @RequestParam} などに付いた制約（生成インターフェースの {@code @Max} など）で拒否された場合に発生する。
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            @NonNull HandlerMethodValidationException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        List<FieldValidationError> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> {
                    // 本文などのオブジェクトの検証はフィールド単位、それ以外は引数単位で返す
                    if (result instanceof ParameterErrors parameterErrors) {
                        return toFieldErrors(parameterErrors.getAllErrors().stream()).stream();
                    }
                    String parameterName = result.getMethodParameter().getParameterName();
                    return result.getResolvableErrors().stream()
                            .map(error -> new FieldValidationError(parameterName, error.getDefaultMessage()));
                })
                .toList();
        return handleInvalidRequest(status, headers, errors, request);
    }

    /**
     * メソッド引数の検証例外のハンドラー（{@code @Validated} の Controller）<br />
     * 生成インターフェースは {@code @Validated} 付きなので、{@code @RequestParam} の制約は Spring MVC ではなく
     * メソッド検証の AOP で検査され、{@link HandlerMethodValidationException} ではなくこの例外になる。
     * {@code errors} の {@code field} は、プロパティパスの最後（引数名）にする。
     *
     * @param ex      {@link ConstraintViolationException} 制約違反
     * @param request リクエスト
     * @return Http 400 + 例外明細
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolationException(ConstraintViolationException ex, WebRequest request) {
        List<FieldValidationError> errors = ex.getConstraintViolations().stream()
                .map(violation -> {
                    String field = null;
                    for (Path.Node node : violation.getPropertyPath()) {
                        field = node.getName();
                    }
                    return new FieldValidationError(field, violation.getMessage());
                })
                .toList();
        return handleInvalidRequest(HttpStatus.BAD_REQUEST, HttpHeaders.EMPTY, errors, request);
    }

    // ============================== Spring MVC の標準の例外 ==============================

    /**
     * Spring MVC の標準の例外を、アプリのエラーレスポンスの形に変換する。<br />
     * {@link ResponseEntityExceptionHandler} が扱う例外（型の不一致、必須パラメータの欠落、存在しないパス、
     * 許可されていないメソッドなど）は、すべてここを通る。HTTP ステータスとヘッダーは Spring MVC の決めたものを使う。
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            @NonNull Exception ex, @Nullable Object body, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode statusCode, @NonNull WebRequest request) {
        if (statusCode.is5xxServerError()) {
            return handleUnexpected(ex, request);
        }

        ErrorCode errorCode = resolveErrorCode(statusCode);
        String traceId = traceIdResolver.resolve();
        log.debug("リクエストの誤り。status={}, exception={}, traceId={}", statusCode.value(), ex.toString(), traceId);

        return buildResponse(statusCode, headers, errorCode,
                messageHelper.getMessage(errorCode.message()), null, traceId, request);
    }

    // ============================== プライベートメソッド ==============================

    /**
     * 入力検証エラーのレスポンスを作る。
     */
    private ResponseEntity<Object> handleInvalidRequest(
            HttpStatusCode status, HttpHeaders headers, List<FieldValidationError> errors, WebRequest request) {
        ErrorCode errorCode = CommonErrorCode.INVALID_REQUEST.getCode();
        String traceId = traceIdResolver.resolve();
        log.debug("入力検証エラー。errors={}, traceId={}", errors, traceId);

        return buildResponse(status, headers, errorCode,
                messageHelper.getMessage(errorCode.message()), errors, traceId, request);
    }

    /**
     * 想定外のエラー（500）のレスポンスを作る。<br />
     * 原因はログにだけ出し、レスポンスには共通のシステムエラーを返す。ログとレスポンスに同じトレース ID を出す。
     */
    private ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        ErrorCode errorCode = CommonErrorCode.SYSTEM_ERROR.getCode();
        String traceId = traceIdResolver.resolve();
        log.error("システムエラーが発生しました。traceId={}", traceId, ex);

        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, HttpHeaders.EMPTY, errorCode,
                messageHelper.getMessage(errorCode.message()), null, traceId, request);
    }

    /**
     * エラーレスポンスを組み立てる。
     *
     * @param status    HTTP ステータス
     * @param headers   HTTP ヘッダー（405 の Allow など、Spring MVC が付けたもの）
     * @param errorCode エラーコード
     * @param message   画面にそのまま出せるメッセージ
     * @param errors    フィールドごとの検証エラー（入力検証エラー以外は null）
     * @param traceId   トレース ID
     * @param request   リクエスト
     * @return エラーレスポンス
     */
    private ResponseEntity<Object> buildResponse(
            HttpStatusCode status, HttpHeaders headers, ErrorCode errorCode, String message,
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

    /**
     * 業務系のエラーコードから HTTP ステータスを決める。
     *
     * @param errorCode エラーコード
     * @return HTTP ステータス
     */
    private static HttpStatus resolveHttpStatus(ErrorCode errorCode) {
        return switch (errorCode.type()) {
            case VALIDATION_ERROR, BUSINESS_RULE_ERROR -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND_ERROR -> HttpStatus.NOT_FOUND;
            case CONFLICT_ERROR -> HttpStatus.CONFLICT;
            case RESTORE_ERROR, SYSTEM_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * Spring MVC の標準の例外の HTTP ステータスから、エラーコードを決める。
     *
     * @param status HTTP ステータス（4xx）
     * @return エラーコード
     */
    private static ErrorCode resolveErrorCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> CommonErrorCode.NOT_FOUND.getCode();
            case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED.getCode();
            default -> CommonErrorCode.INVALID_REQUEST.getCode();
        };
    }

    /**
     * 検証エラーをフィールド単位の形に変換する。
     *
     * @param errors 検証エラー
     * @return フィールドごとの検証エラー
     */
    private static List<FieldValidationError> toFieldErrors(Stream<ObjectError> errors) {
        return errors
                .map(error -> new FieldValidationError(
                        error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList();
    }

    // ============================== プライベートレコード ==============================

    /**
     * フィールド検証エラー
     *
     * @param field   フィールド名
     * @param message エラーメッセージ
     */
    private record FieldValidationError(String field, @Nullable String message) {
    }
}
