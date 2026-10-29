package com.ecsite.module.notification.application.port;

/**
 * メールを送る port（docs/03-機能仕様/notification.md「メールの送信」）。<br />
 * SMTP の型とエラーは実装の中に閉じ、送れなければ実行時例外を投げる。
 */
public interface Mailer {

    /**
     * メールを送る。送信元は設定の値。
     *
     * @param mail メール
     */
    void send(Mail mail);

    /**
     * テキストのメール
     *
     * @param to      宛先
     * @param subject 件名
     * @param body    本文
     */
    record Mail(String to, String subject, String body) {
    }
}
