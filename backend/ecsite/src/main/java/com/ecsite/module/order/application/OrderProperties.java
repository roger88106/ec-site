package com.ecsite.module.order.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.UUID;

/**
 * 注文の設定（plan 002 第 4 節）。環境変数で上書きする（例：ECSITE_ORDER_SHIPPING_TAX_CATEGORY_ID）。
 *
 * @param shippingFee           送料（税込、円。BR-6）
 * @param shippingTaxCategoryId 送料に適用する税区分の ID（標準税率）。税率は注文日時のものを税モジュールから取る
 * @param reservationTtl        引当の期限（注文手続きに進んでからの時間。AC-82）
 */
@Validated
@ConfigurationProperties("ecsite.order")
public record OrderProperties(
        @DefaultValue("1000") @Min(0) long shippingFee,
        @NotNull UUID shippingTaxCategoryId,
        @DefaultValue("15m") Duration reservationTtl) {
}
