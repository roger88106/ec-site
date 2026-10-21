package com.ecsite.module.media.infrastructure.converter;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 画像の変換（imgproxy）の設定を有効にする。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ImgproxyProperties.class)
class ImgproxyConfig {
}
