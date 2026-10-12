package com.ecsite.common.error.handler;

import com.ecsite.common.message.MessageHelper;
import jakarta.validation.ConstraintViolation;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.stereotype.Component;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 入力の検証のエラーの文を作る（docs/style/backend.md 第 3.5 節）。<br />
 * 枠組みの既定の文（JVM のロケールで言語が変わる）を使わず、制約の種類から決めたキー
 * {@code ecsite.validation.<種類の小文字>} の文を messages.properties から取り、制約の値を差し込む。
 */
@Component
@RequiredArgsConstructor
public class ValidationMessages {

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
     * メッセージの取り出し
     */
    private final MessageHelper messageHelper;

    /**
     * 制約の違反から文を作る。
     *
     * @param violation 制約の違反
     * @return 文
     */
    public String of(ConstraintViolation<?> violation) {
        var descriptor = violation.getConstraintDescriptor();
        return of(descriptor.getAnnotation().annotationType().getSimpleName(), descriptor.getAttributes());
    }

    /**
     * 検証エラーの文を作る。Bean Validation の違反なら制約の種類から作り、そうでなければ（型の変換の失敗など）
     * 入力の検証の共通の文にする。
     *
     * @param error 検証エラー
     * @return 文
     */
    public String of(ObjectError error) {
        if (error.contains(ConstraintViolation.class)) {
            return of(error.unwrap(ConstraintViolation.class));
        }
        return of(INVALID, Map.of());
    }

    /**
     * メソッド引数の検証エラーの文を作る。Bean Validation の違反でなければ、入力の検証の共通の文にする。
     *
     * @param result 引数の検証の結果
     * @param error  検証エラー
     * @return 文
     */
    public String of(ParameterValidationResult result, MessageSourceResolvable error) {
        // 元が Bean Validation の違反でなければ、取り出せない（null）
        ConstraintViolation<?> violation = result.unwrap(error, ConstraintViolation.class);
        return violation == null ? of(INVALID, Map.of()) : of(violation);
    }

    /**
     * 制約の種類と属性から文を作る。
     *
     * @param constraint 制約の種類（注釈の単純名。例：Size）
     * @param attributes 制約の属性（min、max、value など）
     * @return 文
     */
    public String of(String constraint, Map<String, Object> attributes) {
        String type = constraint.toLowerCase(Locale.ROOT);
        if (!KNOWN.contains(type)) {
            return messageHelper.getMessage(PREFIX + INVALID, Map.of());
        }
        // 長さの下限が 0 なら、上限だけの文にする
        if (type.equals("size") && attributes.get("min") instanceof Integer min && min == 0) {
            return messageHelper.getMessage(PREFIX + "size_max", attributes);
        }
        return messageHelper.getMessage(PREFIX + type, attributes);
    }
}
