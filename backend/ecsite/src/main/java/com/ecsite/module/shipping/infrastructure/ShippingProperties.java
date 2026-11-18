package com.ecsite.module.shipping.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 出荷の設定
 *
 * <p>物流サービスは mock のフラグ（{@code ecsite.shipping.mock.enabled}）で選び、ここには持たない。
 *
 * @param webhookSecret             物流サービスの通知の署名の鍵。Git に入れず、環境変数 {@code CARRIER_WEBHOOK_SECRET} で渡す
 * @param occurredAtMaxAge          通知の発生日時の古さの上限。現在時刻からこの期間以上前の発生日時の通知は拒否する
 * @param occurredAtFutureTolerance 通知の発生日時の未来の許容。現在時刻からこの期間を超えて先の発生日時の通知は拒否する
 */
@Validated
@ConfigurationProperties("ecsite.shipping")
public record ShippingProperties(
        @NotBlank String webhookSecret,
        @DefaultValue("30d") @NotNull @DurationMin(millis = 1) Duration occurredAtMaxAge,
        @DefaultValue("5m") @NotNull @DurationMin(millis = 1) Duration occurredAtFutureTolerance) {

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        // 秘密の値をログに出さない
        return "ShippingProperties[webhookSecret=***, occurredAtMaxAge=" + occurredAtMaxAge
                + ", occurredAtFutureTolerance=" + occurredAtFutureTolerance + "]";
    }
}
