package com.ecsite.module.shipping.application;

import java.util.List;

/**
 * 追跡の開始の依頼を見分ける、統合テストの補助
 *
 * <p>依頼は {@link TrackingRequested} のイベント。
 * イベントのクラスは package-private なので、同じパッケージのこのクラスを通して調べる。
 */
public final class TrackingRequests {

    /**
     * インスタンスを作らない。
     */
    private TrackingRequests() {
    }

    /**
     * 指定した送り状番号のどれかについての、追跡の開始の依頼か。
     *
     * @param event           受け取ったイベント
     * @param trackingNumbers 送り状番号（ハイフンを除いた数字）
     * @return 追跡の開始の依頼で、番号が一致すれば true
     */
    public static boolean isFor(Object event, List<String> trackingNumbers) {
        return event instanceof TrackingRequested requested && trackingNumbers.contains(requested.trackingNumber());
    }
}
