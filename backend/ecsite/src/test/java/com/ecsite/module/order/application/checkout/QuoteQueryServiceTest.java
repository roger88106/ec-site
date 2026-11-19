package com.ecsite.module.order.application.checkout;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.application.OrderProperties;
import com.ecsite.module.order.application.model.CartSnapshot;
import com.ecsite.module.order.application.port.CatalogDirectory;
import com.ecsite.module.order.application.port.StockDirectory;
import com.ecsite.module.order.domain.quote.QuoteItem;
import com.ecsite.module.order.domain.quote.VariantInfo;
import com.ecsite.module.order.domain.quote.port.TaxDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 見積もりのサービスの単体テスト
 *
 * <p>存在しなくなったバリアントの扱いだけを確認する。商品カタログの削除は表示されたことのない商品に限るので、API の統合テストでは
 * カートに入った商品のバリアントを消せない。そのため、商品カタログの腐敗防止層を差し替えて確認する。金額の求め方は
 * {@code QuoteTest} が確認する。
 */
class QuoteQueryServiceTest {

    // ============================== 定数 ==============================

    /**
     * 見積もりの時点
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 税率（%）
     */
    private static final BigDecimal RATE = BigDecimal.TEN;

    /**
     * 残っているバリアントの品番
     */
    private static final String REMAINING_CODE = "P-1-1";

    // ============================== Bean ==============================

    /**
     * 商品カタログの腐敗防止層（残っているバリアントだけを返す）
     */
    private final CatalogDirectory catalogDirectory = mock(CatalogDirectory.class);

    /**
     * 在庫の腐敗防止層
     */
    private final StockDirectory stockDirectory = mock(StockDirectory.class);

    /**
     * 税の腐敗防止層
     */
    private final TaxDirectory taxDirectory = mock(TaxDirectory.class);

    /**
     * 見積もりのサービス
     */
    private QuoteQueryService service;

    /**
     * 残っているバリアントの ID
     */
    private final UUID remainingId = UUID.randomUUID();

    /**
     * 存在しなくなったバリアントの ID
     */
    private final UUID missingId = UUID.randomUUID();

    /**
     * 残っているバリアントだけを返す商品カタログと、全て在庫のある在庫と、税率のある税で、サービスを作る。
     */
    @BeforeEach
    void setUp() {
        var taxCategoryId = UUID.randomUUID();
        var remaining = new VariantInfo(remainingId, UUID.randomUUID(), "P-1", REMAINING_CODE, "商品", List.of(),
                Money.of(1000), taxCategoryId, null, true, false);
        when(catalogDirectory.findVariants(anyCollection())).thenReturn(Map.of(remainingId, remaining));
        when(stockDirectory.sellableQuantities(anyCollection(), any())).thenReturn(Map.of(remainingId, 10, missingId, 10));
        when(taxDirectory.ratesAt(anyCollection(), any())).thenReturn(Map.of(taxCategoryId, RATE));
        when(taxDirectory.rateAt(any(), any())).thenReturn(Optional.of(RATE));
        when(taxDirectory.priceInclTax(any(), any())).thenReturn(Money.of(1100));
        var properties = new OrderProperties(1000, UUID.randomUUID(), Duration.ofMinutes(15), 3);
        service = new QuoteQueryService(catalogDirectory, stockDirectory, taxDirectory, properties);
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("存在しなくなったバリアントは、見積もりの品目に含めない")
    void excludesMissingVariant() {
        // 準備：存在しなくなったバリアントと、残っているバリアントの順のカート
        var lines = List.of(new CartSnapshot.Line(missingId, 1), new CartSnapshot.Line(remainingId, 2));

        // 操作：見積もる
        var quote = service.quote(lines, NOW, null);

        // 確認：品目は残っているバリアントだけ
        assertThat(quote.items()).extracting(item -> item.variant().variantCode()).containsExactly(REMAINING_CODE);
    }

    @Test
    @DisplayName("存在しなくなったバリアントがあれば、残りの品目が全て購入できても注文できない")
    void isNotOrderableWithMissingVariant() {
        // 準備：存在しなくなったバリアントと、残っているバリアントの順のカート
        var lines = List.of(new CartSnapshot.Line(missingId, 1), new CartSnapshot.Line(remainingId, 2));

        // 操作：見積もる
        var quote = service.quote(lines, NOW, null);

        // 確認（前提）：残っているバリアントの品目があり、購入できる
        assertThat(quote.items()).extracting(item -> item.variant().variantCode()).containsExactly(REMAINING_CODE);
        assertThat(quote.items()).allMatch(QuoteItem::purchasable);

        // 確認：注文できない
        assertThat(quote.orderable()).isFalse();
    }

    @Test
    @DisplayName("税区分が複数あり、同じ税区分の品目が複数あっても、税率は税区分を重複なく集めて 1 回だけ聞く")
    void asksRatesOnceForDistinctTaxCategories() {
        // 準備：税区分 2 つに、同じ税区分の品目が 2 つあるカート
        var standardId = UUID.randomUUID();
        var reducedId = UUID.randomUUID();
        var firstId = UUID.randomUUID();
        var secondId = UUID.randomUUID();
        var thirdId = UUID.randomUUID();
        var first = variant(firstId, standardId);
        var second = variant(secondId, standardId);
        var third = variant(thirdId, reducedId);
        when(catalogDirectory.findVariants(anyCollection())).thenReturn(Map.of(firstId, first, secondId, second, thirdId, third));
        when(taxDirectory.ratesAt(anyCollection(), any())).thenReturn(Map.of(standardId, RATE, reducedId, BigDecimal.valueOf(8)));
        var lines = List.of(new CartSnapshot.Line(firstId, 1), new CartSnapshot.Line(secondId, 1), new CartSnapshot.Line(thirdId, 1));

        // 操作：見積もる
        service.quote(lines, NOW, null);

        // 確認：税率は、重複のない税区分で 1 回だけ聞く（送料の税率は 1 件の取得）
        var captor = ArgumentCaptor.<Collection<UUID>>captor();
        verify(taxDirectory, times(1)).ratesAt(captor.capture(), eq(NOW));
        assertThat(captor.getValue()).containsExactlyInAnyOrder(standardId, reducedId);
    }

    /**
     * 指定した税区分のバリアントを作る。
     */
    private VariantInfo variant(UUID variantId, UUID taxCategoryId) {
        return new VariantInfo(variantId, UUID.randomUUID(), "P-" + variantId, "C-" + variantId, "商品", List.of(),
                Money.of(1000), taxCategoryId, null, true, false);
    }
}
