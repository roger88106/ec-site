package com.ecsite.module.order.application.port;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.order.application.model.OrderSummaryView;
import com.ecsite.module.order.domain.order.OrderStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * 注文の一覧のクエリリポジトリ（B 側）
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
     * @param memberId          会員 ID（会員の注文の一覧）
     * @param orderNumberPrefix 注文番号の前方一致
     * @param status            状態
     * @param orderedFrom       注文日時の下限（この時刻を含む）
     * @param orderedUntil      注文日時の上限（この時刻を含まない）
     * @param orderIds          注文 ID のどれか（他のモジュールで絞り込んだ結果）。空なら 1 件も合わない
     */
    record Filter(@Nullable UUID memberId, @Nullable String orderNumberPrefix, @Nullable OrderStatus status, @Nullable Instant orderedFrom,
                  @Nullable Instant orderedUntil, @Nullable Collection<UUID> orderIds) {

        /**
         * 会員の注文だけの条件を作る。
         *
         * @param memberId 会員 ID
         * @return 会員の注文だけの条件
         */
        public static Filter ofMember(UUID memberId) {
            return new Filter(memberId, null, null, null, null, null);
        }
    }
}
