package com.ecsite.module.reporting.application.model.query;

import com.ecsite.module.reporting.domain.sales.Basis;
import com.ecsite.module.reporting.domain.sales.Granularity;
import com.ecsite.module.reporting.domain.sales.ReportPeriod;
import lombok.NonNull;
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
public record SalesTrendQuery(@NonNull ReportPeriod period, @NonNull Granularity granularity, @NonNull Basis basis, @Nullable UUID productId,
                              boolean excludeTax, boolean excludeShipping) {
}
