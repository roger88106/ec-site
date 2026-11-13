package com.ecsite.module.cart.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.cart.application.model.CartContentsView;
import com.ecsite.module.cart.application.model.MergedCart;
import com.ecsite.module.cart.application.port.CartRepository;
import com.ecsite.module.cart.application.port.CatalogDirectory;
import com.ecsite.module.cart.application.port.StockDirectory;
import com.ecsite.module.cart.domain.Cart;
import com.ecsite.module.cart.domain.CartErrorCode;
import com.ecsite.module.cart.event.CartChanged;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * カートのサービス
 *
 * <p>カートを変更したら、カート変更のイベントを発行する。
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
    private final CatalogDirectory catalogDirectory;

    /**
     * 在庫の腐敗防止層（販売可能数）
     */
    private final StockDirectory stockDirectory;

    /**
     * イベントの発行
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 期限が指定した時刻より前に切れたカートを、品目と共に、件数の上限まで消す。
     *
     * @param expiredBefore この時刻より前に期限が切れたカートを消す
     * @param limit         消す件数の上限
     * @return 消した件数
     */
    @Transactional
    public int deleteExpired(@NonNull Instant expiredBefore, int limit) {
        return cartRepository.deleteUpdatedBefore(Cart.lastUpdateExpiredBefore(expiredBefore), limit);
    }

    /**
     * 空のカートを作る。
     *
     * @return カート ID
     */
    @Transactional
    public UUID createCart() {
        var cart = Cart.create(UUID.randomUUID(), clock.instant());
        // 会員を持たないカートは、会員ごとの一意制約に当たらないので、保存されないのは想定外
        if (!cartRepository.create(cart)) {
            throw SystemException.of("カートを作れない", null);
        }
        return cart.id();
    }

    /**
     * バリアントを入れる。
     *
     * <p>同じバリアントは数量を合算する。
     *
     * @param cartId    カート ID
     * @param requester リクエストした会員の ID（ログインしていなければ null）
     * @param variantId バリアント ID
     * @param quantity  数量
     * @throws BusinessException カートがない、またはカートの持ち主でない（{@code NOT_FOUND}）
     * @throws BusinessException バリアントを選べない（{@code VARIANT_NOT_SELECTABLE}）
     * @throws BusinessException 数量が範囲の外（{@code INVALID_CART_QUANTITY}）
     * @throws BusinessException 販売可能数を超える（{@code QUANTITY_EXCEEDS_STOCK}）
     * @throws BusinessException 品目の数が上限に達している（{@code TOO_MANY_CART_ITEMS}）
     * @throws BusinessException 読み込んだ後に他で変更された（{@code CONCURRENT_MODIFICATION}）
     */
    @Transactional
    public void addItem(@NonNull UUID cartId, @Nullable UUID requester, @NonNull UUID variantId, int quantity) {
        change(cartId, requester, (cart, now) -> {
            // 選べるバリアントかの確認
            if (!catalogDirectory.isSelectable(variantId)) {
                throw BusinessException.of(CartErrorCode.VARIANT_NOT_SELECTABLE);
            }
            // 合算した後の数量と販売可能数の比較
            verifyStock(variantId, cart.quantityAfterAdding(variantId, quantity, now));
            // 品目の追加
            cart.add(variantId, quantity, now);
        });
    }

    /**
     * 品目の数量を変える。
     *
     * <p>増やすときだけ販売可能数を確認する。
     *
     * @param cartId    カート ID
     * @param requester リクエストした会員の ID（ログインしていなければ null）
     * @param variantId バリアント ID
     * @param quantity  新しい数量
     * @throws BusinessException カートがない、カートの持ち主でない、またはカートにない品目を指定した（{@code NOT_FOUND}）
     * @throws BusinessException 数量が範囲の外（{@code INVALID_CART_QUANTITY}）
     * @throws BusinessException 販売可能数を超える（{@code QUANTITY_EXCEEDS_STOCK}）
     * @throws BusinessException 読み込んだ後に他で変更された（{@code CONCURRENT_MODIFICATION}）
     */
    @Transactional
    public void changeQuantity(@NonNull UUID cartId, @Nullable UUID requester, @NonNull UUID variantId, int quantity) {
        change(cartId, requester, (cart, now) -> {
            // 今の数量より増やすときだけ、在庫を確認する（減らす操作は、在庫が減っていても受け付ける）
            var current = cart.quantityOf(variantId, now);
            if (current.isPresent() && quantity > current.getAsInt()) {
                verifyStock(variantId, quantity);
            }
            cart.changeQuantity(variantId, quantity, now);
        });
    }

    /**
     * 品目を削除する。
     *
     * <p>品目がカートになければ何もしない。
     *
     * @param cartId    カート ID
     * @param requester リクエストした会員の ID（ログインしていなければ null）
     * @param variantId バリアント ID
     * @throws BusinessException カートがない（{@code NOT_FOUND}）
     * @throws BusinessException カートの持ち主でない（{@code NOT_FOUND}）
     * @throws BusinessException 読み込んだ後に他で変更された（{@code CONCURRENT_MODIFICATION}）
     */
    @Transactional
    public void removeItem(@NonNull UUID cartId, @Nullable UUID requester, @NonNull UUID variantId) {
        change(cartId, requester, (cart, now) -> cart.remove(variantId, now));
    }

    /**
     * カートの中身を返す。
     *
     * <p>期限切れのカートは品目が空。
     *
     * @param cartId カート ID
     * @return カートの中身。カートがなければ空
     */
    @Transactional(readOnly = true)
    public Optional<CartContentsView> findContents(@NonNull UUID cartId) {
        var now = clock.instant();
        return cartRepository.findById(cartId).map(cart -> toView(cart, now));
    }

    /**
     * 指定したバリアントの品目を、数量によらず行ごとカートから除く。
     *
     * <p>カートがなければ何もしない。持ち主は確認しない。
     *
     * @param cartId     カート ID
     * @param variantIds 除くバリアントの ID
     * @throws BusinessException 読み込んだ後に他で変更された（{@code CONCURRENT_MODIFICATION}）
     */
    @Transactional
    public void removeItems(@NonNull UUID cartId, @NonNull Collection<UUID> variantIds) {
        var now = clock.instant();
        cartRepository.findById(cartId).ifPresent(cart -> {
            var version = cart.version();
            cart.removeAll(variantIds, now);
            saveIfChanged(cart, version, now);
        });
    }

    /**
     * 会員のカートの ID を返す。
     *
     * <p>まだなければ空のカートを作る（会員ごとに 1 つ）。
     *
     * @param memberId 会員 ID
     * @return カート ID
     */
    @Transactional
    public UUID memberCartId(@NonNull UUID memberId) {
        // 既存のカートの検索
        var existing = cartRepository.findIdByMemberId(memberId);
        if (existing.isPresent()) {
            return existing.get();
        }
        // カートの作成
        var cart = Cart.createForMember(UUID.randomUUID(), memberId, clock.instant());
        if (cartRepository.create(cart)) {
            return cart.id();
        }
        // 同じ会員の初回のリクエストが同時に来ると、会員ごとの一意制約で 1 つだけ作られる。作れなかったときは、先に作られたカートを読み直す
        return cartRepository.findIdByMemberId(memberId)
                .orElseThrow(() -> SystemException.of("会員のカートを作れない", null));
    }

    /**
     * ログインせずに使っていたカートの品目を、会員のカートへ移す。
     *
     * <p>移したカートは空になる。
     *
     * <p>商品カタログ・在庫は確認しない。変わったカートごとに、カート変更のイベントを発行する。
     *
     * @param memberId     会員 ID
     * @param sourceCartId 移すカートの ID
     * @return 会員のカートの ID と、移した結果
     * @throws BusinessException 移すカートがない（{@code NOT_FOUND}）
     * @throws BusinessException 移すカートが会員のカートである（{@code NOT_FOUND}）
     * @throws BusinessException 読み込んだ後に他で変更された（{@code CONCURRENT_MODIFICATION}）
     */
    @Transactional
    public MergedCart merge(@NonNull UUID memberId, @NonNull UUID sourceCartId) {
        // 移す元の取得 ※会員のカートは移す元にできない（ないカートと同じく NOT_FOUND にする）
        var source = load(sourceCartId);
        if (source.memberId().isPresent()) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND);
        }
        // 移す先（会員のカート）の取得
        var target = load(memberCartId(memberId));
        // 品目の移動
        var now = clock.instant();
        var sourceVersion = source.version();
        var targetVersion = target.version();
        var result = target.mergeFrom(source, now);
        // 変わったカートの保存
        saveIfChanged(target, targetVersion, now);
        saveIfChanged(source, sourceVersion, now);
        return new MergedCart(target.id(), result);
    }

// ============================== プライベートメソッド ==============================

    /**
     * カートを読み込んで変更し、変わっていれば保存してカート変更のイベントを発行する。
     *
     * @param cartId    カート ID
     * @param requester リクエストした会員の ID（ログインしていなければ null）
     * @param change    変更（カートと現在時刻を受け取る）
     */
    private void change(UUID cartId, @Nullable UUID requester, CartChange change) {
        var now = clock.instant();
        var cart = load(cartId);
        // 会員のカートは持ち主だけが変えられる。ほかの人には、ないカートと同じく NOT_FOUND にする
        if (!cart.isAccessibleBy(requester)) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND);
        }
        var version = cart.version();
        change.apply(cart, now);
        saveIfChanged(cart, version, now);
    }

    /**
     * カートを読み込む。
     *
     * <p>なければ {@code NOT_FOUND}。
     */
    private Cart load(UUID cartId) {
        return cartRepository.findById(cartId).orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
    }

    /**
     * カートが変わっていれば、保存してカート変更のイベントを発行する。
     */
    private void saveIfChanged(Cart cart, long versionBefore, Instant now) {
        if (cart.version() != versionBefore) {
            cartRepository.save(cart);
            eventPublisher.publishEvent(CartChanged.of(now, cart.id()));
        }
    }

    /**
     * 数量が販売可能数を超えないことを確認する。
     */
    private void verifyStock(UUID variantId, int quantity) {
        if (quantity > stockDirectory.sellableQuantity(variantId)) {
            throw BusinessException.of(CartErrorCode.QUANTITY_EXCEEDS_STOCK);
        }
    }

    /**
     * 指定した時刻の中身から、返す値を作る。
     */
    private static CartContentsView toView(Cart cart, Instant now) {
        var items = cart.itemsAt(now).stream()
                .map(item -> new CartContentsView.Item(item.variantId(), item.quantity()))
                .toList();
        return new CartContentsView(cart.id(), cart.memberId().orElse(null), cart.version(), items);
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
