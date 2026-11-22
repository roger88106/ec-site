package com.ecsite.module.order.application;

import com.ecsite.common.model.id.OrderId;
import com.ecsite.module.order.application.port.CartDirectory;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import com.ecsite.module.order.domain.order.vo.OrderLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 注文に使ったカートの更新のサービス
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
     * <p>受付にした時刻より後に入れた行は除かない（同じイベントを再度受けても、除いた後に再び入れた行を除かない）。
     * 数量を変えても入れた日時は変わらないので、受付の前に入れた行は、受付の後に数量を変えていても除く。
     * 注文または会員のカートがなければ何もしない。除く品目がなければ、カートは変わらない。
     *
     * @param orderId    注文 ID
     * @param acceptedAt 受付にした時刻（注文の状態の変化のイベントの発生時刻）
     */
    public void removeOrderedItems(OrderId orderId, Instant acceptedAt) {
        orderRepository.findById(orderId).ifPresent(order -> {
            var variantIds = order.lines().stream().map(OrderLine::variantId).distinct().toList();
            cartDirectory.findMemberCartId(order.memberId())
                    .ifPresent(cartId -> cartDirectory.removeItems(cartId, variantIds, acceptedAt));
        });
    }
}
