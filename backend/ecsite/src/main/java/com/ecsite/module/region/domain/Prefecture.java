package com.ecsite.module.region.domain;

import lombok.NonNull;

/**
 * 都道府県（docs/backend/modules/region.md「マスターデータ」）
 *
 * @param code 都道府県のコード（地方公共団体コードの先頭 2 桁）
 * @param name 都道府県名
 */
public record Prefecture(@NonNull String code, @NonNull String name) {
}
