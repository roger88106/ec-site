package com.ecsite.module.order.application.checkout;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.application.OrderProperties;
import com.ecsite.module.order.application.model.CartSnapshot;
import com.ecsite.module.order.application.model.Municipality;
import com.ecsite.module.order.application.model.command.PlaceOrderCommand;
import com.ecsite.module.order.application.port.CartDirectory;
import com.ecsite.module.order.application.port.PaymentDirectory;
import com.ecsite.module.order.application.port.RegionDirectory;
import com.ecsite.module.order.application.port.StockDirectory;
import com.ecsite.module.order.domain.checkout.Checkout;
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

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
 * <p>注文番号の衝突の扱いだけを確認する。乱数の 6 桁が重なることは API の統合テストでは起こせないので、注文リポジトリを差し替えて
 * 使用済みの番号と保存の失敗を起こす。注文の成立の流れは {@code PlaceOrderPlacementApiTest} などの統合テストが確認する。
 */
class OrderPlacementServiceTest {

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
     * 空いている注文番号を探す回数の上限（{@code OrderPlacementService.ORDER_NUMBER_ATTEMPTS}）
     */
    private static final int ORDER_NUMBER_ATTEMPTS = 10;

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
     * 注文の成立のサービス
     */
    private OrderPlacementService service;

    /**
     * 注文手続き
     */
    private Checkout checkout;

    /**
     * 注文できる 1 品目のカートと注文手続きと、東京都千代田区の地域で、サービスを作る。
     */
    @BeforeEach
    void setUp() {
        var memberId = UUID.randomUUID();
        var cartId = UUID.randomUUID();
        var variantId = UUID.randomUUID();
        checkout = Checkout.start(UUID.randomUUID(), memberId, cartId, 1, NOW.minus(Duration.ofMinutes(5)), Duration.ofMinutes(15),
                Set.of());

        var checkoutRepository = mock(CheckoutRepository.class);
        when(checkoutRepository.lockById(checkout.id())).thenReturn(Optional.of(checkout));
        when(orderRepository.findIdByCheckoutId(checkout.id())).thenReturn(Optional.empty());
        when(orderRepository.findPendingIdsByMemberId(memberId)).thenReturn(List.of());
        when(orderRepository.insert(any())).thenReturn(true);

        var lines = List.of(new CartSnapshot.Line(variantId, 1));
        var cartDirectory = mock(CartDirectory.class);
        when(cartDirectory.find(cartId)).thenReturn(Optional.of(new CartSnapshot(cartId, memberId, 1, lines)));

        var quoteService = mock(QuoteService.class);
        when(quoteService.quote(lines, NOW, checkout.id())).thenReturn(orderableQuote(variantId));

        when(stockDirectory.commit(eq(checkout.id()), anyMap(), any(), anyString())).thenReturn(Set.of());

        var regionDirectory = mock(RegionDirectory.class);
        when(regionDirectory.findPrefectureName("13")).thenReturn(Optional.of("東京都"));
        when(regionDirectory.findMunicipality("13101")).thenReturn(Optional.of(new Municipality("13101", "千代田区", "13")));

        var properties = new OrderProperties(1000, UUID.randomUUID(), Duration.ofMinutes(15), 3);
        service = new OrderPlacementService(checkoutRepository, orderRepository, new PendingOrderGuard(orderRepository, properties),
                cartDirectory, stockDirectory, paymentDirectory, regionDirectory, quoteService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("注文番号の衝突")
    class OrderNumberCollision {

        @Test
        @DisplayName("AC-90 最初に作った注文番号が使用済みなら、再生成した空いている番号で注文を保存する")
        void retriesUsedNumber() {
            // 準備：最初の番号だけが使用済み
            when(orderRepository.existsByOrderNumber(any())).thenReturn(true, false);

            // 操作：注文する
            service.place(command());

            // 確認：番号を 2 回確認し、2 回目に確認した番号で保存した
            var checked = ArgumentCaptor.forClass(OrderNumber.class);
            verify(orderRepository, times(2)).existsByOrderNumber(checked.capture());
            var saved = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).insert(saved.capture());
            assertThat(saved.getValue().orderNumber()).isEqualTo(checked.getAllValues().getLast());
        }

        @Test
        @DisplayName("AC-90 再生成の上限の回数まで全ての番号が使用済みなら、システムのエラーで、在庫と注文と支払に書かない")
        void failsWhenAllNumbersUsed() {
            // 準備：どの番号も使用済み
            when(orderRepository.existsByOrderNumber(any())).thenReturn(true);

            // 操作と確認：システムのエラー
            assertThatThrownBy(() -> service.place(command())).isInstanceOf(SystemException.class);

            // 確認：上限の回数だけ確認し、在庫の減算・注文の保存・支払の作成をしない
            verify(orderRepository, times(ORDER_NUMBER_ATTEMPTS)).existsByOrderNumber(any());
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

    // ============================== 準備の補助 ==============================

    /**
     * 注文手続きの持ち主が、総額 {@link #TOTAL} を確認して注文するコマンドを作る。
     *
     * @return 注文のコマンド
     */
    private PlaceOrderCommand command() {
        return new PlaceOrderCommand(checkout.id(), checkout.memberId(), "taro@example.com", true, "山田 太郎", "1000001", "13",
                "13101", "千代田 1-1", "0312345678", TOTAL, null, null);
    }

    /**
     * 税込 1,100 円の商品 1 個の、注文できる見積もりを作る。
     *
     * @param variantId バリアント ID
     * @return 見積もり
     */
    private static Quote orderableQuote(UUID variantId) {
        var variant = new VariantInfo(variantId, UUID.randomUUID(), "P-1", "P-1-1", "商品", List.of(), Money.of(1000),
                UUID.randomUUID(), null, true, false);
        var amounts = new Amounts(Money.of(1100), Money.of(1000), TOTAL,
                List.of(new TaxAmount(BigDecimal.TEN, TOTAL, Money.of(190))));
        return new Quote(List.of(QuoteItem.of(variant, 1, 10, BigDecimal.TEN, Money.of(1100))), amounts, true);
    }
}
