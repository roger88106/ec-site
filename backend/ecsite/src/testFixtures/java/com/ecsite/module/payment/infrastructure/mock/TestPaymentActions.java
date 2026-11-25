package com.ecsite.module.payment.infrastructure.mock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * テストで決済サービスの操作の途中に差し込む処理
 *
 * <p>処理を行う時点ごとに、次の 1 回だけ行う処理と、呼ばれるたびに行う処理を持つ。
 * 処理の例外はそのまま呼び出し元に伝える。複数のスレッドから呼んでよい。
 */
final class TestPaymentActions {

    /**
     * 時点ごとの、次の 1 回だけ行う処理
     */
    private final Map<Timing, Runnable> onceActions = new ConcurrentHashMap<>();

    /**
     * 時点ごとの、呼ばれるたびに行う処理
     */
    private final Map<Timing, Runnable> alwaysActions = new ConcurrentHashMap<>();

    // ============================== 処理の設定 ==============================

    /**
     * その時点で次に 1 回だけ行う処理を設定する。
     *
     * <p>前に設定した、まだ行っていない処理は置き換える。
     *
     * @param timing 処理を行う時点
     * @param action 行う処理
     */
    void once(Timing timing, Runnable action) {
        onceActions.put(timing, action);
    }

    /**
     * その時点で呼ばれるたびに行う処理を設定する。
     *
     * <p>前に設定した処理は置き換える。
     *
     * @param timing 処理を行う時点
     * @param action 行う処理
     */
    void always(Timing timing, Runnable action) {
        alwaysActions.put(timing, action);
    }

    // ============================== 処理の実行 ==============================

    /**
     * その時点に設定された処理を行う。
     *
     * <p>次の 1 回だけの処理は、先に取り除いてから行うので、処理の中から同じ時点を呼んでも、同時に呼んでも 1 回だけ行う。
     * 両方が設定されていれば、次の 1 回だけの処理、呼ばれるたびの処理の順に行う。
     *
     * @param timing 処理を行う時点
     */
    void run(Timing timing) {
        var once = onceActions.remove(timing);
        if (once != null) {
            once.run();
        }
        var always = alwaysActions.get(timing);
        if (always != null) {
            always.run();
        }
    }

    /**
     * 処理を行う時点
     */
    enum Timing {

        /**
         * 支払画面を作った後
         */
        AFTER_OPEN,

        /**
         * 売上を確定した後
         */
        AFTER_CAPTURE,

        /**
         * 売上確定の状態を読んだ後
         */
        AFTER_CAPTURE_STATE_FETCH,

        /**
         * 返金と与信の取消を呼ばれたとき
         */
        ON_REFUND_OR_VOID,

        /**
         * 与信の取得を呼ばれたとき
         */
        ON_FETCH_AUTHORIZATION
    }
}
