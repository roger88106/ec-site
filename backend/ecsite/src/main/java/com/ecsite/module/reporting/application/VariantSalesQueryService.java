package com.ecsite.module.reporting.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.reporting.application.model.VariantTotal;
import com.ecsite.module.reporting.application.model.query.VariantSalesQuery;
import com.ecsite.module.reporting.application.model.view.VariantSales;
import com.ecsite.module.reporting.application.model.view.VariantSalesReport;
import com.ecsite.module.reporting.application.port.CatalogDirectory;
import com.ecsite.module.reporting.application.port.ReportQueryRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * バリアントの売れ行きのクエリサービス
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
     * バリアントの売れ行きを求める。売れなかったバリアントも 0 として返す。
     *
     * @param query 条件
     * @return バリアントごとの売れ行き
     * @throws BusinessException 商品がない（{@code NOT_FOUND}）
     */
    @Transactional(readOnly = true)
    public VariantSalesReport variants(@NonNull VariantSalesQuery query) {
        // 商品カタログのバリアントの一覧と、売れたバリアントの集計
        var product = catalogDirectory.findProduct(query.productId())
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
        var totals = queryRepository.variantSales(query.period(), query.basis(), query.productId(), query.excludeTax())
                .stream()
                .collect(Collectors.toMap(VariantTotal::variantId, Function.identity()));

        // 商品カタログのバリアントごとに、売れた数を合わせる
        var rows = new ArrayList<VariantSales>();
        for (var variant : product.variants()) {
            var total = totals.remove(variant.variantId());
            rows.add(new VariantSales(variant.variantId(), variant.variantCode(),
                    variant.axisValues().stream().map(axis -> new VariantSales.AxisValue(axis.name(), axis.value())).toList(),
                    total == null ? 0 : total.quantity(), total == null ? Money.ZERO : total.amount()));
        }
        // 商品カタログにないバリアント（注文されたバリアントは削除できないので、通常はない）は、品番の写しで後ろに追加する
        totals.values().forEach(total -> rows.add(new VariantSales(total.variantId(), total.variantCode(), List.of(),
                total.quantity(), total.amount())));
        // 安定な並べ替えなので、販売数量が同じなら商品カタログの並びの順のまま
        rows.sort(Comparator.comparingLong(VariantSales::quantity).reversed());
        // バリアントの売れ行きは、明細だけを数えるので、いつも送料を除いている
        return new VariantSalesReport(product.productId(), product.name(), rows, true);
    }
}
