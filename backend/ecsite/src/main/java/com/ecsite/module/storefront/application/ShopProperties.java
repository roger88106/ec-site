package com.ecsite.module.storefront.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 店舗の情報の設定。環境変数で与える（例：ECSITE_SHOP_CONTACT_EMAIL）。
 *
 * @param contactEmail 問い合わせ先のメールアドレス。キャンセルの申請が却下された注文に示す
 */
@Validated
@ConfigurationProperties("ecsite.storefront.shop")
public record ShopProperties(@NotBlank @Email String contactEmail) {
}
