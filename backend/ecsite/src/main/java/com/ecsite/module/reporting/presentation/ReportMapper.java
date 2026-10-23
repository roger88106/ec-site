package com.ecsite.module.reporting.presentation;

import com.ecsite.api.admin.model.AgeGroupSales;
import com.ecsite.api.admin.model.CustomerBreakdownReport;
import com.ecsite.api.admin.model.GenderSales;
import com.ecsite.api.admin.model.Granularity;
import com.ecsite.api.admin.model.OrderAxisValue;
import com.ecsite.api.admin.model.PrefectureSales;
import com.ecsite.api.admin.model.ReportBasis;
import com.ecsite.api.admin.model.ReportSettings;
import com.ecsite.api.admin.model.SalesTrendPoint;
import com.ecsite.api.admin.model.SalesTrendReport;
import com.ecsite.api.admin.model.VariantSales;
import com.ecsite.api.admin.model.VariantSalesReport;
import com.ecsite.module.reporting.application.model.view.ReportSettingsView;
import com.ecsite.module.reporting.domain.sales.AgeGroup;
import com.ecsite.module.reporting.domain.sales.Basis;
import com.ecsite.module.reporting.domain.sales.ReportGender;
import com.ecsite.module.reporting.domain.sales.TrendPoint;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * レポート API のレスポンスと、アプリケーション層のモデルの変換。<br />
 * レスポンスは集計の値だけで、会員を特定できる情報（会員 ID、氏名、住所など）を持たない。
 */
@Component
class ReportMapper {

    /**
     * レポートの設定のレスポンスに変換する。
     *
     * @param view レポートの設定
     * @return レポートの設定のレスポンス
     */
    ReportSettings toReportSettings(ReportSettingsView view) {
        return new ReportSettings()
                .fiscalYearStartMonth(view.fiscalYearStartMonth());
    }

    /**
     * 売上の数え方に変換する。省けば注文基準。
     *
     * @param basis 売上の数え方（省略可）
     * @return 売上の数え方
     */
    Basis toBasis(@Nullable ReportBasis basis) {
        if (basis == null) {
            return Basis.ORDER;
        }
        return switch (basis) {
            case ORDER -> Basis.ORDER;
            case SHIPMENT -> Basis.SHIPMENT;
        };
    }

    /**
     * 集計の粒度に変換する。
     *
     * @param granularity 集計の粒度
     * @return 集計の粒度
     */
    com.ecsite.module.reporting.domain.sales.Granularity toGranularity(Granularity granularity) {
        return switch (granularity) {
            case WEEK -> com.ecsite.module.reporting.domain.sales.Granularity.WEEK;
            case MONTH -> com.ecsite.module.reporting.domain.sales.Granularity.MONTH;
            case QUARTER -> com.ecsite.module.reporting.domain.sales.Granularity.QUARTER;
            case YEAR -> com.ecsite.module.reporting.domain.sales.Granularity.YEAR;
        };
    }

    /**
     * 売上の推移のレスポンスに変換する。
     *
     * @param report 売上の推移
     * @return 売上の推移のレスポンス
     */
    SalesTrendReport toSalesTrendReport(com.ecsite.module.reporting.application.model.view.SalesTrendReport report) {
        return new SalesTrendReport()
                .points(report.points().stream().map(ReportMapper::toPoint).toList())
                .shippingExcluded(report.shippingExcluded());
    }

    /**
     * バリアントの売れ行きのレスポンスに変換する。
     *
     * @param report バリアントの売れ行き
     * @return バリアントの売れ行きのレスポンス
     */
    VariantSalesReport toVariantSalesReport(com.ecsite.module.reporting.application.model.view.VariantSalesReport report) {
        var variants = report.variants().stream()
                .map(variant -> new VariantSales()
                        .variantId(variant.variantId())
                        .variantCode(variant.variantCode())
                        .axisValues(variant.axisValues().stream()
                                .map(axis -> new OrderAxisValue().name(axis.name()).value(axis.value()))
                                .toList())
                        .quantity(variant.quantity())
                        .amount(variant.amount().yen()))
                .toList();
        return new VariantSalesReport()
                .productId(report.productId())
                .productName(report.productName())
                .variants(variants)
                .shippingExcluded(report.shippingExcluded());
    }

    /**
     * 地域・年齢層・性別のレスポンスに変換する。
     *
     * @param report 地域・年齢層・性別
     * @return 地域・年齢層・性別のレスポンス
     */
    CustomerBreakdownReport toCustomerBreakdownReport(com.ecsite.module.reporting.application.model.view.CustomerBreakdownReport report) {
        var prefectures = report.prefectures().stream()
                .map(sales -> new PrefectureSales()
                        .prefectureCode(sales.prefectureCode())
                        .prefectureName(sales.prefectureName())
                        .amount(sales.amount().yen())
                        .orders(sales.orders()))
                .toList();
        var ageGroups = report.ageGroups().stream()
                .map(sales -> new AgeGroupSales()
                        .ageGroup(toAgeGroup(sales.ageGroup()))
                        .amount(sales.amount().yen())
                        .orders(sales.orders()))
                .toList();
        var genders = report.genders().stream()
                .map(sales -> new GenderSales()
                        .gender(toGender(sales.gender()))
                        .amount(sales.amount().yen())
                        .orders(sales.orders()))
                .toList();
        return new CustomerBreakdownReport()
                .prefectures(prefectures)
                .ageGroups(ageGroups)
                .genders(genders)
                .shippingExcluded(report.shippingExcluded());
    }

    /**
     * 区切りの売上に変換する。
     */
    private static SalesTrendPoint toPoint(TrendPoint point) {
        var bucket = point.bucket();
        return new SalesTrendPoint()
                .start(bucket.start())
                .end(bucket.end())
                .fiscalYear(bucket.fiscalYear())
                .quarter(bucket.quarter())
                .amount(point.amount().yen())
                .quantity(point.quantity());
    }

    /**
     * 年齢層。
     */
    private static AgeGroupSales.AgeGroupEnum toAgeGroup(AgeGroup ageGroup) {
        return switch (ageGroup) {
            case TEENS_OR_YOUNGER -> AgeGroupSales.AgeGroupEnum.TEENS_OR_YOUNGER;
            case TWENTIES -> AgeGroupSales.AgeGroupEnum.TWENTIES;
            case THIRTIES -> AgeGroupSales.AgeGroupEnum.THIRTIES;
            case FORTIES -> AgeGroupSales.AgeGroupEnum.FORTIES;
            case FIFTIES -> AgeGroupSales.AgeGroupEnum.FIFTIES;
            case SIXTIES_OR_OLDER -> AgeGroupSales.AgeGroupEnum.SIXTIES_OR_OLDER;
            case UNKNOWN -> AgeGroupSales.AgeGroupEnum.UNKNOWN;
        };
    }

    /**
     * 性別の区分。
     */
    private static GenderSales.GenderEnum toGender(ReportGender gender) {
        return switch (gender) {
            case MALE -> GenderSales.GenderEnum.MALE;
            case FEMALE -> GenderSales.GenderEnum.FEMALE;
            case OTHER -> GenderSales.GenderEnum.OTHER;
            case UNKNOWN -> GenderSales.GenderEnum.UNKNOWN;
        };
    }
}
