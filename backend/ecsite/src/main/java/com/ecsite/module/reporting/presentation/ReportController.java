package com.ecsite.module.reporting.presentation;

import com.ecsite.api.admin.ReportApi;
import com.ecsite.api.admin.model.CustomerBreakdownReport;
import com.ecsite.api.admin.model.Granularity;
import com.ecsite.api.admin.model.ReportBasis;
import com.ecsite.api.admin.model.ReportSettings;
import com.ecsite.api.admin.model.SalesTrendReport;
import com.ecsite.api.admin.model.VariantSalesReport;
import com.ecsite.module.reporting.application.CustomerBreakdownQueryService;
import com.ecsite.module.reporting.application.ReportingProperties;
import com.ecsite.module.reporting.application.SalesTrendQueryService;
import com.ecsite.module.reporting.application.VariantSalesQueryService;
import com.ecsite.module.reporting.application.model.query.CustomerBreakdownQuery;
import com.ecsite.module.reporting.application.model.query.SalesTrendQuery;
import com.ecsite.module.reporting.application.model.query.VariantSalesQuery;
import com.ecsite.module.reporting.domain.ReportPeriod;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * レポートのコントローラー（B 側。admin.yaml の tag: report）。<br />
 * 権限は admin.yaml の {@code x-required-permission} で判定する（Controller には書かない）。
 */
@RestController
@RequiredArgsConstructor
class ReportController implements ReportApi {

    /**
     * 売上の推移のクエリサービス
     */
    private final SalesTrendQueryService salesTrendService;

    /**
     * バリエーションの売れ行きのクエリサービス
     */
    private final VariantSalesQueryService variantSalesService;

    /**
     * 地域・年齢層・性別のクエリサービス
     */
    private final CustomerBreakdownQueryService customerBreakdownService;

    /**
     * レポートマッパー
     */
    private final ReportMapper mapper;

    /**
     * レポートの設定
     */
    private final ReportingProperties properties;

// ============================== GET ==============================

    /**
     * 売上の推移
     *
     * @param from            期間の始まり
     * @param to              期間の終わり
     * @param granularity     集計の粒度
     * @param basis           売上の数え方（省けば注文基準）
     * @param productId       商品 ID（省けば全商品）
     * @param excludeTax      消費税を除くか
     * @param excludeShipping 送料を除くか
     * @return Http 200 : {@link SalesTrendReport}
     */
    @Override
    public ResponseEntity<SalesTrendReport> getSalesTrend(LocalDate from, LocalDate to, Granularity granularity,
                                                          @Nullable ReportBasis basis, @Nullable UUID productId,
                                                          Boolean excludeTax, Boolean excludeShipping) {
        var query = new SalesTrendQuery(ReportPeriod.of(from, to),
                mapper.toGranularity(granularity), mapper.toBasis(basis), productId, excludeTax, excludeShipping);
        return ResponseEntity.ok(mapper.toSalesTrendReport(salesTrendService.trend(query)));
    }

    /**
     * バリエーションの売れ行き
     *
     * @param productId  商品 ID
     * @param from       期間の始まり
     * @param to         期間の終わり
     * @param basis      売上の数え方（省けば注文基準）
     * @param excludeTax 消費税を除くか
     * @return Http 200 : {@link VariantSalesReport}
     */
    @Override
    public ResponseEntity<VariantSalesReport> getVariantSales(UUID productId, LocalDate from, LocalDate to,
                                                              @Nullable ReportBasis basis, Boolean excludeTax) {
        var query = new VariantSalesQuery(productId, ReportPeriod.of(from, to), mapper.toBasis(basis), excludeTax);
        return ResponseEntity.ok(mapper.toVariantSalesReport(variantSalesService.variants(query)));
    }

    /**
     * 地域・年齢層・性別
     *
     * @param from            期間の始まり
     * @param to              期間の終わり
     * @param basis           売上の数え方（省けば注文基準）
     * @param productId       商品 ID（省けば全商品）
     * @param excludeTax      消費税を除くか
     * @param excludeShipping 送料を除くか
     * @return Http 200 : {@link CustomerBreakdownReport}
     */
    @Override
    public ResponseEntity<CustomerBreakdownReport> getCustomerBreakdown(LocalDate from, LocalDate to,
                                                                        @Nullable ReportBasis basis,
                                                                        @Nullable UUID productId, Boolean excludeTax,
                                                                        Boolean excludeShipping) {
        var query = new CustomerBreakdownQuery(ReportPeriod.of(from, to), mapper.toBasis(basis), productId, excludeTax,
                excludeShipping);
        return ResponseEntity.ok(mapper.toCustomerBreakdownReport(customerBreakdownService.breakdown(query)));
    }

    /**
     * レポートの設定
     *
     * @return Http 200 : {@link ReportSettings}
     */
    @Override
    public ResponseEntity<ReportSettings> getReportSettings() {
        return ResponseEntity.ok(new ReportSettings().fiscalYearStartMonth(properties.fiscalYearStartMonth()));
    }
}
