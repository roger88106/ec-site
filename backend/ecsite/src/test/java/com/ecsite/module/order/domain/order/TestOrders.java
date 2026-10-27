package com.ecsite.module.order.domain.order;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.domain.order.vo.OrderNumber;
import com.ecsite.module.order.domain.order.vo.ShippingAddress;
import com.ecsite.module.order.domain.quote.Amounts;
import com.ecsite.module.order.domain.quote.Quote;
import com.ecsite.module.order.domain.quote.QuoteItem;
import com.ecsite.module.order.domain.quote.TaxAmount;
import com.ecsite.module.order.domain.quote.VariantInfo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 注文のテストで使う、状態ごとの注文の作り方
 */
public final class TestOrders {

    private TestOrders() {
    }

    /**
     * 「支払待ち」の注文を作る（成立のイベントは取り出し済み）
     *
     * @param orderedAt 注文の日時
     * @return 支払待ちの注文
     */
    public static Order pending(Instant orderedAt) {
        var variant = new VariantInfo(UUID.randomUUID(), UUID.randomUUID(), "P-1", "P-1-1", "商品", List.of(),
                Money.of(1000), UUID.randomUUID(), null, true, false);
        var amounts = new Amounts(Money.of(1100), Money.of(1000), Money.of(2100),
                List.of(new TaxAmount(BigDecimal.TEN, Money.of(2100), Money.of(190))));
        var quote = new Quote(List.of(QuoteItem.of(variant, 1, 10, BigDecimal.TEN, Money.of(1100))), amounts, true);
        var order = Order.place(UUID.randomUUID(), OrderNumber.of("20261008-000001"), UUID.randomUUID(), UUID.randomUUID(),
                orderedAt, quote, "taro@example.com",
                ShippingAddress.of("山田 太郎", "1000001", TestAreas.CHIYODA, "千代田 1-1", "0312345678"), BuyerProfile.UNKNOWN);
        order.pullEvents();
        return order;
    }
}
