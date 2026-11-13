package com.ecsite.common.message;

import com.ecsite.common.error.ErrorDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * メッセージの解決
 *
 * <p>メッセージのコードと差し込む値から、画面にそのまま出せる文字列を組み立てる。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MessageResolver {

    /**
     * 文の中の差し込む位置（{@code {名前}}）
     */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");

    /**
     * メッセージソース
     */
    private final MessageSource messageSource;

    /**
     * メッセージを取得する。
     *
     * @param message I18n メッセージ（差し込む値なし）
     * @return メッセージ
     */
    public String getMessage(I18nMessage message) {
        return getMessageTemplate(message.code());
    }

    /**
     * エラー詳細のメッセージを取得する。
     *
     * <p>差し込む値を、名前で差し込む。
     *
     * @param errorDetail エラー詳細
     * @return メッセージ
     */
    public String getMessage(ErrorDetail errorDetail) {
        return formatMessage(getMessageTemplate(errorDetail.errorCode().message().code()), errorDetail.params());
    }

    /**
     * コードの文を取り出し、値を名前で差し込む。
     *
     * <p>整数は 3 桁ごとにカンマで区切る（例：10,000）。
     *
     * @param code   メッセージのコード
     * @param params 差し込む値の名前 → 値
     * @return メッセージ
     */
    public String getMessage(String code, Map<String, ?> params) {
        return formatMessage(getMessageTemplate(code), params);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 文に値を差し込む。
     *
     * <p>差し込む位置を 1 回だけ走査して置き換えるので、差し込んだ値の中の {@code {名前}} は置き換えない。
     * 値のない名前の位置は、そのまま残す。
     *
     * @param template 文
     * @param params   差し込む値の名前 → 値
     * @return 値を差し込んだ文
     */
    private static String formatMessage(String template, Map<String, ?> params) {
        if (params.isEmpty()) {
            return template;
        }
        return PLACEHOLDER.matcher(template).replaceAll(match -> {
            var name = match.group(1);
            var text = params.containsKey(name) ? formatValue(params.get(name)) : match.group();
            return Matcher.quoteReplacement(text);
        });
    }

    /**
     * 差し込む値を文字にする。
     *
     * <p>整数は 3 桁ごとにカンマで区切る。
     *
     * @param value 値
     * @return 文字
     */
    private static String formatValue(@Nullable Object value) {
        if (value instanceof Long || value instanceof Integer || value instanceof Short) {
            return "%,d".formatted(((Number) value).longValue());
        }
        return value == null ? "" : value.toString();
    }

    /**
     * メッセージの文を取得する。
     *
     * @param code メッセージのコード
     * @return 文。未設定の場合はコードをそのまま返す
     */
    private String getMessageTemplate(String code) {
        try {
            return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException e) {
            log.error("メッセージ未設定。code={}", code);
            return code;
        }
    }
}
