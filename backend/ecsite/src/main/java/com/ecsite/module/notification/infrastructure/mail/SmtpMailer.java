package com.ecsite.module.notification.infrastructure.mail;

import com.ecsite.module.notification.application.port.Mailer;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP でメールを送る（Spring Boot のメール。接続先は設定 spring.mail.*）。<br />
 * 送れなければ Spring の MailException（実行時例外）がそのまま伝わる。
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
