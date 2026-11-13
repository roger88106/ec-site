package com.ecsite.common.error.handler;

import com.ecsite.common.error.ErrorDetail;
import com.ecsite.common.error.ValidationErrorMapping;
import com.ecsite.common.message.MessageResolver;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 入力検証のエラーメッセージ
 *
 * <p>入力検証のエラーを、応答の {@code errors}（項目と文の一覧）にする。
 * 文は、制約の種類から決めたキー {@code ecsite.validation.<種類の小文字>} で messages.properties から取る
 * （フレームワークの既定の文は JVM のロケールで言語が変わるので使わない）。
 * 業務のエラーに対応づけた項目（{@link ValidationErrorMapping}）は、その業務のエラーの文にする。
 */
@Component
public class ValidationErrorTranslator {

    /**
     * キーの接頭辞
     */
    private static final String PREFIX = "ecsite.validation.";

    /**
     * 文を用意していない種類（共通の文）
     */
    private static final String INVALID = "invalid";

    /**
     * 文を用意している制約の種類（小文字）
     */
    private static final Set<String> KNOWN = Set.of(
            "notnull", "notblank", "size", "min", "max", "decimalmin", "decimalmax", "pattern", "email");

    /**
     * メッセージの解決
     */
    private final MessageResolver messageResolver;

    /**
     * 項目のパスごとの業務のエラー（各モジュールの対応を合わせたもの）
     */
    private final Map<String, ErrorDetail> errorsByField;

    /**
     * 各モジュールの対応を合わせる。
     *
     * <p>同じパスを複数のモジュールが対応づけていれば、起動で失敗させる。
     *
     * @param messageResolver メッセージの解決
     * @param mappings        各モジュールが、形の検証の誤りを業務のエラーに対応づけたもの
     * @throws IllegalStateException 同じ項目のパスに、複数の業務のエラーが対応づけられている
     */
    public ValidationErrorTranslator(MessageResolver messageResolver, List<ValidationErrorMapping> mappings) {
        this.messageResolver = messageResolver;
        var merged = new HashMap<String, ErrorDetail>();
        mappings.forEach(mapping -> mapping.errorsByField().forEach((field, detail) -> {
            if (merged.putIfAbsent(field, detail) != null) {
                throw new IllegalStateException("同じ項目のパスに、複数の業務のエラーが対応づけられている：" + field);
            }
        }));
        this.errorsByField = Map.copyOf(merged);
    }

    /**
     * リクエスト本文などのオブジェクトの検証エラーを、項目ごとのエラーにする。
     *
     * @param errors 検証エラー
     * @return 項目ごとのエラー
     */
    List<FieldValidationError> fieldErrors(List<? extends ObjectError> errors) {
        return errors.stream()
                .map(error -> new FieldValidationError(
                        // クラスに付いた制約など、フィールドに結び付かないエラーはオブジェクト名にする
                        error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                        messageOf(error)))
                .toList();
    }

    /**
     * メソッド引数の検証エラーを、項目ごとのエラーにする。
     *
     * <p>本文などのオブジェクトの検証は項目単位、それ以外は引数単位にする。
     *
     * @param ex メソッド引数の検証の例外
     * @return 項目ごとのエラー
     */
    List<FieldValidationError> fieldErrors(HandlerMethodValidationException ex) {
        return ex.getParameterValidationResults().stream()
                .flatMap(result -> {
                    if (result instanceof ParameterErrors parameterErrors) {
                        return fieldErrors(parameterErrors.getAllErrors()).stream();
                    }
                    var parameterName = result.getMethodParameter().getParameterName();
                    return result.getResolvableErrors().stream()
                            .map(error -> new FieldValidationError(parameterName, messageOf(result, error)));
                })
                .toList();
    }

    /**
     * メソッド検証の AOP の制約の違反を、項目ごとのエラーにする。
     *
     * <p>項目の名前は、プロパティパス（メソッド名.引数名.…）の最後の名前にする。
     *
     * @param ex 制約の違反の例外
     * @return 項目ごとのエラー
     */
    List<FieldValidationError> fieldErrors(ConstraintViolationException ex) {
        return ex.getConstraintViolations().stream()
                .map(violation -> {
                    String field = null;
                    for (Path.Node node : violation.getPropertyPath()) {
                        field = node.getName();
                    }
                    return new FieldValidationError(field, messageOf(violation));
                })
                .toList();
    }

    /**
     * 制約の違反から文を作る。
     *
     * @param violation 制約の違反
     * @return 文
     */
    public String messageOf(ConstraintViolation<?> violation) {
        var descriptor = violation.getConstraintDescriptor();
        return messageOf(descriptor.getAnnotation().annotationType().getSimpleName(), descriptor.getAttributes(),
                isItems(violation.getInvalidValue()));
    }

    /**
     * 検証エラーの文を作る。
     *
     * <p>業務のエラーに対応づけた項目ならその文、Bean Validation の違反なら制約の種類から作り、
     * そうでなければ（型の変換の失敗など）入力の検証の共通の文にする。
     *
     * @param error 検証エラー
     * @return 文
     */
    public String messageOf(ObjectError error) {
        if (error instanceof FieldError fieldError && errorsByField.containsKey(fieldError.getField())) {
            return messageResolver.getMessage(errorsByField.get(fieldError.getField()));
        }
        if (error.contains(ConstraintViolation.class)) {
            return messageOf(error.unwrap(ConstraintViolation.class));
        }
        return messageOf(INVALID, Map.of());
    }

    /**
     * メソッド引数の検証エラーの文を作る。
     *
     * <p>Bean Validation の違反でなければ、入力の検証の共通の文にする。
     *
     * @param result 引数の検証の結果
     * @param error  検証エラー
     * @return 文
     */
    public String messageOf(ParameterValidationResult result, MessageSourceResolvable error) {
        // 元が Bean Validation の違反でなければ、取り出せない（null）
        ConstraintViolation<?> violation = result.unwrap(error, ConstraintViolation.class);
        return violation == null ? messageOf(INVALID, Map.of()) : messageOf(violation);
    }

    /**
     * 制約の種類と属性から文を作る。
     *
     * @param constraint 制約の種類（注釈の単純名。例：Size）
     * @param attributes 制約の属性（min、max、value など）
     * @return 文
     */
    public String messageOf(String constraint, Map<String, Object> attributes) {
        return messageOf(constraint, attributes, false);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 制約の種類と属性から文を作る。
     *
     * <p>配列・一覧の長さは、文字数ではなく件数の文にする。
     *
     * @param constraint 制約の種類（注釈の単純名。例：Size）
     * @param attributes 制約の属性（min、max、value など）
     * @param items      値が配列・一覧か
     * @return 文
     */
    private String messageOf(String constraint, Map<String, Object> attributes, boolean items) {
        String type = constraint.toLowerCase(Locale.ROOT);
        if (!KNOWN.contains(type)) {
            return messageResolver.getMessage(PREFIX + INVALID, Map.of());
        }
        if (type.equals("size")) {
            String key = items ? "size_items" : "size";
            // 下限が 0 なら上限だけの文、上限がなければ（件数のとき）下限だけの文にする
            if (attributes.get("min") instanceof Integer min && min == 0) {
                return messageResolver.getMessage(PREFIX + key + "_max", attributes);
            }
            if (items && attributes.get("max") instanceof Integer max && max == Integer.MAX_VALUE) {
                return messageResolver.getMessage(PREFIX + key + "_min", attributes);
            }
            return messageResolver.getMessage(PREFIX + key, attributes);
        }
        return messageResolver.getMessage(PREFIX + type, attributes);
    }

    /**
     * 値が配列・一覧（件数を数えるもの）かを返す。
     *
     * @param value 値
     * @return 配列・一覧・マップなら true
     */
    private static boolean isItems(@Nullable Object value) {
        return value instanceof Collection<?> || value instanceof Map<?, ?> || (value != null && value.getClass().isArray());
    }
}
