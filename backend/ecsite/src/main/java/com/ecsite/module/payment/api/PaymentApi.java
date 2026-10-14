package com.ecsite.module.payment.api;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.application.PaymentCommandService;
import com.ecsite.module.payment.application.PaymentPageService;
import com.ecsite.module.payment.application.PaymentQueryService;
import com.ecsite.module.payment.application.model.view.PaymentSummaryView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 注文に公開する、支払の API（docs/backend/modules/payment.md「他のモジュールへの公開」）。<br />
 * 支払の作成と取消は、呼び出し元（注文）のトランザクションに参加し、決済サービスを呼ばない。
 */
@Component
@RequiredArgsConstructor
public class PaymentApi {

    /**
     * 支払の更新のサービス
     */
    private final PaymentCommandService commandService;

    /**
     * 支払の画面のサービス
     */
    private final PaymentPageService pageService;

    /**
     * 支払の参照のサービス
     */
    private final PaymentQueryService queryService;

    /**
     * 注文の支払を未払いで作る。支払の期限は、注文日時に設定の時間を足した時刻。同じ注文で重ねて呼んでも 1 つ。
     *
     * @param orderId   注文 ID
     * @param amount    金額（注文の総額）
     * @param orderedAt 注文日時
     */
    public void create(UUID orderId, Money amount, Instant orderedAt) {
        commandService.create(orderId, amount, orderedAt);
    }

    /**
     * 支払の画面の URL を返す。開いている画面があれば同じ画面を返す。決済サービスを呼ぶので、トランザクションの外で呼ぶ。
     *
     * @param orderId 注文 ID
     * @return 支払の画面の URL。支払がない、未払いでない、期限の後なら空
     */
    public Optional<String> pageUrl(UUID orderId) {
        return pageService.pageUrl(orderId);
    }

    /**
     * 注文のキャンセルに合わせて支払を取り消す。与信済みなら与信の取消待ちにし、決済サービスでの取消はコミットの後に行う。<br />
     * 取消の結果を待たないので、決済サービスが応えなくても注文のキャンセルは止まらない。何度呼んでも結果は同じ。
     *
     * @param orderId 注文 ID
     */
    public void cancel(UUID orderId) {
        commandService.cancel(orderId);
    }

    /**
     * 注文の支払の売上を確定する。決済サービスを呼ぶので、トランザクションの外で呼ぶ。<br />
     * 売上確定済みなら、決済サービスを呼ばずに成功する。
     *
     * @param orderId 注文 ID
     */
    public void capture(UUID orderId) {
        // T9107 で実装する
    }

    /**
     * 注文の支払の状態をまとめて返す。
     *
     * @param orderIds 注文 ID
     * @return 支払の状態。支払のない注文は含まない
     */
    public List<PaymentSummary> find(Collection<UUID> orderIds) {
        return queryService.findSummaries(orderIds).stream().map(PaymentApi::toSummary).toList();
    }

    /**
     * 支払の状態を公開の型にする。
     */
    private static PaymentSummary toSummary(PaymentSummaryView view) {
        return new PaymentSummary(view.orderId(), PaymentSummary.Status.valueOf(view.status().name()), view.deadline());
    }
}
