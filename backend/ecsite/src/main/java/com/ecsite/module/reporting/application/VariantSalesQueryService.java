package com.ecsite.module.reporting.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.reporting.application.model.query.VariantSalesQuery;
import com.ecsite.module.reporting.application.model.view.VariantSales;
import com.ecsite.module.reporting.application.model.view.VariantSalesReport;
import com.ecsite.module.reporting.application.port.CatalogDirectory;
import com.ecsite.module.reporting.application.port.ReportQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * バリアントの売れ行き（docs/backend/modules/reporting.md「バリアントの売れ行き」）。<br />
 * 商品カタログのバリアントの一覧に、売れた数を合わせる。売れなかったバリアントも 0 として返す。
 */
@Service
@RequiredArgsConstructor
public class VariantSalesQueryService {

    /**
     * 写しを集計する問い合わせ
     */
    private final ReportQueryRepository queryRepository;

    /**
     * 商品カタログの腐敗防止層
     */
    private final CatalogDirectory catalogDirectory;

    /**
     * バリアントの売れ行きを求める。
     *
     * @param query 条件
     * @return バリアントごとの売れ行き
     * @throws BusinessException 商品がない（404）
     */
    @Transactional(readOnly = true)
    public VariantSalesReport variants(VariantSalesQuery query) {
        var product = catalogDirectory.findProduct(query.productId())
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
        var totals = queryRepository.variantSales(query.period(), query.basis(), query.productId(), query.excludeTax())
                .stream()
                .collect(Collectors.toMap(ReportQueryRepository.VariantTotal::variantId, Function.identity()));

        var rows = new ArrayList<VariantSales>();
        for (var variant : product.variants()) {
            var total = totals.remove(variant.variantId());
            rows.add(new VariantSales(variant.variantId(), variant.variantCode(),
                    variant.axisValues().stream().map(axis -> new VariantSales.AxisValue(axis.name(), axis.value())).toList(),
                    total == null ? 0 : total.quantity(), total == null ? Money.ZERO : total.amount()));
        }
        // 商品カタログにないバリアント（注文されたバリアントは削除できないので、通常はない）は、品番の写しで後ろに足す
        totals.values().forEach(total -> rows.add(new VariantSales(total.variantId(), total.variantCode(), List.of(),
                total.quantity(), total.amount())));
        // 安定な並べ替えなので、販売数量が同じなら商品カタログの並びの順のまま
        rows.sort(Comparator.comparingLong(VariantSales::quantity).reversed());
        return new VariantSalesReport(product.productId(), product.name(), rows, true);
    }
}
