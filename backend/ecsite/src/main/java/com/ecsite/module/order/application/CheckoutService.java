package com.ecsite.module.order.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.order.application.model.CheckoutView;
import com.ecsite.module.order.application.port.CartDirectory;
import com.ecsite.module.order.application.port.StockDirectory;
import com.ecsite.module.order.domain.checkout.Checkout;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import com.ecsite.module.order.domain.order.OrderErrorCode;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import com.ecsite.module.order.domain.quote.Quote;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 注文手続きのサービス（docs/backend/modules/order.md「注文手続き」）
 */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    /**
     * 注文手続きリポジトリ
     */
    private final CheckoutRepository checkoutRepository;

    /**
     * 注文リポジトリ（成立済みの注文の確認）
     */
    private final OrderRepository orderRepository;

    /**
     * カートの腐敗防止層
     */
    private final CartDirectory cartDirectory;

    /**
     * 在庫の腐敗防止層
     */
    private final StockDirectory stockDirectory;

    /**
     * 見積もりのサービス
     */
    private final QuoteService quoteService;

    /**
     * 注文の設定（引当の期限）
     */
    private final OrderProperties properties;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 注文手続きに進む。全品目をカートの数量だけ引き当てる。<br />
     * 同じカートの前の注文手続きの引当は解く（進み直し）。1 品目でも引き当てられなければ何も引き当てない。
     * 始められるのは、会員の自分の会員のカートだけ。ログインせずに作ったカートと、ほかの会員のカートは 404。
     *
     * @param cartId   カート ID
     * @param memberId 会員 ID
     * @return 注文手続き ID
     */
    @Transactional
    public UUID start(UUID cartId, UUID memberId) {
        var now = clock.instant();
        var cart = cartDirectory.find(cartId)
                .filter(found -> found.isOwnedBy(memberId))
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
        // 進み直し：前の注文手続きの引当を解いてから判定する（自分の前の引当で足りなくならないように）
        releaseReservations(cartId);

        verifyOrderable(quoteService.quote(cart.lines(), now, null));

        var checkout = Checkout.start(UUID.randomUUID(), memberId, cartId, cart.version(), now, properties.reservationTtl());
        checkoutRepository.insert(checkout);
        var shortages = stockDirectory.reserve(checkout.id(), quantities(cart.lines()), checkout.expiresAt());
        if (!shortages.isEmpty()) {
            // 見積もりの後に、ほかの消費者に引き当てられた
            throw BusinessException.of(OrderErrorCode.OUT_OF_STOCK);
        }
        return checkout.id();
    }

    /**
     * カートのすべての注文手続きの引当を解く（カート変更）。何度呼んでも結果は同じ。
     *
     * @param cartId カート ID
     */
    @Transactional
    public void releaseReservations(UUID cartId) {
        checkoutRepository.findIdsByCartId(cartId).forEach(stockDirectory::release);
    }

    /**
     * 注文手続きを表示する。見積もりは表示の時点の価格・税率で、自分の引当を販売可能数から除く。
     * 始めた会員だけが参照できる。ほかの会員には 404。
     *
     * @param checkoutId 注文手続き ID
     * @param memberId   要求した会員の ID
     * @return 注文手続きの表示
     */
    @Transactional(readOnly = true)
    public CheckoutView get(UUID checkoutId, UUID memberId) {
        var now = clock.instant();
        var checkout = checkoutRepository.findById(checkoutId)
                .filter(found -> found.isOwnedBy(memberId))
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
        var lines = cartDirectory.find(checkout.cartId()).map(CartDirectory.CartSnapshot::lines).orElse(List.of());
        return new CheckoutView(checkout.id(), quoteService.quote(lines, now, checkout.id()), checkout.expiresAt(),
                orderRepository.findIdByCheckoutId(checkoutId).orElse(null));
    }

    /**
     * 見積もりが注文できることを確かめる。在庫だけが足りなければ 409、それ以外（空、表示されない、販売停止）は 400。
     *
     * @param quote 見積もり
     */
    static void verifyOrderable(Quote quote) {
        if (quote.orderable()) {
            return;
        }
        throw BusinessException.of(quote.shortOfStockOnly() ? OrderErrorCode.OUT_OF_STOCK : OrderErrorCode.CART_NOT_ORDERABLE);
    }

    /**
     * カートの品目を、バリアント ID → 数量にする（カートの並びを保つ）。
     *
     * @param lines 品目
     * @return バリアント ID → 数量
     */
    static Map<UUID, Integer> quantities(List<CartDirectory.Line> lines) {
        var quantities = new LinkedHashMap<UUID, Integer>();
        lines.forEach(line -> quantities.put(line.variantId(), line.quantity()));
        return quantities;
    }
}
