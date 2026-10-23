package com.ecsite.common.message;

import com.ecsite.common.error.ErrorCode;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * エラーメッセージ
 *
 * @param code   I18nメッセージコード
 * @param params メッセージパラメータ
 */
public record ErrorMessage(@NonNull String code, @Nullable Map<String, String> params) {

    /**
     * エラーメッセージ作成
     *
     * @param errorCode     エラーコード
     * @param messageParams メッセージパラメータ
     * @return メッセージマップ
     */
    public static ErrorMessage of(@NonNull ErrorCode errorCode, @NonNull List<Object> messageParams) {

        I18nMessage i18n = errorCode.message();
        List<String> paramKeys = i18n.paramKeys();

        // パラメータ数が合わない場合は例外をスローする
        // ※ハンドラー層のため、バックエンドのエラーページに遷移する想定
        if (paramKeys.size() != messageParams.size()) {
            throw new IllegalArgumentException("messageParams size is not equal to paramKeys size");
        }

        // ゼロ件の場合、空のマップを返す
        if (paramKeys.isEmpty()) {
            return new ErrorMessage(i18n.code(), null);
        }

        // パラメータ設定
        Map<String, String> messageParamsMap = IntStream.range(0, paramKeys.size())
                .boxed()
                .collect(Collectors.toMap(
                        paramKeys::get,
                        i -> MessageHelper.formatValue(messageParams.get(i))
                ));

        return new ErrorMessage(i18n.code(), messageParamsMap);
    }
}
