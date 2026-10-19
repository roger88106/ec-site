package com.ecsite.module.order.domain.order;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.domain.order.vo.BuyerProfile;
import com.ecsite.module.order.domain.order.vo.OrderNumber;
import com.ecsite.module.order.domain.order.vo.ShippingAddress;
import com.ecsite.module.order.domain.quote.Amounts;
import com.ecsite.module.order.domain.quote.AxisValue;
import com.ecsite.module.order.domain.quote.Quote;
import com.ecsite.module.order.domain.quote.QuoteItem;
import com.ecsite.module.order.domain.quote.TaxAmount;
import com.ecsite.module.order.domain.quote.VariantInfo;
import com.ecsite.module.order.event.OrderStatusChanged;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 注文の単体テスト（T2307、AC-86、90、92、BR-9、10）
 */
class OrderTest {

    /**
     * 注文日時：2026-10-08 00:30（日本時間）
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    private static final ShippingAddress ADDRESS =
            ShippingAddress.of("山田 太郎", "100-0001", TestAreas.CHIYODA, "千代田 1-1", "03-1234-5678");

    @Test
    @DisplayName("AC-143、BR-14 成立した注文は「支払待ち」で、注文成立のイベントが登録される")
    void placesPendingPaymentOrder() {
        var order = place(quote());

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.pullEvents()).singleElement().isInstanceOfSatisfying(OrderStatusChanged.class, event -> {
            assertThat(event.orderId()).isEqualTo(order.id());
            assertThat(event.from()).isNull();
            assertThat(event.to()).isEqualTo(OrderStatusChanged.Status.PENDING_PAYMENT);
        });
    }

    @Test
    @DisplayName("AC-92 明細は注文時点の品番・商品名・軸の値・税抜単価・税率・税込単価の写しと数量・小計を持つ")
    void copiesLinesFromQuote() {
        var quote = quote();

        var order = place(quote);

        assertThat(order.lines()).hasSize(1);
        var line = order.lines().getFirst();
        var variant = quote.items().getFirst().variant();
        assertThat(line.lineNo()).isEqualTo(1);
        assertThat(line.variantId()).isEqualTo(variant.variantId());
        assertThat(line.productCode()).isEqualTo("TS-001");
        assertThat(line.variantCode()).isEqualTo("TS-001-WM");
        assertThat(line.productName()).isEqualTo("T シャツ");
        assertThat(line.axisValues()).containsExactly(new AxisValue("色", "白"), new AxisValue("サイズ", "M"));
        assertThat(line.unitPriceExclTax()).isEqualTo(Money.of(2700));
        assertThat(line.ratePercent()).isEqualByComparingTo("10");
        assertThat(line.unitPriceInclTax()).isEqualTo(Money.of(2970));
        assertThat(line.quantity()).isEqualTo(2);
        assertThat(line.subtotal()).isEqualTo(Money.of(5940));
        assertThat(order.amounts()).isEqualTo(quote.amounts());
        assertThat(order.email()).isEqualTo("taro@example.com");
        assertThat(order.shippingAddress()).isEqualTo(ADDRESS);
    }

    @Test
    @DisplayName("AC-88 注文できない見積もり（空、購入できない品目を含む）からは注文を作れない")
    void rejectsUnorderableQuote() {
        var empty = new Quote(List.of(), new Amounts(Money.ZERO, Money.ZERO, Money.ZERO, List.of()), false);

        assertThatThrownBy(() -> place(empty)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "taro", "taro@", "@example.com"})
    @DisplayName("AC-86 メールアドレスの形が違えば拒否される")
    void rejectsInvalidEmail(String email) {
        assertBusinessError(() -> Order.place(UUID.randomUUID(), OrderNumber.of("20261008-000001"), UUID.randomUUID(), UUID.randomUUID(),
                ORDERED_AT, quote(), email, ADDRESS, BuyerProfile.UNKNOWN), OrderErrorCode.INVALID_EMAIL);
    }

    // ============================== 補助 ==============================

    /**
     * 注文を作る
     */
    private static Order place(Quote quote) {
        return Order.place(UUID.randomUUID(), OrderNumber.of("20261008-482915"), UUID.randomUUID(), UUID.randomUUID(), ORDERED_AT,
                quote, "taro@example.com", ADDRESS, BuyerProfile.UNKNOWN);
    }

    /**
     * T シャツ（2,700 円・10%）2 枚の見積もり
     */
    private static Quote quote() {
        var variant = new VariantInfo(UUID.randomUUID(), UUID.randomUUID(), "TS-001", "TS-001-WM", "T シャツ",
                List.of(new AxisValue("色", "白"), new AxisValue("サイズ", "M")), Money.of(2700), UUID.randomUUID(),
                null, true, false);
        var item = QuoteItem.of(variant, 2, 10, BigDecimal.TEN, Money.of(2970));
        var amounts = new Amounts(Money.of(5940), Money.of(1000), Money.of(6940),
                List.of(new TaxAmount(BigDecimal.TEN, Money.of(6940), Money.of(630))));
        return new Quote(List.of(item), amounts, true);
    }
}
