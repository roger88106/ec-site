package com.ecsite.module.order.application.checkout;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.model.id.CartId;
import com.ecsite.common.model.id.MemberId;
import com.ecsite.common.model.id.ProductId;
import com.ecsite.common.model.id.TaxCategoryId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.application.OrderProperties;
import com.ecsite.module.order.application.model.CartSnapshot;
import com.ecsite.module.order.application.model.Municipality;
import com.ecsite.module.order.application.model.PaymentAmountRange;
import com.ecsite.module.order.application.model.command.PlaceOrderCommand;
import com.ecsite.module.order.application.port.CartDirectory;
import com.ecsite.module.order.application.port.PaymentDirectory;
import com.ecsite.module.order.application.port.RegionDirectory;
import com.ecsite.module.order.application.port.StockDirectory;
import com.ecsite.module.order.domain.OrderErrorCode;
import com.ecsite.module.order.domain.checkout.Checkout;
import com.ecsite.module.order.domain.checkout.CheckoutId;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import com.ecsite.module.order.domain.order.Order;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import com.ecsite.module.order.domain.order.vo.OrderNumber;
import com.ecsite.module.order.domain.quote.Amounts;
import com.ecsite.module.order.domain.quote.Quote;
import com.ecsite.module.order.domain.quote.QuoteItem;
import com.ecsite.module.order.domain.quote.TaxAmount;
import com.ecsite.module.order.domain.quote.VariantInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static com.ecsite.support.BusinessErrorAssertions.assertSystemError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 注文の成立のサービスの単体テスト
 *
 * <p>注文番号の衝突の扱いと、総額の判定の順を確認する。乱数の 6 桁が重なることは API の統合テストでは起こせないので、乱数を決めた値に固定し、
 * 注文リポジトリを差し替えて使用済みの番号と保存の失敗を起こす。注文の成立の流れは {@code PlaceOrderPlacementApiTest} などの統合テストが確認する。
 */
class OrderPlacementCommandServiceTest {

    // ============================== 定数 ==============================

    /**
     * 注文日時
     *
     * <p>日本時間の 2026-10-08 00:00。注文番号の日付の部分は 20261008。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 注文の総額（税込 1,100 円の商品 1 個と送料 1,000 円）
     */
    private static final Money TOTAL = Money.of(2100);

    /**
     * 見積もりの送料の消費税（送料 1,000 円、標準税率）
     */
    private static final Money SHIPPING_TAX = Money.of(90);

    /**
     * 空いている注文番号を探す回数の上限（{@code OrderPlacementCommandService.ORDER_NUMBER_ATTEMPTS}）
     */
    private static final int ORDER_NUMBER_ATTEMPTS = 10;

    /**
     * 範囲の上限（99,999,999 円）の外の総額
     */
    private static final long OUT_OF_RANGE_TOTAL = 100_000_000;

    /**
     * 注文番号の乱数の部分の範囲（{@code OrderNumber} の 6 桁）
     */
    private static final int RANDOM_BOUND = 1_000_000;

    /**
     * 乱数が 1 回目に返す値
     */
    private static final int FIRST_RANDOM = 123456;

    /**
     * 乱数が 2 回目に返す値
     */
    private static final int SECOND_RANDOM = 654321;

    /**
     * 1 回目の注文番号（注文日 20261008）
     */
    private static final String FIRST_NUMBER = "20261008-123456";

    /**
     * 2 回目の注文番号（注文日 20261008）
     */
    private static final String SECOND_NUMBER = "20261008-654321";

    // ============================== Bean ==============================

    /**
     * 注文リポジトリ（使用済みの番号と保存の結果を差し替える）
     */
    private final OrderRepository orderRepository = mock(OrderRepository.class);

    /**
     * 在庫の腐敗防止層（足りない品目はない）
     */
    private final StockDirectory stockDirectory = mock(StockDirectory.class);

    /**
     * 決済の腐敗防止層
     */
    private final PaymentDirectory paymentDirectory = mock(PaymentDirectory.class);

    /**
     * 見積もりのサービス（見積もりの総額を差し替える）
     */
    private final QuoteQueryService quoteQueryService = mock(QuoteQueryService.class);

    /**
     * 乱数（注文番号の乱数の部分の値を固定する）
     */
    private final RandomGenerator random = mock(RandomGenerator.class);

    /**
     * 注文の成立のサービス
     */
    private OrderPlacementCommandService service;

    /**
     * カートの品目のバリアント ID
     */
    private VariantId variantId;

    /**
     * カートの品目
     */
    private List<CartSnapshot.Line> lines;

    /**
     * 注文手続き
     */
    private Checkout checkout;

    /**
     * 注文できる 1 品目のカートと注文手続きと、東京都千代田区の地域で、サービスを作る。
     */
    @BeforeEach
    void setUp() {
        var memberId = new MemberId(UUID.randomUUID());
        var cartId = new CartId(UUID.randomUUID());
        variantId = new VariantId(UUID.randomUUID());
        checkout = Checkout.start(new CheckoutId(UUID.randomUUID()), memberId, cartId, 1, NOW.minus(Duration.ofMinutes(5)), Duration.ofMinutes(15),
                Set.of());

        var checkoutRepository = mock(CheckoutRepository.class);
        when(checkoutRepository.lockById(checkout.id())).thenReturn(Optional.of(checkout));
        when(orderRepository.findIdByCheckoutId(checkout.id())).thenReturn(Optional.empty());
        when(orderRepository.findPendingIdsByMemberId(memberId)).thenReturn(List.of());
        when(orderRepository.insert(any())).thenReturn(true);

        lines = List.of(new CartSnapshot.Line(variantId, 1));
        var cartDirectory = mock(CartDirectory.class);
        when(cartDirectory.find(cartId)).thenReturn(Optional.of(new CartSnapshot(cartId, memberId, 1, lines)));

        when(quoteQueryService.quote(lines, NOW, checkout.id())).thenReturn(orderableQuote(variantId));
        when(paymentDirectory.amountRange()).thenReturn(new PaymentAmountRange(Money.of(50), Money.of(99_999_999)));

        when(stockDirectory.commit(eq(checkout.id()), anyMap(), any(), anyString())).thenReturn(Set.of());

        var regionDirectory = mock(RegionDirectory.class);
        when(regionDirectory.findPrefectureName("13")).thenReturn(Optional.of("東京都"));
        when(regionDirectory.findMunicipality("13101")).thenReturn(Optional.of(new Municipality("13101", "千代田区", "13")));

        var properties = new OrderProperties(1000, UUID.randomUUID(), Duration.ofMinutes(15), 3);
        service = new OrderPlacementCommandService(checkoutRepository, orderRepository, new PendingOrderGuard(orderRepository, properties),
                cartDirectory, stockDirectory, paymentDirectory, regionDirectory, quoteQueryService, Clock.fixed(NOW, ZoneOffset.UTC), UUID::randomUUID,
                random);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("注文番号の衝突")
    class OrderNumberCollision {

        @Test
        @DisplayName("AC-90 最初に作った注文番号が使用済みなら、再生成した空いている番号で注文を保存する")
        void retriesUsedNumber() {
            // 準備：乱数が 1 回目に 123456、2 回目に 654321 を返し、1 回目の番号だけが使用済み
            when(random.nextInt(RANDOM_BOUND)).thenReturn(FIRST_RANDOM, SECOND_RANDOM);
            when(orderRepository.existsByOrderNumber(OrderNumber.of(FIRST_NUMBER))).thenReturn(true);

            // 操作：注文する
            service.place(command());

            // 確認：2 回目の番号で保存した
            var saved = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).insert(saved.capture());
            assertThat(saved.getValue().orderNumber()).isEqualTo(OrderNumber.of(SECOND_NUMBER));
        }

        @Test
        @DisplayName("AC-90 再生成の上限の回数まで全ての番号が使用済みなら、システムのエラーで、在庫と注文と支払に書かない")
        void failsWhenAllNumbersUsed() {
            // 準備：乱数が 1 回目に 123456、2 回目以降に 654321 を返し、どの番号も使用済み
            when(random.nextInt(RANDOM_BOUND)).thenReturn(FIRST_RANDOM, SECOND_RANDOM);
            when(orderRepository.existsByOrderNumber(any())).thenReturn(true);

            // 操作と確認：共通のシステムのエラー
            assertSystemError(() -> service.place(command()), CommonErrorCode.SYSTEM_ERROR);

            // 確認：上限の回数だけ乱数を引いて番号を確認し（乱数は 1 回目だけ 123456、残りは 654321 を返すので、番号は 1 回目の 1 件と 2 回目の 9 件）、在庫の減算・注文の保存・支払の作成をしない
            verify(random, times(ORDER_NUMBER_ATTEMPTS)).nextInt(RANDOM_BOUND);
            verify(orderRepository, times(ORDER_NUMBER_ATTEMPTS)).existsByOrderNumber(any());
            verify(orderRepository).existsByOrderNumber(OrderNumber.of(FIRST_NUMBER));
            verify(orderRepository, times(ORDER_NUMBER_ATTEMPTS - 1)).existsByOrderNumber(OrderNumber.of(SECOND_NUMBER));
            verify(stockDirectory, never()).commit(any(), anyMap(), any(), anyString());
            verify(orderRepository, never()).insert(any());
            verify(paymentDirectory, never()).create(any(), any(), any());
        }

        @Test
        @DisplayName("AC-90 番号を確認してから保存するまでに同じ番号が使われたら、同時の更新のエラーで、支払を作らない")
        void rejectsNumberTakenBeforeInsert() {
            // 準備：番号は空いているが、保存のときには使われている
            when(orderRepository.existsByOrderNumber(any())).thenReturn(false);
            when(orderRepository.insert(any())).thenReturn(false);

            // 操作と確認：業務のエラー CONCURRENT_MODIFICATION（在庫の減算はトランザクションごと取り消される）
            assertBusinessError(() -> service.place(command()), CommonErrorCode.CONCURRENT_MODIFICATION);

            // 確認：支払を作らない
            verify(paymentDirectory, never()).create(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("総額の範囲")
    class AmountRange {

        @Test
        @DisplayName("BR-518-2 総額が変わり、かつ新しい総額が範囲の外なら、総額が変わったエラーで拒否する（総額の比較の後に範囲を判定する）")
        void reportsChangedTotalBeforeRange() {
            // 準備：見積もりの総額が範囲の上限の外で、確認した総額（TOTAL）と違う
            when(quoteQueryService.quote(lines, NOW, checkout.id())).thenReturn(quoteWithTotal(OUT_OF_RANGE_TOTAL));

            // 操作と確認：総額が変わったエラー
            assertBusinessError(() -> service.place(command(TOTAL)), OrderErrorCode.TOTAL_AMOUNT_CHANGED);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 注文手続きの持ち主が、総額 {@link #TOTAL} を確認して注文するコマンドを作る。
     *
     * @return 注文のコマンド
     */
    private PlaceOrderCommand command() {
        return command(TOTAL);
    }

    /**
     * 注文手続きの持ち主が、指定の総額を確認して注文するコマンドを作る。
     *
     * @param expectedTotal 確認した総額
     * @return 注文のコマンド
     */
    private PlaceOrderCommand command(Money expectedTotal) {
        return new PlaceOrderCommand(checkout.id(), checkout.memberId(), "taro@example.com", true, "山田 太郎", "1000001", "13",
                "13101", "千代田 1-1", "0312345678", expectedTotal, null, null);
    }

    /**
     * 税込 1,100 円の商品 1 個の、注文できる見積もりを作る。
     *
     * @param variantId バリアント ID
     * @return 見積もり
     */
    private static Quote orderableQuote(VariantId variantId) {
        return quote(variantId, TOTAL);
    }

    /**
     * 税込 1,100 円の商品 1 個で、総額を指定した、注文できる見積もりを作る。送料は総額との差。送料の消費税は、総額によらず 90 円（範囲外の総額のテストは、送料の消費税を使わない）。
     *
     * @param total 総額（円）
     * @return 見積もり
     */
    private Quote quoteWithTotal(long total) {
        return quote(variantId, Money.of(total));
    }

    /**
     * 税込 1,100 円の商品 1 個の見積もりを、総額を指定して作る。
     *
     * @param variantId バリアント ID
     * @param total     総額
     * @return 見積もり
     */
    private static Quote quote(VariantId variantId, Money total) {
        var variant = new VariantInfo(variantId, new ProductId(UUID.randomUUID()), "P-1", "P-1-1", "商品", List.of(), Money.of(1000),
                new TaxCategoryId(UUID.randomUUID()), null, true, false);
        var amounts = new Amounts(Money.of(1100), Money.of(total.yen() - 1100), total,
                List.of(new TaxAmount(BigDecimal.TEN, total, Money.of(190))));
        return new Quote(List.of(QuoteItem.of(variant, 1, 10, BigDecimal.TEN, Money.of(1100))), amounts,
                SHIPPING_TAX, true);
    }
}
