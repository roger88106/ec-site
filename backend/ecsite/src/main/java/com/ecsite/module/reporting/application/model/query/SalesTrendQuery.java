package com.ecsite.module.reporting.application.model.query;

import com.ecsite.module.reporting.domain.Basis;
import com.ecsite.module.reporting.domain.Granularity;
import com.ecsite.module.reporting.domain.ReportPeriod;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * 売上の推移の条件
 *
 * @param period          期間
 * @param granularity     粒度
 * @param basis           売上の数え方
 * @param productId       商品 ID。全商品なら null
 * @param excludeTax      消費税を除くか
 * @param excludeShipping 送料を除くか（商品を選んだときは、指定によらず除く）
 */
public record SalesTrendQuery(ReportPeriod period, Granularity granularity, Basis basis, @Nullable UUID productId,
                              boolean excludeTax, boolean excludeShipping) {
}
