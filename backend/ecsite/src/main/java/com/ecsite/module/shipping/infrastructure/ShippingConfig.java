package com.ecsite.module.shipping.infrastructure;

import com.ecsite.module.shipping.application.ShippingProperties;
import com.ecsite.module.shipping.infrastructure.carrier.CarrierSignature;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 出荷モジュールの設定（物流サービス、通知の署名の鍵）を有効にする。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ShippingProperties.class)
class ShippingConfig {

    /**
     * 物流サービスの通知の署名を作る。
     *
     * @param properties 出荷の設定
     * @return 物流サービスの通知の署名
     */
    @Bean
    CarrierSignature carrierSignature(ShippingProperties properties) {
        return new CarrierSignature(properties.webhookSecret());
    }
}
