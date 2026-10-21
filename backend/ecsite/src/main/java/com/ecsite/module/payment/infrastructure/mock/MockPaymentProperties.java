package com.ecsite.module.payment.infrastructure.mock;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * mock の決済サービスの設定。決済の mock フラグ（{@code ecsite.payment.mock.enabled}）を立てたときだけ使う。
 *
 * @param baseUrl mock の支払画面を出すバックエンドの URL（ブラウザーから開ける URL）
 */
@Validated
@ConfigurationProperties("ecsite.payment.mock")
public record MockPaymentProperties(@NotBlank String baseUrl) {
}
