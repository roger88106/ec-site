package com.ecsite.module.cart.service;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.cart.catalogdir.CartCatalogDirectory;
import com.ecsite.module.cart.event.CartChanged;
import com.ecsite.module.cart.stockdir.CartStockDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * カートのサービス（docs/modules/cart.md）。<br />
 * 変更したら、カート変更のイベントを発行する（注文モジュールが引当を解く。AC-84）。
 */
@Service
@RequiredArgsConstructor
public class CartService {

    /**
     * カートリポジトリ
     */
    private final CartRepository cartRepository;

    /**
     * 商品カタログの腐敗防止層（選べるバリアントか）
     */
    private final CartCatalogDirectory catalogDirectory;

    /**
     * 在庫の腐敗防止層（販売可能数）
     */
    private final CartStockDirectory stockDirectory;

    /**
     * イベントの発行
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 空のカートを作る（AC-73）。
     *
     * @return カート ID
     */
    @Transactional
    public UUID createCart() {
        var cart = Cart.create(UUID.randomUUID(), clock.instant());
        cartRepository.create(cart);
        return cart.id();
    }

    /**
     * バリアントを入れる（AC-72、74〜76）。同じバリアントは数量を合算する。
     *
     * @param cartId    カート ID
     * @param variantId バリアント ID
     * @param quantity  数量
     */
    @Transactional
    public void addItem(UUID cartId, UUID variantId, int quantity) {
        change(cartId, (cart, now) -> {
            if (!catalogDirectory.isSelectable(variantId)) {
                throw BusinessException.of(CartErrorCode.VARIANT_NOT_SELECTABLE);
            }
            verifyStock(variantId, cart.quantityAfterAdding(variantId, quantity, now));
            cart.add(variantId, quantity, now);
        });
    }

    /**
     * 品目の数量を変える（AC-78）。増やすときだけ販売可能数を確かめる。
     *
     * @param cartId    カート ID
     * @param variantId バリアント ID
     * @param quantity  新しい数量
     */
    @Transactional
    public void changeQuantity(UUID cartId, UUID variantId, int quantity) {
        change(cartId, (cart, now) -> {
            var current = cart.itemsAt(now).stream()
                    .filter(item -> item.variantId().equals(variantId))
                    .mapToInt(CartItem::quantity)
                    .findFirst();
            if (current.isPresent() && quantity > current.getAsInt()) {
                verifyStock(variantId, quantity);
            }
            cart.changeQuantity(variantId, quantity, now);
        });
    }

    /**
     * 品目を削除する（AC-78）。カートになければ何もしない。
     *
     * @param cartId    カート ID
     * @param variantId バリアント ID
     */
    @Transactional
    public void removeItem(UUID cartId, UUID variantId) {
        change(cartId, (cart, now) -> cart.remove(variantId, now));
    }

// ============================== プライベートメソッド ==============================

    /**
     * カートを読み込んで変更し、変わっていれば保存してカート変更のイベントを発行する。
     *
     * @param cartId カート ID
     * @param change 変更（カートと現在時刻を受け取る）
     */
    private void change(UUID cartId, CartChange change) {
        var now = clock.instant();
        var cart = cartRepository.findById(cartId).orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
        var version = cart.version();
        change.apply(cart, now);
        if (cart.version() != version) {
            cartRepository.save(cart);
            eventPublisher.publishEvent(CartChanged.of(now, cart.id()));
        }
    }

    /**
     * 数量が販売可能数を超えないことを確かめる（AC-76）。
     */
    private void verifyStock(UUID variantId, int quantity) {
        if (quantity > stockDirectory.sellableQuantity(variantId)) {
            throw BusinessException.of(CartErrorCode.QUANTITY_EXCEEDS_STOCK);
        }
    }

    /**
     * カートへの変更
     */
    @FunctionalInterface
    private interface CartChange {

        /**
         * 変更する。
         *
         * @param cart カート
         * @param now  現在時刻
         */
        void apply(Cart cart, Instant now);
    }
}
