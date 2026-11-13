package com.ecsite.common.error.handler;

import org.jspecify.annotations.Nullable;

/**
 * 項目ごとの入力検証のエラー（応答の {@code errors} の要素）
 *
 * @param field   項目の名前
 * @param message エラーメッセージ
 */
record FieldValidationError(@Nullable String field, @Nullable String message) {
}
