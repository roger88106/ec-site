package com.ecsite.module.order.application;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.application.model.OrderReportView;
import com.ecsite.module.order.domain.order.Order;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import com.ecsite.module.order.domain.quote.TaxAmount;
import com.ecsite.module.order.domain.quote.port.TaxDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * レポート用に注文を読むサービス（docs/design/modules/order.md「他のモジュールへの公開」）。<br />
 * 注文と、注文の金額のうち送料の消費税を返す。送料の消費税は注文に持たないので、注文日時の送料の税区分の税率で求める
 * （税率ごとの消費税額は、明細と送料を合わせて求めているため）。
 */
@Service
@RequiredArgsConstructor
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
    @Transactional(readOnly = true)
    public Optional<OrderReportView> find(UUID orderId) {
        return orderRepository.findById(orderId).map(order -> new OrderReportView(order, shippingTax(order)));
    }

    /**
     * 送料の消費税を求める。送料がなければ 0。
     */
    private Money shippingTax(Order order) {
        var fee = order.amounts().shippingFee();
        if (fee.yen() == 0) {
            return Money.ZERO;
        }
        var rate = taxDirectory.rateAt(properties.shippingTaxCategoryId(), order.orderedAt())
                .orElseThrow(() -> SystemException.of("送料の税区分に、注文日時の税率がありません: " + order.id(), null));
        return taxDirectory.consumptionTaxes(List.of(new TaxDirectory.Taxable(rate, fee))).stream()
                .map(TaxAmount::taxAmount)
                .reduce(Money.ZERO, Money::plus);
    }
}
