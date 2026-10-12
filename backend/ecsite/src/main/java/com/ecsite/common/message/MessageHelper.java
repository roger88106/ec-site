package com.ecsite.common.message;

import com.ecsite.common.error.ErrorDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * メッセージヘルパー。<br />
 * メッセージのコードとパラメータから、画面にそのまま出せる文字列を組み立てる。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MessageHelper {

    /**
     * メッセージソース
     */
    private final MessageSource messageSource;

    /**
     * メッセージを取得する。
     *
     * @param message I18n メッセージ（パラメータなし）
     * @return メッセージ
     */
    public String getMessage(I18nMessage message) {
        return getMessageTemplate(message.code());
    }

    /**
     * エラー明細のメッセージを取得する。パラメータを埋め込む。
     *
     * @param errorDetail エラー明細
     * @return メッセージ
     */
    public String getMessage(ErrorDetail errorDetail) {
        ErrorMessage message = errorDetail.message();
        return formatMessage(getMessageTemplate(message.code()), message.params());
    }

    // ============================== プライベートメソッド ==============================

    /**
     * メッセージをフォーマットする。
     *
     * @param template メッセージテンプレート
     * @param params   パラメータマップ
     * @return フォーマット済みメッセージ
     */
    private String formatMessage(String template, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return template;
        }
        // パラメータをテンプレートに置換
        for (Map.Entry<String, String> entry : params.entrySet()) {
            template = template.replace("{%s}".formatted(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return template;
    }

    /**
     * メッセージテンプレートを取得する。
     *
     * @param code メッセージコード
     * @return メッセージテンプレート。未設定の場合はコードをそのまま返す
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
