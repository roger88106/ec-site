package com.ecsite.module.order.application;

import com.ecsite.module.order.application.port.CartDirectory;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import com.ecsite.module.order.domain.order.vo.OrderLine;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 注文したカートの更新のサービス
 *
 * <p>注文を受付にした後に、注文した品目を会員のカートから除く。注文を受付にする処理とは別のトランザクションで動く。
 */
@Service
@Transactional
@RequiredArgsConstructor
public class OrderCartCommandService {

    /**
     * 注文リポジトリ
     */
    private final OrderRepository orderRepository;

    /**
     * カートの腐敗防止層
     */
    private final CartDirectory cartDirectory;

    /**
     * 注文した品目（バリアント）を、注文した会員のカートから、数量によらず行ごと除く。
     *
     * <p>注文または会員のカートがなければ何もしない。除く品目がなければ、カートは変わらない。何度呼んでも結果は同じ。
     *
     * @param orderId 注文 ID
     */
    public void removeOrderedItems(@NonNull UUID orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            var variantIds = order.lines().stream().map(OrderLine::variantId).distinct().toList();
            cartDirectory.findMemberCartId(order.memberId())
                    .ifPresent(cartId -> cartDirectory.removeItems(cartId, variantIds));
        });
    }
}
