package com.ecsite.module.notification.infrastructure.directory;

import com.ecsite.common.model.id.OrderId;
import com.ecsite.module.notification.application.port.OrderDirectory;
import com.ecsite.module.order.api.OrderApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 注文の腐敗防止層
 *
 * <p>注文モジュールの API を呼ぶ。
 */
@Component
@RequiredArgsConstructor
class OrderApiDirectory implements OrderDirectory {

    /**
     * 注文の API
     */
    private final OrderApi orderApi;

    /**
     * 注文のメールアドレスを返す。
     *
     * <p>注文がなければ、注文 ID を入れた例外を投げる（メールアドレスは入れない）。
     *
     * @param orderId 注文 ID
     * @return 注文のメールアドレス
     * @throws IllegalStateException 注文が見つからないとき
     */
    @Override
    public String email(OrderId orderId) {
        return orderApi.findEmail(orderId)
                .orElseThrow(() -> new IllegalStateException("注文が見つかりません: " + orderId));
    }
}
