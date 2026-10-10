package com.ecsite.module.order.application;

import com.ecsite.module.cart.event.CartChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * カートのイベントのリスナー。カートが変わったら、そのカートの注文手続きの引当を解く（AC-84）。<br />
 * コミット後に非同期で、別のトランザクションで動く。何度届いても結果は同じ。
 * 処理に失敗しても引当は期限で数えられなくなり、注文の時点ではカートのバージョンで変更を見つけるので、実害は小さい。
 */
@Component
@RequiredArgsConstructor
class CartChangedHandler {

    /**
     * 注文手続きのサービス
     */
    private final CheckoutService checkoutService;

    /**
     * カート変更：そのカートのすべての注文手続きの引当を解く。
     *
     * @param event カート変更
     */
    @ApplicationModuleListener
    void on(CartChanged event) {
        checkoutService.releaseReservations(event.cartId());
    }
}
