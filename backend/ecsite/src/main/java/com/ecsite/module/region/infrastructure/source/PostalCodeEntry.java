package com.ecsite.module.region.infrastructure.source;

import lombok.NonNull;

/**
 * 郵便番号の表の 1 行（docs/backend/modules/region.md「マスターデータ」）
 *
 * @param postalCode       郵便番号（数字 7 桁）
 * @param municipalityCode 市区町村のコード
 * @param townName         町域名（注記を除いたもの。注記だけなら空）
 * @param displayOrder     同じ郵便番号の中の並び順（1 から）
 */
public record PostalCodeEntry(@NonNull String postalCode, @NonNull String municipalityCode, @NonNull String townName,
                              int displayOrder) {
}
