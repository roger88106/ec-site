package com.ecsite.module.order.application.handler;

import com.ecsite.module.cart.event.CartChanged;
import com.ecsite.module.order.application.checkout.CheckoutCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * カートのイベントのリスナー
 *
 * <p>コミット後に非同期で、別のトランザクションで動く。何度届いても結果は同じ。
 * 処理に失敗しても引当は期限で数えられなくなり、注文の時点ではカートのバージョンで変更を見つけるので、実害は小さい。
 */
@Component
@RequiredArgsConstructor
class CartChangedHandler {

    /**
     * 注文手続きのサービス
     */
    private final CheckoutCommandService checkoutCommandService;

    /**
     * カート変更：イベントのバージョンより古いバージョンで始めた注文手続きの引当を解く。
     *
     * <p>変更の後に始めた注文手続きの引当は、遅れて届いたイベントで解かない。バージョンがないイベントは、すべて解く。
     *
     * @param event カート変更
     */
    @ApplicationModuleListener
    void on(CartChanged event) {
        var cartVersion = event.cartVersion();
        if (cartVersion == null) {
            // 変更の前に発行されたイベント ※開始の前後を区別できないので、すべて解く
            checkoutCommandService.releaseReservations(event.cartId().value());
            return;
        }
        checkoutCommandService.releaseReservationsStartedBefore(event.cartId().value(), cartVersion);
    }
}
