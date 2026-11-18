package com.ecsite.module.shipping.infrastructure;

import com.ecsite.module.shipping.infrastructure.carrier.CarrierSignature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 出荷モジュールの設定
 *
 * <p>物流サービスの通知の署名を作る。
 */
@Configuration(proxyBeanMethods = false)
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
