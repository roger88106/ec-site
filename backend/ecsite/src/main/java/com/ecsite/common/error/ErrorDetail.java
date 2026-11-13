package com.ecsite.common.error;

import lombok.NonNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * エラー詳細情報
 *
 * <p>差し込む値は、文にするとき（{@link com.ecsite.common.message.MessageResolver}）に文字にする。
 *
 * @param errorCode エラーコード
 * @param params    文に差し込む値（値の名前 → 値）
 */
public record ErrorDetail(@NonNull ErrorCode errorCode, @NonNull Map<String, Object> params) {

    /**
     * エラー詳細情報を生成する。
     *
     * @param errorCode     エラーコード
     * @param messageParams 文に差し込む値（エラーコードの値の名前と同じ順）
     * @return {@link ErrorDetail}
     * @throws IllegalArgumentException 値の数が、エラーコードの値の名前の数と合わない
     */
    public static ErrorDetail of(ErrorCode errorCode, List<Object> messageParams) {
        var keys = errorCode.message().paramKeys();
        // 数が合わないのはプログラムの誤り
        if (keys.size() != messageParams.size()) {
            throw new IllegalArgumentException("差し込む値の数が、値の名前の数と合わない：" + errorCode.code());
        }
        var params = new LinkedHashMap<String, Object>();
        for (int i = 0; i < keys.size(); i++) {
            params.put(keys.get(i), messageParams.get(i));
        }
        return new ErrorDetail(errorCode, Collections.unmodifiableMap(params));
    }

    /**
     * 差し込む値のないエラー詳細情報を生成する。
     *
     * @param errorCode エラーコード
     * @return {@link ErrorDetail}
     * @throws IllegalArgumentException エラーコードが差し込む値の名前を持つ
     */
    public static ErrorDetail of(ErrorCode errorCode) {
        return of(errorCode, List.of());
    }
}
