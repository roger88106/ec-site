package com.ecsite.module.member.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 会員 ID のキャッシュの設定
 *
 * <p>環境変数で上書きする（例：ECSITE_MEMBER_ID_CACHE_EXPIRE_AFTER_WRITE）。
 *
 * @param maximumSize      キャッシュに持つ件数の上限
 * @param expireAfterWrite 入れてからの有効期間
 */
@Validated
@ConfigurationProperties("ecsite.member.id-cache")
public record MemberIdCacheProperties(
        @DefaultValue("10000") @Min(1) long maximumSize,
        @DefaultValue("1h") @NotNull @DurationMin(millis = 1) Duration expireAfterWrite) {
}
