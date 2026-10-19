package com.ecsite.module.reporting.application.model.view;

import com.ecsite.common.model.value.Money;
import jakarta.annotation.Nullable;

/**
 * 都道府県の売上
 *
 * @param prefectureCode 全国地方公共団体コードの上 2 桁。不明なら null
 * @param prefectureName 都道府県名（地域のマスターデータの今の名前）。不明、またはマスターデータにないコードなら null
 * @param amount         売上額
 * @param orders         注文件数
 */
public record PrefectureSales(@Nullable String prefectureCode, @Nullable String prefectureName, Money amount, long orders) {
}
