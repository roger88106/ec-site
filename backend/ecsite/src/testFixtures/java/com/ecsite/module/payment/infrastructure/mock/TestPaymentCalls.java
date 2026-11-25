package com.ecsite.module.payment.infrastructure.mock;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway.Operation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * テストで決済サービスに対して呼ばれた操作の記録
 *
 * <p>何を、いつ記録するかは決めず、渡されたものを記録して読み取りに答える。複数のスレッドから呼んでよい。
 */
final class TestPaymentCalls {

    /**
     * 呼ばれた金額のある操作
     *
     * <p>呼ばれた順。失敗と重複を含む。
     */
    private final List<AmountCall> amountCalls = new CopyOnWriteArrayList<>();

    /**
     * 作った支払画面の ID
     *
     * <p>作った順。
     */
    private final List<String> openedPageIds = new CopyOnWriteArrayList<>();

    /**
     * 画面を閉じる操作に渡された支払画面 ID
     *
     * <p>呼ばれた順。失敗と重複を含む。
     */
    private final List<String> closedPageCalls = new CopyOnWriteArrayList<>();

    /**
     * 与信の取得に渡された支払画面 ID
     *
     * <p>呼ばれた順。失敗と重複を含む。
     */
    private final List<String> authorizationFetches = new CopyOnWriteArrayList<>();

    /**
     * 売上確定の状態の取得に渡された与信の参照
     *
     * <p>呼ばれた順。失敗を含む。
     */
    private final List<String> captureStateFetches = new CopyOnWriteArrayList<>();

    /**
     * 取り消した与信の参照
     *
     * <p>取り消した順。
     */
    private final List<String> voided = new CopyOnWriteArrayList<>();

    /**
     * 売上を確定した注文 ID
     *
     * <p>確定した順。
     */
    private final CopyOnWriteArrayList<UUID> captured = new CopyOnWriteArrayList<>();

    /**
     * 売上確定に渡された与信の参照
     *
     * <p>注文 ID ごとに、最後に渡されたもの。
     */
    private final Map<UUID, String> capturedAuthorizationRefs = new ConcurrentHashMap<>();

    /**
     * 返金した注文 ID
     *
     * <p>返金した順。
     */
    private final CopyOnWriteArrayList<UUID> refunded = new CopyOnWriteArrayList<>();

    // ============================== 記録 ==============================

    /**
     * 金額のある操作が呼ばれたことを記録する。
     *
     * @param operation 操作
     * @param orderId   注文 ID
     * @param amount    渡された金額
     */
    void recordAmount(Operation operation, UUID orderId, Money amount) {
        amountCalls.add(new AmountCall(operation, orderId, amount));
    }

    /**
     * 支払画面を作ったことを記録する。
     *
     * @param pageId 作った支払画面 ID
     */
    void recordOpenedPage(String pageId) {
        openedPageIds.add(pageId);
    }

    /**
     * 画面を閉じる操作が呼ばれたことを記録する。
     *
     * @param pageId 支払画面 ID
     */
    void recordClose(String pageId) {
        closedPageCalls.add(pageId);
    }

    /**
     * 与信の取得が呼ばれたことを記録する。
     *
     * @param pageId 支払画面 ID
     */
    void recordAuthorizationFetch(String pageId) {
        authorizationFetches.add(pageId);
    }

    /**
     * 売上確定の状態の取得が呼ばれたことを記録する。
     *
     * @param authorizationRef 与信の参照
     */
    void recordCaptureStateFetch(String authorizationRef) {
        captureStateFetches.add(authorizationRef);
    }

    /**
     * 与信を取り消したことを記録する。
     *
     * @param authorizationRef 与信の参照
     */
    void recordVoid(String authorizationRef) {
        voided.add(authorizationRef);
    }

    /**
     * 売上を確定したことを記録する。
     *
     * @param orderId          注文 ID
     * @param authorizationRef 売上確定に渡された与信の参照
     */
    void recordCapture(UUID orderId, String authorizationRef) {
        capturedAuthorizationRefs.put(orderId, authorizationRef);
        // 注文 ID が同じなら、確定は 1 回だけ（本番の決済サービスで、確定済みとして拒否されたら成功とする扱いに合わせる）
        captured.addIfAbsent(orderId);
    }

    /**
     * 返金したことを記録する。
     *
     * @param orderId 注文 ID
     */
    void recordRefund(UUID orderId) {
        // 注文 ID が同じなら、返金は 1 回だけ（本番の決済サービスで、返金済みとして拒否されたら成功とする扱いに合わせる）
        refunded.addIfAbsent(orderId);
    }

    // ============================== 読み取り ==============================

    /**
     * 売上を確定した注文 ID を返す。
     *
     * @return 売上を確定した注文 ID（確定した順。同じ注文は 1 回）
     */
    List<UUID> captured() {
        return List.copyOf(captured);
    }

    /**
     * 売上確定を呼ばれた回数を返す。
     *
     * @return 売上確定を呼ばれた回数（失敗と重複を含む）
     */
    int captureCalls() {
        return (int) amountCalls.stream().filter(call -> call.operation() == Operation.CAPTURE).count();
    }

    /**
     * 注文について、売上確定を呼ばれた回数を返す。
     *
     * @param orderId 注文 ID
     * @return 売上確定を呼ばれた回数（失敗と重複を含む）
     */
    int captureCallsOf(UUID orderId) {
        return amountsOf(Operation.CAPTURE, orderId).size();
    }

    /**
     * 注文の売上確定に渡された与信の参照を返す。
     *
     * @param orderId 注文 ID
     * @return 与信の参照。売上確定を呼ばれていなければ空
     */
    Optional<String> capturedAuthorizationRef(UUID orderId) {
        return Optional.ofNullable(capturedAuthorizationRefs.get(orderId));
    }

    /**
     * 与信の取得を呼ばれた回数を返す。
     *
     * @return 与信の取得を呼ばれた回数（失敗を含む）
     */
    int fetchAuthorizationCalls() {
        return authorizationFetches.size();
    }

    /**
     * 支払画面について、画面を閉じる操作を呼ばれた回数を返す。
     *
     * @param pageId 支払画面 ID
     * @return 画面を閉じる操作を呼ばれた回数（失敗と重複を含む）
     */
    int closeCallsOf(String pageId) {
        return (int) closedPageCalls.stream().filter(pageId::equals).count();
    }

    /**
     * 売上確定の状態の取得を呼ばれた回数を返す。
     *
     * @return 売上確定の状態の取得を呼ばれた回数（失敗を含む）
     */
    int fetchCaptureStateCalls() {
        return captureStateFetches.size();
    }

    /**
     * 与信について、売上確定の状態の取得を呼ばれた回数を返す。
     *
     * @param authorizationRef 与信の参照
     * @return 売上確定の状態の取得を呼ばれた回数（失敗を含む）
     */
    int fetchCaptureStateCallsOf(String authorizationRef) {
        return (int) captureStateFetches.stream().filter(authorizationRef::equals).count();
    }

    /**
     * 返金した注文 ID を返す。
     *
     * @return 返金した注文 ID（返金した順。同じ注文は 1 回）
     */
    List<UUID> refunded() {
        return List.copyOf(refunded);
    }

    /**
     * 注文について、返金した回数を返す。
     *
     * @param orderId 注文 ID
     * @return 返金した回数（同じ注文は 1 回）
     */
    int refundsOf(UUID orderId) {
        return (int) refunded.stream().filter(orderId::equals).count();
    }

    /**
     * 取り消した与信の参照を返す。
     *
     * @return 取り消した与信の参照（取り消した順）
     */
    List<String> voided() {
        return List.copyOf(voided);
    }

    /**
     * 与信について、取り消した回数を返す。
     *
     * @param authorizationRef 与信の参照
     * @return 取り消した回数
     */
    int voidsOf(String authorizationRef) {
        return (int) voided.stream().filter(authorizationRef::equals).count();
    }

    /**
     * 注文について、決済サービスに渡した金額を、呼ばれた順に返す。
     *
     * @param operation 操作（支払画面を開く、売上確定、返金）
     * @param orderId   注文 ID
     * @return 渡した金額（呼ばれた順。失敗と重複を含む）
     */
    List<Money> amountsOf(Operation operation, UUID orderId) {
        return amountCalls.stream()
                .filter(call -> call.operation() == operation && call.orderId().equals(orderId))
                .map(AmountCall::amount)
                .toList();
    }

    /**
     * 作った支払画面の数を返す。
     *
     * @return 作った支払画面の数
     */
    int openedPages() {
        return openedPageIds.size();
    }

    /**
     * 作った支払画面の ID を返す。
     *
     * @return 作った順の支払画面 ID
     */
    List<String> openedPageIds() {
        return List.copyOf(openedPageIds);
    }

    /**
     * 呼ばれた金額のある操作
     *
     * @param operation 操作
     * @param orderId   注文 ID
     * @param amount    渡された金額
     */
    private record AmountCall(Operation operation, UUID orderId, Money amount) {
    }
}
