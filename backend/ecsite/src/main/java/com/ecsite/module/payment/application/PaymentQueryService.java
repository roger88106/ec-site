package com.ecsite.module.payment.application;

import com.ecsite.module.payment.application.model.view.AuthorizationDeadlineView;
import com.ecsite.module.payment.application.model.view.PaymentSummaryView;
import com.ecsite.module.payment.application.port.PaymentQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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
     * 決済の設定
     */
    private final PaymentProperties properties;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 注文の支払の状態をまとめて取得する。
     *
     * @param orderIds 注文 ID
     * @return 支払の状態。支払のない注文は含まない
     */
    public List<PaymentSummaryView> findSummaries(Collection<UUID> orderIds) {
        return queryRepository.findSummaries(orderIds);
    }

    /**
     * 注文の与信期限と、期限が近いかをまとめて取得する。
     *
     * @param orderIds 注文 ID
     * @return 与信期限。与信を受けていない注文は含まない
     */
    public List<AuthorizationDeadlineView> findAuthorizationDeadlines(Collection<UUID> orderIds) {
        var term = properties.authorizationTerm();
        var now = clock.instant();
        return queryRepository.findAuthorizations(orderIds).stream()
                .map(view -> new AuthorizationDeadlineView(
                        view.orderId(),
                        term.deadlineOf(view.authorizedAt()),
                        view.status().watchesAuthorizationDeadline() && term.isExpiring(view.authorizedAt(), now)))
                .toList();
    }

    /**
     * 与信期限が近い注文 ID を、与信の古い順に取得する。期限を過ぎたものを含む。
     *
     * @return 注文 ID
     */
    public List<UUID> findExpiringOrderIds() {
        var latest = properties.authorizationTerm().latestExpiringAuthorizedAt(clock.instant());
        return queryRepository.findOrderIdsAuthorizedUntil(latest);
    }
}
