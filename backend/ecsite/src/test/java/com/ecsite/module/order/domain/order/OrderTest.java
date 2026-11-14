package com.ecsite.module.order.domain.order;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.domain.OrderErrorCode;
import com.ecsite.module.order.domain.order.vo.BuyerProfile;
import com.ecsite.module.order.domain.order.vo.OrderLine;
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
import org.junit.jupiter.api.Nested;
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
 * 注文の単体テスト
 *
 * <p>注文の成立（{@link Order#place}）だけを確認する。見積もりが注文できるかの判定（{@code orderable}）は
 * {@code QuoteTest}、API を通した流れは {@code CheckoutApiTest}・{@code PlaceOrderApiTest} が確認する。
 *
 * <p>準備のデータと確認の期待値は、同じ定数から作る（準備した物から期待値を取り出さない）。
 */
class OrderTest {

    // ============================== 定数 ==============================

    /**
     * 注文日時
     *
     * <p>日本時間の 2026-10-08 00:30。
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * お届け先
     */
    private static final ShippingAddress ADDRESS =
            ShippingAddress.of("山田 太郎", "100-0001", TestAreas.CHIYODA, "千代田 1-1", "03-1234-5678");

    /**
     * 注文のメールアドレス
     */
    private static final String EMAIL = "taro@example.com";

    /**
     * T シャツの品番
     */
    private static final String PRODUCT_CODE = "TS-001";

    /**
     * T シャツ（白・M）のバリアント品番
     */
    private static final String VARIANT_CODE = "TS-001-WM";

    /**
     * T シャツの商品名
     */
    private static final String PRODUCT_NAME = "T シャツ";

    /**
     * T シャツ（白・M）の軸の値
     */
    private static final List<AxisValue> AXIS_VALUES = List.of(new AxisValue("色", "白"), new AxisValue("サイズ", "M"));

    /**
     * T シャツの税抜単価
     */
    private static final Money UNIT_PRICE_EXCL_TAX = Money.of(2700);

    /**
     * T シャツの税率（%）
     */
    private static final BigDecimal RATE_PERCENT = BigDecimal.TEN;

    /**
     * T シャツの税込単価
     */
    private static final Money UNIT_PRICE_INCL_TAX = Money.of(2970);

    /**
     * 注文できる見積もりの数量
     */
    private static final int QUANTITY = 2;

    /**
     * 注文できる見積もりの小計（税込単価 × 数量）
     */
    private static final Money SUBTOTAL = Money.of(5940);

    // ============================== テスト ==============================

    @Nested
    @DisplayName("注文の成立")
    class Placement {

        @Test
        @DisplayName("AC-143、BR-14 成立した注文は「支払待ち」になる")
        void isPendingPayment() {
            // 操作：注文できる見積もりから注文を作る
            var order = place(quote());

            // 確認：状態が「支払待ち」
            assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        }

        @Test
        @DisplayName("AC-143、BR-14 成立すると、注文成立のイベントが 1 件だけ登録される")
        void registersPlacedEvent() {
            // 操作：注文できる見積もりから注文を作る
            var order = place(quote());

            // 確認：この注文の「なし → 支払待ち」のイベントだけが登録されている
            assertPlacedEventOnly(order);
        }
    }

    @Nested
    @DisplayName("明細と注文の項目")
    class Lines {

        @Test
        @DisplayName("AC-92 明細の数は、見積もりの品目の数と同じ")
        void hasOneLinePerItem() {
            // 操作：品目が 1 つの見積もりから注文を作る
            var order = place(quote());

            // 確認：明細が 1 行
            assertThat(order.lines()).hasSize(1);
        }

        @Test
        @DisplayName("AC-92 明細は、注文時点の品番・商品名・軸の値・税抜単価・税率・税込単価の写しと、数量・小計を持つ")
        void copiesItemIntoLine() {
            // 準備：T シャツ 2 枚の見積もり
            var quote = quote();

            // 操作：注文を作る
            var order = place(quote);

            // 確認：明細の全ての項目が、見積もりの品目の写し
            assertLineCopiedFromQuote(order.lines().getFirst(), quote.items().getFirst().variant().variantId());
        }

        @Test
        @DisplayName("AC-92 注文は、見積もりの金額の内訳を持つ")
        void keepsAmounts() {
            // 準備：T シャツ 2 枚の見積もり
            var quote = quote();

            // 操作：注文を作る
            var order = place(quote);

            // 確認：金額の内訳が見積もりと同じ
            assertThat(order.amounts()).isEqualTo(quote.amounts());
        }

        @Test
        @DisplayName("AC-92 注文は、入力したメールアドレスとお届け先を持つ")
        void keepsBuyerInput() {
            // 操作：メールアドレスとお届け先を渡して注文を作る
            var order = place(quote());

            // 確認：渡した値のまま
            assertThat(order.email()).isEqualTo(EMAIL);
            assertThat(order.shippingAddress()).isEqualTo(ADDRESS);
        }
    }

    @Nested
    @DisplayName("注文できない見積もり")
    class UnorderableQuote {

        @Test
        @DisplayName("AC-88 空の見積もりからは注文を作れない")
        void rejectsEmptyQuote() {
            // 準備：品目のない見積もり
            var empty = new Quote(List.of(), new Amounts(Money.ZERO, Money.ZERO, Money.ZERO, List.of()), false);

            // 操作と確認：プログラムの誤りとして拒否される
            assertThatThrownBy(() -> place(empty)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("AC-88 購入できない品目を含む見積もりからは注文を作れない")
        void rejectsQuoteWithUnpurchasableItem() {
            // 準備：在庫が 0 で購入できない品目を含む見積もり
            var unorderable = quoteWithOutOfStockItem();

            // 確認（前提）：品目が購入できず、見積もりが注文できない
            assertThat(unorderable.items().getFirst().purchasable()).isFalse();
            assertThat(unorderable.orderable()).isFalse();

            // 操作と確認：プログラムの誤りとして拒否される
            assertThatThrownBy(() -> place(unorderable)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("メールアドレスの検証")
    class EmailValidation {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"", " ", "taro", "taro@", "@example.com"})
        @DisplayName("AC-86 メールアドレスの形が違えば拒否される")
        void rejectsInvalidEmail(String email) {
            // 操作と確認：業務のエラー INVALID_EMAIL で拒否される
            assertBusinessError(() -> Order.place(UUID.randomUUID(), OrderNumber.of("20261008-000001"), UUID.randomUUID(), UUID.randomUUID(),
                    ORDERED_AT, quote(), email, ADDRESS, BuyerProfile.UNKNOWN), OrderErrorCode.INVALID_EMAIL);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 注文成立のイベントだけが登録されていることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>登録されたイベントが 1 件だけ</li>
     *   <li>種類が {@link OrderStatusChanged}</li>
     *   <li>注文 ID がこの注文</li>
     *   <li>前の状態がない（成立）</li>
     *   <li>後の状態が「支払待ち」</li>
     * </ul>
     *
     * @param order 確認する注文
     */
    private static void assertPlacedEventOnly(Order order) {
        assertThat(order.pullEvents()).singleElement().isInstanceOfSatisfying(OrderStatusChanged.class, event -> {
            assertThat(event.orderId()).isEqualTo(order.id());
            assertThat(event.from()).isNull();
            assertThat(event.to()).isEqualTo(OrderStatusChanged.Status.PENDING_PAYMENT);
        });
    }

    /**
     * 明細が、注文できる見積もり（{@link #quote()}）の品目の写しであることを確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>行番号が 1</li>
     *   <li>バリアント ID</li>
     *   <li>品番、バリアント品番</li>
     *   <li>商品名</li>
     *   <li>軸の値（並びを含む）</li>
     *   <li>税抜単価、税率、税込単価</li>
     *   <li>数量、小計</li>
     * </ul>
     *
     * @param line      確認する明細
     * @param variantId 見積もりの品目のバリアント ID（実行ごとに作るので、定数にできない）
     */
    private static void assertLineCopiedFromQuote(OrderLine line, UUID variantId) {
        assertThat(line.lineNo()).isEqualTo(1);
        assertThat(line.variantId()).isEqualTo(variantId);
        assertThat(line.productCode()).isEqualTo(PRODUCT_CODE);
        assertThat(line.variantCode()).isEqualTo(VARIANT_CODE);
        assertThat(line.productName()).isEqualTo(PRODUCT_NAME);
        assertThat(line.axisValues()).containsExactlyElementsOf(AXIS_VALUES);
        assertThat(line.unitPriceExclTax()).isEqualTo(UNIT_PRICE_EXCL_TAX);
        assertThat(line.ratePercent()).isEqualByComparingTo(RATE_PERCENT);
        assertThat(line.unitPriceInclTax()).isEqualTo(UNIT_PRICE_INCL_TAX);
        assertThat(line.quantity()).isEqualTo(QUANTITY);
        assertThat(line.subtotal()).isEqualTo(SUBTOTAL);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 注文を作る。
     *
     * @param quote 見積もり
     * @return 注文
     */
    private static Order place(Quote quote) {
        return Order.place(UUID.randomUUID(), OrderNumber.of("20261008-482915"), UUID.randomUUID(), UUID.randomUUID(), ORDERED_AT,
                quote, EMAIL, ADDRESS, BuyerProfile.UNKNOWN);
    }

    /**
     * T シャツ 2 枚の、注文できる見積もりを作る。
     *
     * <p>送料は 1,000 円、総額は 6,940 円（うち消費税 630 円）。
     *
     * @return 見積もり
     */
    private static Quote quote() {
        var item = QuoteItem.of(tShirt(), QUANTITY, 10, RATE_PERCENT, UNIT_PRICE_INCL_TAX);
        var amounts = new Amounts(SUBTOTAL, Money.of(1000), Money.of(6940),
                List.of(new TaxAmount(RATE_PERCENT, Money.of(6940), Money.of(630))));
        return new Quote(List.of(item), amounts, true);
    }

    /**
     * 在庫が 0 で購入できない T シャツ 1 枚の、注文できない見積もりを作る。
     *
     * <p>購入できない品目は合計に含めないので、金額は 0。
     *
     * @return 見積もり
     */
    private static Quote quoteWithOutOfStockItem() {
        var item = QuoteItem.of(tShirt(), 1, 0, RATE_PERCENT, UNIT_PRICE_INCL_TAX);
        return new Quote(List.of(item), new Amounts(Money.ZERO, Money.ZERO, Money.ZERO, List.of()), false);
    }

    /**
     * T シャツ（白・M）のバリアントを、定数から作る。
     *
     * @return バリアント
     */
    private static VariantInfo tShirt() {
        return new VariantInfo(UUID.randomUUID(), UUID.randomUUID(), PRODUCT_CODE, VARIANT_CODE, PRODUCT_NAME,
                AXIS_VALUES, UNIT_PRICE_EXCL_TAX, UUID.randomUUID(), null, true, false);
    }
}
