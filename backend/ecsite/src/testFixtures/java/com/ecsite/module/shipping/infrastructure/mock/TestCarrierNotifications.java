package com.ecsite.module.shipping.infrastructure.mock;

import com.ecsite.module.shipping.infrastructure.carrier.CarrierSignature;
import com.ecsite.module.shipping.infrastructure.mock.MockCarrier.SignedNotification;

/**
 * テストだけで使う、物流サービスの通知の作り方（テストで共有するソース。本番の jar に入らない）。<br />
 * 本番の mock は、画面のボタンが使う正しい署名の通知だけを作る。署名の誤りなど、テストで作りたい通知はここで作る。
 */
public final class TestCarrierNotifications {

    /**
     * 誤った署名に使う鍵（mock の鍵と違うもの）
     */
    private static final String WRONG_SECRET = "mock-carrier-wrong-secret";

    /**
     * 作らせない
     */
    private TestCarrierNotifications() {
    }

    /**
     * 署名が誤っている通知を作る（本文は正しいが、署名は別の鍵で作ったもの）。
     *
     * @param valid 正しい署名の通知（{@link MockCarrier#notification} で作ったもの）
     * @return 本文が同じで、署名を確かめられない通知
     */
    public static SignedNotification withInvalidSignature(SignedNotification valid) {
        return new SignedNotification(valid.body(), new CarrierSignature(WRONG_SECRET).sign(valid.body()));
    }
}
