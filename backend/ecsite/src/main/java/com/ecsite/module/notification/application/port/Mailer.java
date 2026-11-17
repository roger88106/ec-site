package com.ecsite.module.notification.application.port;

/**
 * メールの送信
 */
public interface Mailer {

    /**
     * メールを送る。
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
