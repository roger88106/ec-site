package com.ecsite.module.order.application.checkout;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.order.application.OrderProperties;
import com.ecsite.module.order.domain.OrderErrorCode;
import com.ecsite.module.order.domain.checkout.Checkout;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * 支払待ちの注文の判定（docs/03-機能仕様/order.md「支払待ちの注文の確認」）。<br />
 * 判定は注文の状態で行い、品目を比べない。支払の期限を過ぎ、まだキャンセルされていない注文も対象にする。
 */
@Component
@RequiredArgsConstructor
class PendingOrderGuard {

    /**
     * 注文リポジトリ
     */
    private final OrderRepository orderRepository;

    /**
     * 注文の設定
     */
    private final OrderProperties properties;

    /**
     * 会員の今の支払待ちの注文が、全て確認した注文に含まれ、上限に達していないことを確認する。<br />
     * 確認の判定の後に、上限の判定を行う。両方の判定で、同じ支払待ちの注文の ID を使う。
     *
     * @param memberId             会員 ID
     * @param acknowledgedOrderIds 確認した注文の ID
     * @return 今の支払待ちの注文の ID
     * @throws BusinessException 確認していない支払待ちの注文があるとき、または支払待ちの注文が上限に達しているとき
     */
    Set<UUID> verifyAcknowledged(UUID memberId, Set<UUID> acknowledgedOrderIds) {
        var pendingOrderIds = pendingOrderIds(memberId);
        requireAcknowledged(acknowledgedOrderIds.containsAll(pendingOrderIds));
        requireBelowLimit(pendingOrderIds);
        return pendingOrderIds;
    }

    /**
     * 今の支払待ちの注文が、全て注文手続きの確認した注文に含まれ、上限に達していないことを確認する。<br />
     * 注文の時点の判定で、画面の値でなく、注文手続きに保存した集合と比べる。
     * 確認の判定の後に、上限の判定を行う。両方の判定で、同じ支払待ちの注文の ID を使う（読み取りは 1 回）。
     * 上限の最終的な判定は注文の時点で、開始の後に上限が下がっても、この判定で拒否する。
     *
     * @param checkout 注文手続き
     * @throws BusinessException 確認していない支払待ちの注文があるとき、または支払待ちの注文が上限に達しているとき
     */
    void verifyAcknowledged(Checkout checkout) {
        var pendingOrderIds = pendingOrderIds(checkout.memberId());
        requireAcknowledged(checkout.acknowledges(pendingOrderIds));
        requireBelowLimit(pendingOrderIds);
    }

    /**
     * 会員について、今の支払待ちの注文の ID を読む。
     *
     * @param memberId 会員 ID
     * @return 今の支払待ちの注文の ID
     */
    private Set<UUID> pendingOrderIds(UUID memberId) {
        return Set.copyOf(orderRepository.findPendingIdsByMemberId(memberId));
    }

    /**
     * 確認していない支払待ちの注文があれば拒否する。
     *
     * @param acknowledged 全て確認していれば true
     * @throws BusinessException 確認していない支払待ちの注文があるとき
     */
    private static void requireAcknowledged(boolean acknowledged) {
        if (!acknowledged) {
            throw BusinessException.of(OrderErrorCode.PENDING_ORDER_CONFIRMATION_REQUIRED);
        }
    }

    /**
     * 支払待ちの注文が上限の件数に達していれば拒否する。
     *
     * @param pendingOrderIds 今の支払待ちの注文の ID
     * @throws BusinessException 支払待ちの注文が上限に達しているとき
     */
    private void requireBelowLimit(Set<UUID> pendingOrderIds) {
        if (pendingOrderIds.size() >= properties.pendingOrderLimit()) {
            throw BusinessException.of(OrderErrorCode.PENDING_ORDER_LIMIT_EXCEEDED);
        }
    }
}
