package com.ecsite.module.order.application.checkout;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.order.application.OrderProperties;
import com.ecsite.module.order.application.model.CartSnapshot;
import com.ecsite.module.order.application.port.CartDirectory;
import com.ecsite.module.order.application.port.StockDirectory;
import com.ecsite.module.order.domain.OrderErrorCode;
import com.ecsite.module.order.domain.checkout.Checkout;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import com.ecsite.module.order.domain.quote.Quote;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 注文手続きのサービス
 */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    /**
     * 注文手続きリポジトリ
     */
    private final CheckoutRepository checkoutRepository;

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
     * 支払待ちの注文の判定
     */
    private final PendingOrderGuard pendingOrderGuard;

    /**
     * 注文の設定（引当の期限）
     */
    private final OrderProperties properties;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 注文手続きに進む。
     *
     * <p>全品目をカートの数量だけ引き当てる。同じカートの前の注文手続きの引当は解く（進み直し）。
     * 1 品目でも引き当てられなければ何も引き当てない。注文手続きには、受けた一覧でなく、その時点の支払待ちの注文を保存する。
     *
     * @param cartId               カート ID
     * @param memberId             会員 ID
     * @param acknowledgedOrderIds 確認した注文の ID（確認の画面で表示した注文。確認していなければ空）
     * @return 注文手続き ID
     * @throws BusinessException カートがない、または会員の自分のカートでない（{@code NOT_FOUND}）
     * @throws BusinessException 確認していない支払待ちの注文がある（{@code PENDING_ORDER_CONFIRMATION_REQUIRED}）
     * @throws BusinessException 支払待ちの注文が上限に達している（{@code PENDING_ORDER_LIMIT_EXCEEDED}）
     * @throws BusinessException 在庫が足りない（{@code OUT_OF_STOCK}）
     * @throws BusinessException 注文できないカート（{@code CART_NOT_ORDERABLE}）
     */
    @Transactional
    public UUID start(@NonNull UUID cartId, @NonNull UUID memberId, @NonNull Set<UUID> acknowledgedOrderIds) {
        var now = clock.instant();
        // カート取得 ※始められるのは会員の自分のカートだけ。ログインせずに作ったカートと、ほかの会員のカートは、ないときと同じにする
        var cart = cartDirectory.find(cartId)
                .filter(found -> found.isOwnedBy(memberId))
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));

        // 支払待ちの注文の確認
        // 拒否するときは前の引当を残し、購入の可否によらず確認の画面へ案内するので、引当の解除と購入の可否の判定より先に行う
        // （確認の判定、上限の判定の順）
        var pendingOrderIds = pendingOrderGuard.verifyAcknowledged(memberId, acknowledgedOrderIds);

        // 購入できるかの判定 ※前の注文手続きの引当を解いてから判定する（自分の前の引当で足りなくならないように）
        releaseReservations(cartId);
        verifyOrderable(quoteService.quote(cart.lines(), now, null));

        // 注文手続き登録と引当
        var checkout = Checkout.start(UUID.randomUUID(), memberId, cartId, cart.version(), now, properties.reservationTtl(), pendingOrderIds);
        checkoutRepository.insert(checkout);
        var shortages = stockDirectory.reserve(checkout.id(), quantities(cart.lines()), checkout.expiresAt());
        if (!shortages.isEmpty()) {
            // 見積もりの後に、ほかの消費者に引き当てられた
            throw BusinessException.of(OrderErrorCode.OUT_OF_STOCK);
        }
        return checkout.id();
    }

    /**
     * カートのすべての注文手続きの引当を解く。
     *
     * <p>何度呼んでも結果は同じ。
     *
     * @param cartId カート ID
     */
    @Transactional
    public void releaseReservations(@NonNull UUID cartId) {
        for (var checkoutId : checkoutRepository.findIdsByCartId(cartId)) {
            stockDirectory.release(checkoutId);
        }
    }

    /**
     * 期限が指定した時刻より前の注文手続きを、件数の上限まで消す。
     *
     * <p>注文になったものも消す。
     *
     * @param expiredBefore この時刻より前に期限が切れた注文手続きを消す
     * @param limit         消す件数の上限
     * @return 消した件数
     */
    @Transactional
    public int deleteExpired(@NonNull Instant expiredBefore, int limit) {
        return checkoutRepository.deleteExpiredBefore(expiredBefore, limit);
    }

    /**
     * 見積もりが注文できることを確認する。
     *
     * @param quote 見積もり
     * @throws BusinessException 在庫だけが足りない（{@code OUT_OF_STOCK}）
     * @throws BusinessException それ以外で注文できない（{@code CART_NOT_ORDERABLE}。空、表示されない、販売停止）
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
    static Map<UUID, Integer> quantities(List<CartSnapshot.Line> lines) {
        var quantities = new LinkedHashMap<UUID, Integer>();
        lines.forEach(line -> quantities.put(line.variantId(), line.quantity()));
        return quantities;
    }
}
