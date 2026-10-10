package com.ecsite.module.cart.service;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * カート（docs/modules/cart.md）。品目を入れた順に持つ。価格は持たない（AC-79）。<br />
 * 最後の変更から 30 日たったカートは空として扱い、次に書くときに品目を消す（AC-81）。
 * 変更のたびにバージョンを上げる（注文手続きの後の変更を見つけるため）。
 */
@Getter
@Accessors(fluent = true)
public class Cart {

    /**
     * 1 品目の数量の上限（AC-75）
     */
    static final int MAX_QUANTITY = 10;

    /**
     * 品目の数の上限（AC-75）
     */
    static final int MAX_ITEMS = 30;

    /**
     * 最後の変更からこの期間がたったら、空として扱う（AC-81）
     */
    static final Duration LIFETIME = Duration.ofDays(30);

    /**
     * カート ID
     */
    private final UUID id;

    /**
     * 品目（入れた順）
     */
    @Getter(AccessLevel.NONE)
    private final List<CartItem> items;

    /**
     * バージョン。変更のたびに上がる
     */
    private long version;

    /**
     * 最終更新日時
     */
    private Instant updatedAt;

    /**
     * 全属性コンストラクタ（リポジトリが復元に使う）
     *
     * @param id        カート ID
     * @param items     品目（入れた順）
     * @param version   バージョン
     * @param updatedAt 最終更新日時
     */
    public Cart(@NonNull UUID id, @NonNull List<CartItem> items, long version, @NonNull Instant updatedAt) {
        this.id = id;
        this.items = new ArrayList<>(items);
        this.version = version;
        this.updatedAt = updatedAt;
    }

    /**
     * 空のカートを作る（AC-73）。
     *
     * @param id  カート ID
     * @param now 現在時刻
     * @return カート
     */
    public static Cart create(UUID id, Instant now) {
        return new Cart(id, List.of(), 0, now);
    }

    /**
     * 指定した時刻での品目を返す。期限が切れていれば空（AC-81）。
     *
     * @param now 現在時刻
     * @return 品目（入れた順）
     */
    public List<CartItem> itemsAt(Instant now) {
        return isExpiredAt(now) ? List.of() : List.copyOf(items);
    }

    /**
     * バリアントを入れた後の数量を、変更せずに求める（AC-74、76。販売可能数と比べるため）。
     *
     * @param variantId バリアント ID
     * @param quantity  入れる数量
     * @param now       現在時刻
     * @return 合算した数量
     */
    public int quantityAfterAdding(UUID variantId, int quantity, Instant now) {
        return itemsAt(now).stream()
                .filter(item -> item.variantId().equals(variantId))
                .mapToInt(CartItem::quantity)
                .sum() + quantity;
    }

    /**
     * バリアントを入れる。カートにあれば数量を合算する（AC-74）。
     *
     * @param variantId バリアント ID
     * @param quantity  入れる数量（1〜10）
     * @param now       現在時刻
     */
    public void add(@NonNull UUID variantId, int quantity, @NonNull Instant now) {
        verifyQuantity(quantity);
        dropIfExpired(now);
        var index = indexOf(variantId);
        if (index >= 0) {
            var merged = items.get(index).quantity() + quantity;
            verifyQuantity(merged);
            items.set(index, items.get(index).withQuantity(merged));
        } else {
            if (items.size() >= MAX_ITEMS) {
                throw BusinessException.of(CartErrorCode.TOO_MANY_CART_ITEMS);
            }
            items.add(new CartItem(variantId, quantity, now));
        }
        touch(now);
    }

    /**
     * 品目の数量を変える（AC-78）。
     *
     * @param variantId バリアント ID
     * @param quantity  新しい数量（1〜10）
     * @param now       現在時刻
     */
    public void changeQuantity(@NonNull UUID variantId, int quantity, @NonNull Instant now) {
        verifyQuantity(quantity);
        dropIfExpired(now);
        var index = indexOf(variantId);
        if (index < 0) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND);
        }
        items.set(index, items.get(index).withQuantity(quantity));
        touch(now);
    }

    /**
     * 品目を削除する（AC-78）。カートになければ何もしない（冪等）。
     *
     * @param variantId バリアント ID
     * @param now       現在時刻
     */
    public void remove(@NonNull UUID variantId, @NonNull Instant now) {
        var index = isExpiredAt(now) ? -1 : indexOf(variantId);
        if (index >= 0) {
            items.remove(index);
            touch(now);
        }
    }

    /**
     * 品目をすべて消す（注文の成立。AC-90）。
     *
     * @param now 現在時刻
     */
    public void clear(@NonNull Instant now) {
        items.clear();
        touch(now);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 期限が切れているか。最後の変更から 30 日ちょうどで切れる。
     */
    private boolean isExpiredAt(Instant now) {
        return !now.isBefore(updatedAt.plus(LIFETIME));
    }

    /**
     * 期限が切れていれば、書く前に品目を消す（AC-81）。
     */
    private void dropIfExpired(Instant now) {
        if (isExpiredAt(now)) {
            items.clear();
        }
    }

    /**
     * 数量が 1〜10 であることを確かめる（AC-75）。
     */
    private static void verifyQuantity(int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw BusinessException.of(CartErrorCode.INVALID_CART_QUANTITY);
        }
    }

    /**
     * バリアントの品目の位置。なければ -1。
     */
    private int indexOf(UUID variantId) {
        return IntStream.range(0, items.size())
                .filter(i -> items.get(i).variantId().equals(variantId))
                .findFirst()
                .orElse(-1);
    }

    /**
     * 変更を記録する：バージョンを上げ、最終更新日時を進める。
     */
    private void touch(Instant now) {
        version++;
        updatedAt = now;
    }
}
