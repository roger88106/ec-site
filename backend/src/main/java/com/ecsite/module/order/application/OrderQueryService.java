package com.ecsite.module.order.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.order.domain.order.Order;
import com.ecsite.module.order.domain.order.OrderStatus;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 注文の照会のサービス
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    /**
     * 日本時間（注文日の範囲の解釈。BR-3）
     */
    private static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");

    /**
     * 注文リポジトリ
     */
    private final OrderRepository orderRepository;

    /**
     * 注文の一覧のクエリリポジトリ
     */
    private final OrderQueryRepository orderQueryRepository;

    /**
     * 注文を取得する（AC-93、94、100）。
     *
     * @param orderId 注文 ID
     * @return 注文
     */
    public Order get(UUID orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
    }

    /**
     * 注文を、注文日時の新しい順に一覧する（AC-97〜99、106）。注文日の範囲は日本時間の日付で、両端を含む（BR-3）。
     *
     * @param orderNumberPrefix 注文番号の前方一致。なければ null
     * @param status            状態。なければ null
     * @param orderedFrom       注文日の範囲の開始。なければ null
     * @param orderedTo         注文日の範囲の終了。なければ null
     * @param request           ページの指定
     * @return 注文の一覧
     */
    public PageResult<OrderQueryRepository.OrderSummaryView> list(String orderNumberPrefix, OrderStatus status,
                                                                  LocalDate orderedFrom, LocalDate orderedTo,
                                                                  PageRequest request) {
        var filter = new OrderQueryRepository.Filter(orderNumberPrefix, status, startOf(orderedFrom),
                orderedTo == null ? null : startOf(orderedTo.plusDays(1)));
        return orderQueryRepository.findOrders(filter, request);
    }

    /**
     * 日本時間の日付の始まりの時刻。
     */
    private static Instant startOf(LocalDate date) {
        return date == null ? null : date.atStartOfDay(JAPAN).toInstant();
    }
}
