package com.ecsite.module.payment.application.port;

import com.ecsite.module.payment.application.model.view.PaymentSummaryView;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 支払のクエリリポジトリ
 */
public interface PaymentQueryRepository {

    /**
     * 注文の支払の状態をまとめて取得する。
     *
     * @param orderIds 注文 ID
     * @return 支払の状態。支払のない注文は含まない
     */
    List<PaymentSummaryView> findSummaries(Collection<UUID> orderIds);
}
