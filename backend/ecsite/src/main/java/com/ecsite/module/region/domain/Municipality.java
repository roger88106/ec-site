package com.ecsite.module.region.domain;

import lombok.NonNull;

/**
 * 市区町村
 *
 * <p>市・町・村と東京都の特別区。政令指定都市は区を 1 つの市区町村とし、市そのものは市区町村に含めない。
 *
 * @param code           市区町村のコード（地方公共団体コードの 5 桁。検査数字を除く）
 * @param prefectureCode 都道府県のコード
 * @param name           市区町村名（郡を含む町村は郡を含む）
 */
public record Municipality(@NonNull String code, @NonNull String prefectureCode, @NonNull String name) {
}
