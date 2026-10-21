package com.ecsite.module.shipping.application;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 出荷の設定。<br />
 * 物流サービスは mock フラグ（{@code ecsite.shipping.mock.enabled}）で選び、ここには持たない。
 *
 * @param webhookSecret 物流サービスの通知の署名の鍵。Git に入れず、環境変数 {@code CARRIER_WEBHOOK_SECRET} で渡す（application.yml）
 */
@Validated
@ConfigurationProperties("ecsite.shipping")
public record ShippingProperties(@NotBlank String webhookSecret) {

    @Override
    public String toString() {
        // 秘密の値をログに出さない
        return "ShippingProperties[webhookSecret=***]";
    }
}
