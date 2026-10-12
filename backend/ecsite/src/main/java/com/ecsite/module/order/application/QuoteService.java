package com.ecsite.module.order.application;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.application.port.CartDirectory;
import com.ecsite.module.order.application.port.ProductDirectory;
import com.ecsite.module.order.application.port.StockDirectory;
import com.ecsite.module.order.domain.quote.Quote;
import com.ecsite.module.order.domain.quote.QuoteItem;
import com.ecsite.module.order.domain.quote.Shipping;
import com.ecsite.module.order.domain.quote.VariantInfo;
import com.ecsite.module.order.domain.quote.port.TaxDirectory;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 見積もりのサービス（docs/modules/order.md「見積もり」）。<br />
 * 商品カタログ・在庫・税から必要な値を集め、ドメインの見積もりを組み立てる。
 */
@Service
@RequiredArgsConstructor
public class QuoteService {

    /**
     * 商品カタログの腐敗防止層
     */
    private final ProductDirectory productDirectory;

    /**
     * 在庫の腐敗防止層
     */
    private final StockDirectory stockDirectory;

    /**
     * 税の腐敗防止層
     */
    private final TaxDirectory taxDirectory;

    /**
     * 注文の設定（送料）
     */
    private final OrderProperties properties;

    /**
     * カートの品目を、指定した時点の価格・税率・販売可能数で見積もる（BR-7、8、AC-80）。
     *
     * @param lines    品目（カートの並び）
     * @param at       価格と税率を判定する時点
     * @param holderId 販売可能数から除く引当の持ち主（自分の注文手続き）。なければ null
     * @return 見積もり。存在しなくなったバリアントは品目に含めず、注文できないものとする
     */
    public Quote quote(List<CartDirectory.Line> lines, Instant at, @Nullable UUID holderId) {
        var variantIds = lines.stream().map(CartDirectory.Line::variantId).toList();
        var variants = variantIds.isEmpty() ? Map.<UUID, VariantInfo>of()
                : productDirectory.findVariants(variantIds);
        var sellable = variantIds.isEmpty() ? Map.<UUID, Integer>of()
                : stockDirectory.sellableQuantities(variantIds, holderId);
        // 税区分ごとの率（同じ税区分の品目が多くても、税モジュールには 1 回だけ聞く）
        var rates = new HashMap<UUID, Optional<BigDecimal>>();

        var items = new ArrayList<QuoteItem>();
        boolean missingVariant = false;
        for (var line : lines) {
            var variant = variants.get(line.variantId());
            if (variant == null) {
                missingVariant = true;
                continue;
            }
            var rate = rates.computeIfAbsent(variant.taxCategoryId(), id -> taxDirectory.rateAt(id, at)).orElse(null);
            Money unitPrice = rate == null ? null : taxDirectory.priceInclTax(variant.priceExclTax(), rate);
            items.add(QuoteItem.of(variant, line.quantity(), sellable.getOrDefault(line.variantId(), 0), rate, unitPrice));
        }
        return Quote.of(items, missingVariant, shipping(at), taxDirectory);
    }

    /**
     * 時点の送料を返す。送料の税区分に有効な税率がなければ、設定の誤りなのでシステムのエラーにする。
     */
    private Shipping shipping(Instant at) {
        var rate = taxDirectory.rateAt(properties.shippingTaxCategoryId(), at).orElseThrow(() -> SystemException.of(
                "送料の税区分に有効な税率がありません: " + properties.shippingTaxCategoryId(), null));
        return new Shipping(Money.of(properties.shippingFee()), rate);
    }
}
