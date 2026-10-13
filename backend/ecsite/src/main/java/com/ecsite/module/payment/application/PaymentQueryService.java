package com.ecsite.module.payment.application;

import com.ecsite.module.payment.application.model.view.PaymentSummaryView;
import com.ecsite.module.payment.application.port.PaymentQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 支払の参照のサービス
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PaymentQueryService {

    /**
     * 支払のクエリリポジトリ
     */
    private final PaymentQueryRepository queryRepository;

    /**
     * 注文の支払の状態をまとめて取得する。
     *
     * @param orderIds 注文 ID
     * @return 支払の状態。支払のない注文は含まない
     */
    public List<PaymentSummaryView> findSummaries(Collection<UUID> orderIds) {
        return queryRepository.findSummaries(orderIds);
    }
}
