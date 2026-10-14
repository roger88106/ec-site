package com.ecsite.module.shipping.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 出荷の設定。環境変数で上書きする（例：ECSITE_SHIPPING_CARRIER）。
 *
 * @param carrier       物流サービス
 * @param webhookSecret 物流サービスの通知の署名の鍵。Git に入れず、環境変数 {@code CARRIER_WEBHOOK_SECRET} で渡す（application.yml）
 */
@Validated
@ConfigurationProperties("ecsite.shipping")
public record ShippingProperties(
        @DefaultValue("mock") @NotNull Carrier carrier,
        @NotBlank String webhookSecret) {

    @Override
    public String toString() {
        // 秘密の値をログに出さない
        return "ShippingProperties[carrier=" + carrier + ", webhookSecret=***]";
    }

    /**
     * 物流サービス
     */
    public enum Carrier {

        /**
         * 内部の mock（自動テスト、CI、ローカル）
         */
        MOCK,
    }
}
