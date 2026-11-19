package com.ecsite.module.notification.application.port;

import java.util.UUID;

/**
 * 注文の腐敗防止層の port
 */
public interface OrderDirectory {

    /**
     * 注文のメールアドレスを返す。
     *
     * @param orderId 注文 ID
     * @return 注文のメールアドレス
     * @throws IllegalStateException 注文が見つからないとき
     */
    String email(UUID orderId);
}
