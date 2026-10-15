package com.ecsite.module.reporting.application;

import com.ecsite.common.model.value.Money;
import jakarta.annotation.Nullable;

/**
 * 都道府県の売上
 *
 * @param prefectureCode 全国地方公共団体コードの上 2 桁。不明なら null
 * @param amount         売上額
 * @param orders         注文件数
 */
public record PrefectureSales(@Nullable String prefectureCode, Money amount, long orders) {
}
