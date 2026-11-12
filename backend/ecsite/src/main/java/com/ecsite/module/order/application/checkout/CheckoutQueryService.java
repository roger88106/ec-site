package com.ecsite.module.order.application.checkout;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.order.application.model.CartSnapshot;
import com.ecsite.module.order.application.model.view.CheckoutView;
import com.ecsite.module.order.application.port.CartDirectory;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * 注文手続きの参照のサービス
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheckoutQueryService {

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
     * 見積もりのサービス
     */
    private final QuoteService quoteService;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 注文手続きを表示する。見積もりは表示の時点の価格・税率で、自分の引当を販売可能数から除く。
     *
     * @param checkoutId 注文手続き ID
     * @param memberId   リクエストした会員の ID
     * @return 注文手続きの表示
     * @throws BusinessException 注文手続きがない、または始めた会員でない（{@code NOT_FOUND}）
     */
    public CheckoutView get(@NonNull UUID checkoutId, @NonNull UUID memberId) {
        var now = clock.instant();
        // 注文手続き取得 ※始めた会員だけが参照できる
        var checkout = checkoutRepository.findById(checkoutId)
                .filter(found -> found.isOwnedBy(memberId))
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));

        // 見積もりと、成立済みの注文
        var lines = cartDirectory.find(checkout.cartId()).map(CartSnapshot::lines).orElse(List.of());
        return new CheckoutView(checkout.id(), quoteService.quote(lines, now, checkout.id()), checkout.expiresAt(),
                orderRepository.findIdByCheckoutId(checkoutId).orElse(null));
    }
}
