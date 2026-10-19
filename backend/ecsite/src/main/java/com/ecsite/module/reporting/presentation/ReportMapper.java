package com.ecsite.module.reporting.presentation;

import com.ecsite.api.admin.model.AgeGroupSales;
import com.ecsite.api.admin.model.CustomerBreakdownReport;
import com.ecsite.api.admin.model.GenderSales;
import com.ecsite.api.admin.model.Granularity;
import com.ecsite.api.admin.model.OrderAxisValue;
import com.ecsite.api.admin.model.PrefectureSales;
import com.ecsite.api.admin.model.ReportBasis;
import com.ecsite.api.admin.model.SalesTrendPoint;
import com.ecsite.api.admin.model.SalesTrendReport;
import com.ecsite.api.admin.model.VariantSales;
import com.ecsite.api.admin.model.VariantSalesReport;
import com.ecsite.module.reporting.domain.Basis;
import com.ecsite.module.reporting.domain.TrendPoint;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Component;

/**
 * レポート API のレスポンスと、アプリケーション層のモデルの変換。<br />
 * レスポンスは集計の値だけで、会員を特定できる情報（会員 ID、氏名、住所など）を持たない。
 */
@Component
class ReportMapper {

    /**
     * 売上の数え方に変換する。省けば注文基準。
     *
     * @param basis 売上の数え方（省略可）
     * @return 売上の数え方
     */
    Basis toBasis(@Nullable ReportBasis basis) {
        return basis == null ? Basis.ORDER : Basis.valueOf(basis.getValue());
    }

    /**
     * 集計の粒度に変換する。
     *
     * @param granularity 集計の粒度
     * @return 集計の粒度
     */
    com.ecsite.module.reporting.domain.Granularity toGranularity(Granularity granularity) {
        return com.ecsite.module.reporting.domain.Granularity.valueOf(granularity.getValue());
    }

    /**
     * 売上の推移のレスポンスに変換する。
     *
     * @param report 売上の推移
     * @return 売上の推移のレスポンス
     */
    SalesTrendReport toSalesTrendReport(com.ecsite.module.reporting.application.model.view.SalesTrendReport report) {
        return new SalesTrendReport(report.points().stream().map(ReportMapper::toPoint).toList(), report.shippingExcluded());
    }

    /**
     * バリエーションの売れ行きのレスポンスに変換する。
     *
     * @param report バリエーションの売れ行き
     * @return バリエーションの売れ行きのレスポンス
     */
    VariantSalesReport toVariantSalesReport(com.ecsite.module.reporting.application.model.view.VariantSalesReport report) {
        var variants = report.variants().stream()
                .map(variant -> new VariantSales(
                        variant.variantId(),
                        variant.variantCode(),
                        variant.axisValues().stream().map(axis -> new OrderAxisValue(axis.name(), axis.value())).toList(),
                        variant.quantity(),
                        variant.amount().yen()))
                .toList();
        return new VariantSalesReport(report.productId(), report.productName(), variants, report.shippingExcluded());
    }

    /**
     * 地域・年齢層・性別のレスポンスに変換する。
     *
     * @param report 地域・年齢層・性別
     * @return 地域・年齢層・性別のレスポンス
     */
    CustomerBreakdownReport toCustomerBreakdownReport(com.ecsite.module.reporting.application.model.view.CustomerBreakdownReport report) {
        var prefectures = report.prefectures().stream()
                .map(sales -> new PrefectureSales(sales.amount().yen(), sales.orders()).prefectureCode(sales.prefectureCode()))
                .toList();
        var ageGroups = report.ageGroups().stream()
                .map(sales -> new AgeGroupSales(AgeGroupSales.AgeGroupEnum.fromValue(sales.ageGroup().name()),
                        sales.amount().yen(), sales.orders()))
                .toList();
        var genders = report.genders().stream()
                .map(sales -> new GenderSales(GenderSales.GenderEnum.fromValue(sales.gender().name()),
                        sales.amount().yen(), sales.orders()))
                .toList();
        return new CustomerBreakdownReport(prefectures, ageGroups, genders, report.shippingExcluded());
    }

    /**
     * 区切りの売上に変換する。
     */
    private static SalesTrendPoint toPoint(TrendPoint point) {
        var bucket = point.bucket();
        return new SalesTrendPoint(bucket.start(), bucket.end(), point.amount().yen(), point.quantity())
                .fiscalYear(bucket.fiscalYear())
                .quarter(bucket.quarter());
    }
}
