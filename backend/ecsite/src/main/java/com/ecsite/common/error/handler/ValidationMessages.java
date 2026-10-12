package com.ecsite.common.error.handler;

import com.ecsite.common.message.MessageHelper;
import jakarta.validation.ConstraintViolation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
     * 制約の種類と属性から文を作る。
     *
     * @param constraint 制約の種類（注釈の単純名。例：Size）
     * @param attributes 制約の属性（min、max、value など）
     * @return 文
     */
    public String of(String constraint, Map<String, Object> attributes) {
        String type = constraint.toLowerCase(Locale.ROOT);
        if (!KNOWN.contains(type)) {
            return messageHelper.getMessage(PREFIX + "invalid", Map.of());
        }
        // 長さの下限が 0 なら、上限だけの文にする
        if (type.equals("size") && attributes.get("min") instanceof Integer min && min == 0) {
            return messageHelper.getMessage(PREFIX + "size_max", attributes);
        }
        return messageHelper.getMessage(PREFIX + type, attributes);
    }
}
