package com.ecsite.module.notification.infrastructure.mail;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 通知の設定
 *
 * @param from メールの送信元のアドレス
 */
@Validated
@ConfigurationProperties(prefix = "ecsite.notification")
public record NotificationProperties(@NotBlank @Email String from) {
}
