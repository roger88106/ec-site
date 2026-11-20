package com.ecsite.module.region.application;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 地域のマスターデータのキャッシュの設定
 *
 * <p>環境変数で上書きする（例：ECSITE_REGION_CACHE_EXPIRE_AFTER_WRITE）。
 *
 * @param expireAfterWrite 読み込んでからの有効期間
 */
@Validated
@ConfigurationProperties("ecsite.region.cache")
public record RegionCacheProperties(
        @DefaultValue("24h") @NotNull @DurationMin(millis = 1) Duration expireAfterWrite) {
}
