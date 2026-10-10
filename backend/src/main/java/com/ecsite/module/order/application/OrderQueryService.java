package com.ecsite.module.order.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.order.domain.order.Order;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 注文の照会のサービス
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
     * 注文を取得する（AC-93、94、100）。
     *
     * @param orderId 注文 ID
     * @return 注文
     */
    public Order get(UUID orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
    }
}
