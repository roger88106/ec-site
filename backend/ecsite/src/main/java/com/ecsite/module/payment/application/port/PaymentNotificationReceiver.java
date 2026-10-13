package com.ecsite.module.payment.application.port;

/**
 * 決済サービスからの通知の受け口（docs/backend/modules/payment.md「決済サービス」）。<br />
 * 決済サービスの実装（Stripe の通知の受け口、mock の支払の画面）が、通知を決済サービスの型から直してから渡す。
 * 通知は重ねて届き、順序が入れ替わるので、何度受けても結果が変わらないように処理する。
 */
public interface PaymentNotificationReceiver {

    /**
     * 与信の成功を受ける。
     *
     * @param pageId           支払の画面 ID
     * @param authorizationRef 与信の参照
     */
    void authorized(String pageId, String authorizationRef);

    /**
     * 支払の画面の期限切れを受ける。
     *
     * @param pageId 支払の画面 ID
     */
    void pageExpired(String pageId);
}
