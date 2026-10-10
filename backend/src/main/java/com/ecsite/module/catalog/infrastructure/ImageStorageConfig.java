package com.ecsite.module.catalog.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 画像の置き場所の設定を読む。S3 のクライアントはメディアモジュールの設定のものを使う。<br />
 * 商品カタログをメディアモジュールの API に切り替えるまでの間だけ置く（T1030 で消す）。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ImageStorageProperties.class)
class ImageStorageConfig {
}
