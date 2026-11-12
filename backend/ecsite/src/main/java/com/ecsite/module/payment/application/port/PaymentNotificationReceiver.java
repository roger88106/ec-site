package com.ecsite.module.payment.application.port;

/**
 * 決済サービスの通知の port
 *
 * <p>通知は重ねて届き、順序が入れ替わるので、何度受けても結果が変わらないように処理する。
 */
public interface PaymentNotificationReceiver {

    /**
     * 与信の成功を受ける。
     *
     * @param pageId           支払画面 ID
     * @param authorizationRef 与信の参照
     */
    void authorized(String pageId, String authorizationRef);

    /**
     * 支払画面の期限切れを受ける。
     *
     * @param pageId 支払画面 ID
     */
    void pageExpired(String pageId);
}
