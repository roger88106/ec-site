package com.ecsite.module.order.application;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.application.model.view.OrderReportView;
import com.ecsite.module.order.domain.order.Order;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import com.ecsite.module.order.domain.quote.Quote;
import com.ecsite.module.order.domain.quote.port.TaxDirectory;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 注文の写しの参照のサービス
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderReportQueryService {

    /**
     * 注文リポジトリ
     */
    private final OrderRepository orderRepository;

    /**
     * 税の腐敗防止層
     */
    private final TaxDirectory taxDirectory;

    /**
     * 注文の設定（送料の税区分）
     */
    private final OrderProperties properties;

    /**
     * 注文と、送料の消費税を読む。
     *
     * @param orderId 注文 ID
     * @return 注文と送料の消費税。注文がなければ空
     */
    public Optional<OrderReportView> find(@NonNull UUID orderId) {
        return orderRepository.findById(orderId).map(this::toView);
    }

    /**
     * 注文を、写しの表示にする。
     */
    private OrderReportView toView(Order order) {
        return new OrderReportView(order.id(), order.orderNumber(), order.memberId(), order.orderedAt(),
                order.status(), order.cancellation(), order.shippingAddress(), order.buyerProfile(),
                order.amounts(), shippingTax(order), order.lines());
    }

    /**
     * 送料の消費税を求める。
     *
     * <p>注文の成立のときに保存した額があれば返し、なければ求める。求めるときは、送料がなければ 0。
     */
    private Money shippingTax(Order order) {
        var saved = order.shippingTax();
        if (saved != null) {
            return saved;
        }
        var fee = order.amounts().shippingFee();
        if (fee.yen() == 0) {
            return Money.ZERO;
        }
        // 送料の消費税を保存する前に成立した注文のための計算。税率は、設定にある今の送料の税区分から、注文日時のものを使う
        // ※税区分の設定や税率のマスターを変えると、値が変わる
        var rate = taxDirectory.rateAt(properties.shippingTaxCategoryId(), order.orderedAt())
                .orElseThrow(() -> SystemException.of("送料の税区分に、注文日時の税率がありません: " + order.id(), null));
        return Quote.shippingTaxOf(fee, rate, taxDirectory);
    }
}
