package com.ecsite.module.order.application.checkout;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.application.OrderProperties;
import com.ecsite.module.order.application.model.CartSnapshot;
import com.ecsite.module.order.application.port.CatalogDirectory;
import com.ecsite.module.order.application.port.StockDirectory;
import com.ecsite.module.order.domain.quote.Quote;
import com.ecsite.module.order.domain.quote.QuoteItem;
import com.ecsite.module.order.domain.quote.Shipping;
import com.ecsite.module.order.domain.quote.VariantInfo;
import com.ecsite.module.order.domain.quote.port.TaxDirectory;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 見積もりのサービス
 *
 * <p>商品カタログ・在庫・税から必要な値を集め、ドメインの見積もりを組み立てる。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class QuoteService {

    /**
     * 商品カタログの腐敗防止層
     */
    private final CatalogDirectory catalogDirectory;

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
     * カートの品目を、指定した時点の価格・税率・販売可能数で見積もる。
     *
     * @param lines    品目（カートの並び）
     * @param at       価格と税率を判定する時点
     * @param holderId 販売可能数から除く引当の持ち主（自分の注文手続き）。なければ null
     * @return 見積もり。存在しなくなったバリアントは品目に含めず、注文できないものとする
     */
    public Quote quote(@NonNull List<CartSnapshot.Line> lines, @NonNull Instant at, @Nullable UUID holderId) {
        // バリアントの情報と販売可能数の取得
        var variantIds = lines.stream().map(CartSnapshot.Line::variantId).toList();
        var variants = variantIds.isEmpty() ? Map.<UUID, VariantInfo>of()
                : catalogDirectory.findVariants(variantIds);
        var sellable = variantIds.isEmpty() ? Map.<UUID, Integer>of()
                : stockDirectory.sellableQuantities(variantIds, holderId);

        // 税区分ごとの率の取得 ※同じ税区分の品目が多くても、税モジュールには 1 回だけ聞く
        var rates = new HashMap<UUID, Optional<BigDecimal>>();
        for (var variant : variants.values()) {
            if (!rates.containsKey(variant.taxCategoryId())) {
                rates.put(variant.taxCategoryId(), taxDirectory.rateAt(variant.taxCategoryId(), at));
            }
        }

        // 品目の組み立て
        var items = new ArrayList<QuoteItem>();
        boolean missingVariant = false;
        for (var line : lines) {
            var variant = variants.get(line.variantId());
            if (variant == null) {
                missingVariant = true;
                continue;
            }
            var rate = rates.getOrDefault(variant.taxCategoryId(), Optional.empty()).orElse(null);
            Money unitPrice = rate == null ? null : taxDirectory.priceInclTax(variant.priceExclTax(), rate);
            items.add(QuoteItem.of(variant, line.quantity(), sellable.getOrDefault(line.variantId(), 0), rate, unitPrice));
        }

        // 見積もり
        return Quote.of(items, missingVariant, shipping(at), taxDirectory);
    }

    /**
     * 時点の送料を返す。
     */
    private Shipping shipping(Instant at) {
        // 送料の税区分に有効な税率がなければ、設定の誤りなのでシステムのエラーにする
        var rate = taxDirectory.rateAt(properties.shippingTaxCategoryId(), at).orElseThrow(() -> SystemException.of(
                "送料の税区分に有効な税率がありません: " + properties.shippingTaxCategoryId(), null));
        return new Shipping(Money.of(properties.shippingFee()), rate);
    }
}
