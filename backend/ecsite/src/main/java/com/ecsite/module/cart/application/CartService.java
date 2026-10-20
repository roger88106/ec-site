package com.ecsite.module.cart.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.cart.api.CartContents;
import com.ecsite.module.cart.application.port.CartRepository;
import com.ecsite.module.cart.application.port.CatalogDirectory;
import com.ecsite.module.cart.application.port.StockDirectory;
import com.ecsite.module.cart.domain.Cart;
import com.ecsite.module.cart.domain.CartErrorCode;
import com.ecsite.module.cart.domain.CartItem;
import com.ecsite.module.cart.domain.CartMergeResult;
import com.ecsite.module.cart.event.CartChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * カートのサービス（docs/backend/modules/cart.md）。<br />
 * 変更したら、カート変更のイベントを発行する（注文モジュールが引当を解く）。
 * 会員のカートは、持ち主の会員だけが使える。ほかの人には、カートがないときと同じく 404 にする。
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
     * 期限が指定した時刻より前に切れたカートを、品目と共に、件数の上限まで消す（片付けのジョブから呼ぶ）。
     *
     * @param expiredBefore この時刻より前に期限が切れたカートを消す
     * @param limit         消す件数の上限
     * @return 消した件数
     */
    @Transactional
    public int deleteExpired(Instant expiredBefore, int limit) {
        return 0;
    }

    /**
     * 空のカートを作る。
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
     * バリアントを入れる。同じバリアントは数量を合算する。
     *
     * @param cartId    カート ID
     * @param requester 要求した会員の ID（ログインしていなければ null）
     * @param variantId バリアント ID
     * @param quantity  数量
     */
    @Transactional
    public void addItem(UUID cartId, UUID requester, UUID variantId, int quantity) {
        change(cartId, requester, (cart, now) -> {
            if (!catalogDirectory.isSelectable(variantId)) {
                throw BusinessException.of(CartErrorCode.VARIANT_NOT_SELECTABLE);
            }
            verifyStock(variantId, cart.quantityAfterAdding(variantId, quantity, now));
            cart.add(variantId, quantity, now);
        });
    }

    /**
     * 品目の数量を変える。増やすときだけ販売可能数を確かめる。
     *
     * @param cartId    カート ID
     * @param requester 要求した会員の ID（ログインしていなければ null）
     * @param variantId バリアント ID
     * @param quantity  新しい数量
     */
    @Transactional
    public void changeQuantity(UUID cartId, UUID requester, UUID variantId, int quantity) {
        change(cartId, requester, (cart, now) -> {
            // 今の数量より増やすときだけ、在庫を確かめる（減らす操作は、在庫が減っていても受け付ける）
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
     * 品目を削除する。カートになければ何もしない。
     *
     * @param cartId    カート ID
     * @param requester 要求した会員の ID（ログインしていなければ null）
     * @param variantId バリアント ID
     */
    @Transactional
    public void removeItem(UUID cartId, UUID requester, UUID variantId) {
        change(cartId, requester, (cart, now) -> cart.remove(variantId, now));
    }

    /**
     * カートの中身を返す（注文モジュール向け）。期限切れのカートは品目が空。
     *
     * @param cartId カート ID
     * @return カートの中身。カートがなければ空
     */
    @Transactional(readOnly = true)
    public Optional<CartContents> findContents(UUID cartId) {
        var now = clock.instant();
        return cartRepository.findById(cartId).map(cart -> new CartContents(cart.id(), cart.memberId().orElse(null), cart.version(),
                cart.itemsAt(now).stream().map(item -> new CartContents.Item(item.variantId(), item.quantity())).toList()));
    }

    /**
     * 注文した品目（バリアント）を、数量によらず行ごとカートから除く（注文の与信の成功）。<br />
     * 注文の後に足した品目は残る。カートがなければ何もしない。
     *
     * @param cartId     カート ID
     * @param variantIds 除くバリアントの ID
     */
    @Transactional
    public void removeItems(UUID cartId, Collection<UUID> variantIds) {
        // 注文の与信の成功で呼ばれる。持ち主の確認は、注文手続きを始めるときに注文モジュールが済ませている
        var now = clock.instant();
        cartRepository.findById(cartId).ifPresent(cart -> {
            var version = cart.version();
            cart.removeAll(variantIds, now);
            saveIfChanged(cart, version, now);
        });
    }

    /**
     * 会員のカートの ID を返す。まだなければ空のカートを作る（会員ごとに 1 つ）。<br />
     * 同じ会員の初回の要求が同時に来ても、会員ごとの一意制約で 1 つだけ作られ、作れなかった要求は先に作られたカートを読み直す。
     *
     * @param memberId 会員 ID
     * @return カート ID
     */
    @Transactional
    public UUID memberCartId(UUID memberId) {
        var existing = cartRepository.findIdByMemberId(memberId);
        if (existing.isPresent()) {
            return existing.get();
        }
        cartRepository.create(Cart.createForMember(UUID.randomUUID(), memberId, clock.instant()));
        return cartRepository.findIdByMemberId(memberId)
                .orElseThrow(() -> SystemException.of("会員のカートを作れない：" + memberId, null));
    }

    /**
     * ログインせずに使っていたカートの品目を、会員のカートへ移す。移したカートは空になる。<br />
     * 移す元に指定できるのは、会員を持たないカートだけ（会員のカートは 404）。商品カタログ・在庫は確かめない。
     * 変わったカートごとに、カート変更のイベントを発行する（その注文手続きの引当を解く）。
     *
     * @param memberId     会員 ID
     * @param sourceCartId 移すカートの ID
     * @return 会員のカートの ID と、移した結果
     */
    @Transactional
    public MergedCart merge(UUID memberId, UUID sourceCartId) {
        var source = load(sourceCartId);
        if (source.memberId().isPresent()) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND);
        }
        var target = load(memberCartId(memberId));
        var now = clock.instant();
        var sourceVersion = source.version();
        var targetVersion = target.version();
        var result = target.mergeFrom(source, now);
        saveIfChanged(target, targetVersion, now);
        saveIfChanged(source, sourceVersion, now);
        return new MergedCart(target.id(), result);
    }

    /**
     * 会員のカートと、移した結果
     *
     * @param cartId 会員のカートの ID
     * @param result 移した結果
     */
    public record MergedCart(UUID cartId, CartMergeResult result) {
    }

// ============================== プライベートメソッド ==============================

    /**
     * カートを読み込んで変更し、変わっていれば保存してカート変更のイベントを発行する。
     *
     * @param cartId    カート ID
     * @param requester 要求した会員の ID（ログインしていなければ null）。会員のカートは持ち主だけが変えられる
     * @param change    変更（カートと現在時刻を受け取る）
     */
    private void change(UUID cartId, UUID requester, CartChange change) {
        var now = clock.instant();
        var cart = load(cartId);
        if (!cart.isAccessibleBy(requester)) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND);
        }
        var version = cart.version();
        change.apply(cart, now);
        saveIfChanged(cart, version, now);
    }

    /**
     * カートを読み込む。なければ 404。
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
     * 数量が販売可能数を超えないことを確かめる。
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
