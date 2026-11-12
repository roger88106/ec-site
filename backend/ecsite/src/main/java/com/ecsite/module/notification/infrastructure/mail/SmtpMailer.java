package com.ecsite.module.notification.infrastructure.mail;

import com.ecsite.module.notification.application.port.Mailer;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP のメールの送信
 *
 * <p>Spring Boot のメールで送る。接続先は設定 spring.mail.* にある。
 */
@Component
@RequiredArgsConstructor
class SmtpMailer implements Mailer {

    /**
     * Spring Boot のメールの送信
     */
    private final JavaMailSender mailSender;

    /**
     * 通知の設定
     */
    private final NotificationProperties properties;

    /**
     * メールを SMTP で送る。
     *
     * <p>送信元は設定の値。
     *
     * @param mail メール
     * @throws MailException 送れなかったとき
     */
    @Override
    public void send(Mail mail) {
        var message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(mail.to());
        message.setSubject(mail.subject());
        message.setText(mail.body());
        mailSender.send(message);
    }
}
