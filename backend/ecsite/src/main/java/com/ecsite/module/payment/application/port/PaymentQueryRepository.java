package com.ecsite.module.payment.application.port;

import com.ecsite.module.payment.application.model.view.PaymentSummaryView;

import java.time.Instant;
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

    /**
     * 与信期限が近い注文 ID を、与信の古い順に取得する。
     *
     * @param latestAuthorizedAt 与信の時刻の上限。この時刻以前に与信を受けたものが対象
     * @return 注文 ID。与信済みと与信期限切れの支払だけ
     */
    List<UUID> findOrderIdsAuthorizedUntil(Instant latestAuthorizedAt);

    /**
     * 与信の通知を待つ未払いの支払のうち、決めた時刻までに待ち始めたものを数える。
     *
     * @param latestAwaitingSince 待ち始めた時刻の上限。この時刻以前に待ち始めたものが対象
     * @return 支払の数
     */
    long countUnpaidAwaitingAuthorizationUntil(Instant latestAwaitingSince);
}
