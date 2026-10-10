package com.ecsite.module.order.application;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.order.domain.order.OrderStatus;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * 注文の一覧のクエリリポジトリ（B 側。AC-97〜99）
 */
public interface OrderQueryRepository {

    /**
     * 注文を、注文日時の新しい順に取得する。同じ日時なら ID の順。
     *
     * @param filter  絞り込みの条件
     * @param request ページの指定
     * @return 注文の一覧
     */
    PageResult<OrderSummaryView> findOrders(Filter filter, PageRequest request);

    /**
     * 絞り込みの条件。指定しない項目は null
     *
     * @param orderNumberPrefix 注文番号の前方一致
     * @param status            状態
     * @param orderedFrom       注文日時の下限（この時刻を含む）
     * @param orderedUntil      注文日時の上限（この時刻を含まない）
     */
    record Filter(@Nullable String orderNumberPrefix, @Nullable OrderStatus status, @Nullable Instant orderedFrom,
                  @Nullable Instant orderedUntil) {
    }

    /**
     * 注文一覧の 1 件（AC-97）
     *
     * @param id           注文 ID
     * @param orderNumber  注文番号
     * @param orderedAt    注文日時
     * @param customerName お届け先の氏名
     * @param total        総額（円）
     * @param status       状態
     */
    record OrderSummaryView(UUID id, String orderNumber, Instant orderedAt, String customerName, long total,
                            OrderStatus status) {
    }
}
