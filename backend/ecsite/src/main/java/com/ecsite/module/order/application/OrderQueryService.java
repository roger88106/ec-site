package com.ecsite.module.order.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.order.application.model.OrderDetailView;
import com.ecsite.module.order.application.model.OrderPayment;
import com.ecsite.module.order.application.port.OrderQueryRepository;
import com.ecsite.module.order.application.port.PaymentDirectory;
import com.ecsite.module.order.application.port.ShippingDirectory;
import com.ecsite.module.order.domain.order.Order;
import com.ecsite.module.order.domain.order.OrderStatus;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 注文の照会のサービス。支払と出荷は、決済・出荷の API から読む。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    /**
     * 日本時間（注文日の範囲の解釈）
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
     * 決済の腐敗防止層
     */
    private final PaymentDirectory paymentDirectory;

    /**
     * 出荷の腐敗防止層
     */
    private final ShippingDirectory shippingDirectory;

    /**
     * 注文の詳細を取得する（運営者用）。
     *
     * @param orderId 注文 ID
     * @return 注文の詳細
     */
    public OrderDetailView get(UUID orderId) {
        return toDetail(orderRepository.findById(orderId)
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND)));
    }

    /**
     * 会員の注文の詳細を取得する。注文した会員でなければ、ないときと同じく 404。
     *
     * @param orderId  注文 ID
     * @param memberId 要求した会員の ID
     * @return 注文の詳細
     */
    public OrderDetailView getForMember(UUID orderId, UUID memberId) {
        return toDetail(orderRepository.findById(orderId)
                .filter(order -> order.memberId().equals(memberId))
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND)));
    }

    /**
     * 会員の注文を、注文日時の新しい順に一覧する。
     *
     * @param memberId 会員 ID
     * @param request  ページの指定
     * @return 注文の一覧
     */
    public PageResult<OrderQueryRepository.OrderSummaryView> listForMember(UUID memberId, PageRequest request) {
        return orderQueryRepository.findOrders(OrderQueryRepository.Filter.ofMember(memberId), request);
    }

    /**
     * 注文を、注文日時の新しい順に一覧する。注文日の範囲は日本時間の日付で、両端を含む。<br />
     * 与信期限が近い・返送の絞り込みは、決済・出荷の API から注文 ID を受け取り、ID の条件にする（他のモジュールのテーブルは結合しない）。
     *
     * @param orderNumberPrefix     注文番号の前方一致。なければ null
     * @param status                状態。なければ null
     * @param orderedFrom           注文日の範囲の開始。なければ null
     * @param orderedTo             注文日の範囲の終了。なければ null
     * @param authorizationExpiring true なら、与信期限が近い注文だけ
     * @param returned              true なら、配送状態が返送の注文だけ
     * @param request               ページの指定
     * @return 注文の一覧
     */
    public PageResult<OrderQueryRepository.OrderSummaryView> list(String orderNumberPrefix, OrderStatus status,
                                                                  LocalDate orderedFrom, LocalDate orderedTo,
                                                                  boolean authorizationExpiring, boolean returned,
                                                                  PageRequest request) {
        Set<UUID> orderIds = null;
        if (authorizationExpiring) {
            // 与信期限が近いのは「受付」の注文だけ（キャンセル済みの注文は、与信期限切れの支払が残っても除く）
            orderIds = status == null || status == OrderStatus.ACCEPTED
                    ? new HashSet<>(paymentDirectory.findExpiringOrderIds())
                    : new HashSet<>();
            status = OrderStatus.ACCEPTED;
        }
        if (returned) {
            var returnedIds = shippingDirectory.returnedOrderIds();
            if (orderIds == null) {
                orderIds = new HashSet<>(returnedIds);
            } else {
                orderIds.retainAll(returnedIds);
            }
        }
        var filter = new OrderQueryRepository.Filter(null, orderNumberPrefix, status, startOf(orderedFrom),
                orderedTo == null ? null : startOf(orderedTo.plusDays(1)), orderIds);
        return orderQueryRepository.findOrders(filter, request);
    }

    /**
     * 注文の支払をまとめて取得する。支払えるか（未払いで、支払の期限の前）は決済が判定する。
     *
     * @param orderIds 注文 ID
     * @return 注文 ID → 支払。支払のない注文（決済を入れる前の注文）は含まない
     */
    public Map<UUID, OrderPayment> findPayments(Collection<UUID> orderIds) {
        var authorizations = paymentDirectory.findAuthorizations(orderIds);
        var payments = new HashMap<UUID, OrderPayment>();
        paymentDirectory.find(orderIds).forEach((orderId, payment) -> {
            var authorization = authorizations.get(orderId);
            payments.put(orderId, new OrderPayment(payment.status(), payment.deadline(), payment.payable(),
                    authorization == null ? null : authorization.deadline(),
                    authorization != null && authorization.expiring()));
        });
        return payments;
    }

    /**
     * 注文の出荷をまとめて取得する。
     *
     * @param orderIds 注文 ID
     * @return 注文 ID → 出荷。出荷のない注文は含まない
     */
    public Map<UUID, ShippingDirectory.Shipment> findShipments(Collection<UUID> orderIds) {
        return shippingDirectory.find(orderIds);
    }

    /**
     * 注文を、支払と出荷を合わせた詳細の表示にする。操作の可否は注文の判定を使う。
     */
    private OrderDetailView toDetail(Order order) {
        var ids = List.of(order.id());
        var payment = findPayments(ids).get(order.id());
        var shipment = findShipments(ids).get(order.id());
        var awaitingPayment = order.isAwaitingPayment() && payment != null;
        var returned = shipment != null && shipment.isReturned();
        var actions = new OrderDetailView.Actions(order.isCancellationRequestable(),
                awaitingPayment && payment.payable(), order.isCancellationPending(),
                order.isCancellableByOperator(returned), order.isShippable());
        return new OrderDetailView(order.id(), order.orderNumber(), order.orderedAt(), order.status(), order.lines(),
                order.amounts(), order.email(), order.shippingAddress(), order.cancellationRequest(),
                order.cancellation(), payment, shipment, awaitingPayment, actions);
    }

    /**
     * 日本時間の日付の始まりの時刻。
     */
    private static Instant startOf(LocalDate date) {
        return date == null ? null : date.atStartOfDay(JAPAN).toInstant();
    }
}
