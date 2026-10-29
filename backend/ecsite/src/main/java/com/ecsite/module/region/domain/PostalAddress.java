package com.ecsite.module.region.domain;

import lombok.NonNull;

/**
 * 郵便番号の住所（docs/03-機能仕様/region.md「マスターデータ」）
 *
 * @param postalCode   郵便番号（数字 7 桁）
 * @param prefecture   都道府県
 * @param municipality 市区町村
 * @param townName     町域名（注記を除く。注記だけなら空）
 */
public record PostalAddress(@NonNull String postalCode, @NonNull Prefecture prefecture, @NonNull Municipality municipality,
                            @NonNull String townName) {
}
