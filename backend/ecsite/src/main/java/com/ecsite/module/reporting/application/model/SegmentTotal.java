package com.ecsite.module.reporting.application.model;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.reporting.domain.snapshot.ReportBuyerGender;
import org.jspecify.annotations.Nullable;

/**
 * 都道府県・年齢・性別の組の売上の集計
 *
 * @param prefectureCode 都道府県のコード。不明なら null
 * @param age            注文時の年齢。不明なら null
 * @param gender         注文時の性別。不明なら null
 * @param amount         売上額
 * @param orders         注文件数
 */
public record SegmentTotal(@Nullable String prefectureCode, @Nullable Integer age, @Nullable ReportBuyerGender gender,
                           Money amount, long orders) {
}
