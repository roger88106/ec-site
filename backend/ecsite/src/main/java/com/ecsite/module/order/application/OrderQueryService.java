package com.ecsite.module.order.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.value.BusinessTime;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.order.application.model.view.OrderDetailView;
import com.ecsite.module.order.application.model.view.OrderPayment;
import com.ecsite.module.order.application.model.view.OrderShipment;
import com.ecsite.module.order.application.model.view.OrderSummaryView;
import com.ecsite.module.order.application.port.OrderQueryRepository;
import com.ecsite.module.order.application.port.PaymentDirectory;
import com.ecsite.module.order.application.port.ShippingDirectory;
import com.ecsite.module.order.domain.order.Order;
import com.ecsite.module.order.domain.order.OrderStatus;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 注文の照会のサービス
 *
 * <p>支払と出荷は、決済・出荷の API から読む。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

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
     * 注文の詳細を取得する。
     *
     * @param orderId 注文 ID
     * @return 注文の詳細
     * @throws BusinessException 注文がない（{@code NOT_FOUND}）
     */
    public OrderDetailView get(@NonNull UUID orderId) {
        return toDetail(orderRepository.findById(orderId)
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND)));
    }

    /**
     * 会員の注文の詳細を取得する。
     *
     * @param orderId  注文 ID
     * @param memberId リクエストした会員の ID
     * @return 注文の詳細
     * @throws BusinessException 注文がない（{@code NOT_FOUND}）
     * @throws BusinessException 注文した会員でない（{@code NOT_FOUND}）
     */
    public OrderDetailView getForMember(@NonNull UUID orderId, @NonNull UUID memberId) {
        // 注文した会員でなければ、ないときと同じにする
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
    public PageResult<OrderSummaryView> listForMember(@NonNull UUID memberId, @NonNull PageRequest request) {
        return orderQueryRepository.findOrders(OrderQueryRepository.Filter.ofMember(memberId), request);
    }

    /**
     * 会員の支払待ちの注文の詳細を、注文日時の新しい順に全て取得する。
     *
     * <p>支払の期限を過ぎ、まだキャンセルされていない注文も含む。
     *
     * @param memberId 会員 ID
     * @return 注文の詳細
     */
    public List<OrderDetailView> listPendingForMember(@NonNull UUID memberId) {
        // 支払待ちの注文 ID と、その支払・出荷の取得
        var orderIds = orderRepository.findPendingIdsByMemberId(memberId);
        var payments = findPayments(orderIds);
        var shipments = findShipments(orderIds);

        // 注文の取得と詳細の組み立て ※ID を読んだ後に受付やキャンセルになった注文は、支払待ちの一覧に含めない
        var details = new ArrayList<OrderDetailView>();
        for (var orderId : orderIds) {
            orderRepository.findById(orderId)
                    .filter(Order::isAwaitingPayment)
                    .ifPresent(order -> details.add(toDetail(order, payments.get(order.id()), shipments.get(order.id()))));
        }
        return List.copyOf(details);
    }

    /**
     * 注文を、注文日時の新しい順に一覧する。
     *
     * <p>注文日の範囲は日本時間の日付で、両端を含む。
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
    public PageResult<OrderSummaryView> list(@Nullable String orderNumberPrefix,
                                             @Nullable OrderStatus status,
                                             @Nullable LocalDate orderedFrom, @Nullable LocalDate orderedTo,
                                             boolean authorizationExpiring, boolean returned,
                                             @NonNull PageRequest request) {
        // 与信期限が近い・返送の絞り込み ※決済・出荷の API から注文 ID を受け取り、ID の条件にする（他のモジュールのテーブルは結合しない）
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

        // 注文の検索
        var filter = new OrderQueryRepository.Filter(null, orderNumberPrefix, status, startOf(orderedFrom),
                orderedTo == null ? null : startOf(orderedTo.plusDays(1)), orderIds);
        return orderQueryRepository.findOrders(filter, request);
    }

    /**
     * 注文の支払をまとめて取得する。
     *
     * <p>支払えるか（未払いで、支払の期限の前）は決済が判定する。
     *
     * @param orderIds 注文 ID
     * @return 注文 ID → 支払。支払のない注文（決済を入れる前の注文）は含まない
     */
    public Map<UUID, OrderPayment> findPayments(@NonNull Collection<UUID> orderIds) {
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
    public Map<UUID, OrderShipment> findShipments(@NonNull Collection<UUID> orderIds) {
        return shippingDirectory.find(orderIds);
    }

    /**
     * 注文の支払と出荷を読み、詳細の表示にする。
     */
    private OrderDetailView toDetail(Order order) {
        var ids = List.of(order.id());
        return toDetail(order, findPayments(ids).get(order.id()), findShipments(ids).get(order.id()));
    }

    /**
     * 注文を、読んだ支払と出荷を合わせた詳細の表示にする。
     */
    private static OrderDetailView toDetail(Order order, @Nullable OrderPayment payment, @Nullable OrderShipment shipment) {
        // 操作の可否は注文の判定を使う
        var awaitingPayment = order.isAwaitingPayment() && payment != null;
        var returned = shipment != null && shipment.isReturned();
        var actions = new OrderDetailView.Actions(order.isCancellationRequestable(),
                payment != null && awaitingPayment && payment.payable(), order.isCancellationPending(),
                order.isCancellableByOperator(returned), order.isShippable(), order.asksRestockOnCancel(returned));
        return new OrderDetailView(order.id(), order.orderNumber(), order.orderedAt(), order.status(), order.lines(),
                order.amounts(), order.email(), order.shippingAddress(), order.cancellationRequest(),
                order.cancellation(), payment, shipment, awaitingPayment, actions);
    }

    /**
     * 日本時間の日付の始まりの時刻。
     */
    @Contract("null -> null; !null -> !null")
    private static @Nullable Instant startOf(@Nullable LocalDate date) {
        return date == null ? null : date.atStartOfDay(BusinessTime.ZONE).toInstant();
    }
}
