package com.ecsite.module.payment.infrastructure.mock;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * テストで決済サービスの操作を失敗させる指定
 *
 * <p>回数で指定する失敗は種類ごとに残りの回数を持ち、拒否する支払画面と期限切れの与信は指定したものを持つ。
 * 例外や結果の選択は持たず、失敗させるかどうかだけを答える。複数のスレッドから呼んでよい。
 */
final class TestPaymentFailures {

    /**
     * 種類ごとの、これから失敗させる残りの回数
     */
    private final Map<Kind, Integer> remaining = new ConcurrentHashMap<>();

    /**
     * 与信の取得を、予期しないエラーで失敗させ続ける支払画面 ID
     */
    private final Set<String> rejectedAuthorizationFetches = ConcurrentHashMap.newKeySet();

    /**
     * 与信が切れたことにした与信の参照
     */
    private final Set<String> expiredAuthorizations = ConcurrentHashMap.newKeySet();

    // ============================== 回数で指定する失敗 ==============================

    /**
     * 次の操作を、指定した回数だけ失敗させる。
     *
     * <p>前に指定した残りの回数には加えず、置き換える。0 以下なら失敗させない。
     *
     * @param kind  失敗の種類
     * @param times 失敗させる回数
     */
    void failNext(Kind kind, int times) {
        remaining.put(kind, times);
    }

    /**
     * 残りの回数があれば 1 つ減らす。
     *
     * <p>同時に呼ばれても、失敗させる回数は指定した回数ちょうどになる。
     *
     * @param kind 失敗の種類
     * @return 残りがあった（今回を失敗させる）なら true
     */
    boolean consume(Kind kind) {
        var consumed = new boolean[1];
        remaining.compute(kind, (key, count) -> {
            if (count == null || count <= 0) {
                return count;
            }
            consumed[0] = true;
            return count - 1;
        });
        return consumed[0];
    }

    // ============================== 拒否する支払画面 ==============================

    /**
     * その支払画面の与信の取得を、失敗させ続ける。
     *
     * @param pageId 支払画面 ID
     */
    void rejectAuthorizationFetch(String pageId) {
        rejectedAuthorizationFetches.add(pageId);
    }

    /**
     * その支払画面の与信の取得を、失敗させ続けることにしているかを返す。
     *
     * @param pageId 支払画面 ID
     * @return 失敗させ続けることにしているなら true
     */
    boolean isAuthorizationFetchRejected(String pageId) {
        return rejectedAuthorizationFetches.contains(pageId);
    }

    // ============================== 期限切れの与信 ==============================

    /**
     * 与信が切れたことにする。
     *
     * @param authorizationRef 与信の参照
     */
    void expireAuthorization(String authorizationRef) {
        expiredAuthorizations.add(authorizationRef);
    }

    /**
     * 与信が切れたことにしているかを返す。
     *
     * @param authorizationRef 与信の参照
     * @return 切れたことにしているなら true
     */
    boolean isExpired(String authorizationRef) {
        return expiredAuthorizations.contains(authorizationRef);
    }

    /**
     * 回数で指定する失敗の種類
     */
    enum Kind {

        /**
         * 支払画面の作成
         */
        OPEN,

        /**
         * 画面を閉じる操作
         */
        CLOSE,

        /**
         * 与信の取得
         */
        AUTHORIZATION_FETCH,

        /**
         * 与信の取消
         */
        VOID,

        /**
         * 売上確定の接続
         */
        CAPTURE_CONNECTION,

        /**
         * 売上確定の応答
         */
        CAPTURE_RESPONSE,

        /**
         * 売上確定の状態の取得
         */
        CAPTURE_STATE_FETCH,

        /**
         * 返金
         */
        REFUND
    }
}
