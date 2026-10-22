package com.ecsite.module.cart.domain;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * カート（docs/design/modules/cart.md）。品目を入れた順に持つ。価格は持たない。<br />
 * 最後の変更から 30 日たったカートは空として扱い、次に書くときに品目を消す。
 * 変更のたびにバージョンを上げる（注文手続きの後の変更を見つけるため）。<br />
 * 会員のカートは会員 ID を持ち、持ち主の会員だけが使える。ログインせずに作ったカートは会員を持たず、ID を知っていればだれでも使える。
 */
@Getter
@Accessors(fluent = true)
public class Cart {

    /**
     * 1 品目の数量の上限
     */
    static final int MAX_QUANTITY = 10;

    /**
     * 品目の数の上限
     */
    static final int MAX_ITEMS = 30;

    /**
     * 最後の変更からこの期間がたったら、空として扱う
     */
    static final Duration LIFETIME = Duration.ofDays(30);

    /**
     * カート ID
     */
    private final UUID id;

    /**
     * 会員 ID。ログインせずに作ったカートは null
     */
    @Getter(AccessLevel.NONE)
    private final UUID memberId;

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
     * @param memberId  会員 ID（ログインせずに作ったカートは null）
     * @param items     品目（入れた順）
     * @param version   バージョン
     * @param updatedAt 最終更新日時
     */
    public Cart(@NonNull UUID id, UUID memberId, @NonNull List<CartItem> items, long version, @NonNull Instant updatedAt) {
        this.id = id;
        this.memberId = memberId;
        this.items = new ArrayList<>(items);
        this.version = version;
        this.updatedAt = updatedAt;
    }

    /**
     * 空のカートを作る。
     *
     * @param id  カート ID
     * @param now 現在時刻
     * @return カート
     */
    public static Cart create(UUID id, Instant now) {
        return new Cart(id, null, List.of(), 0, now);
    }

    /**
     * 会員の空のカートを作る。
     *
     * @param id       カート ID
     * @param memberId 会員 ID
     * @param now      現在時刻
     * @return カート
     */
    public static Cart createForMember(UUID id, @NonNull UUID memberId, Instant now) {
        return new Cart(id, memberId, List.of(), 0, now);
    }

    /**
     * カートの持ち主の会員 ID を返す。
     *
     * @return 会員 ID。ログインせずに作ったカートは空
     */
    public Optional<UUID> memberId() {
        return Optional.ofNullable(memberId);
    }

    /**
     * 要求した人がカートを使えるか。会員のカートは持ち主の会員だけ、会員を持たないカートはだれでも。
     *
     * @param requesterMemberId 要求した会員の ID（ログインしていなければ null）
     * @return 使えれば true
     */
    public boolean isAccessibleBy(UUID requesterMemberId) {
        return memberId == null || memberId.equals(requesterMemberId);
    }

    /**
     * 指定した時刻での品目を返す。期限が切れていれば空。
     *
     * @param now 現在時刻
     * @return 品目（入れた順）
     */
    public List<CartItem> itemsAt(Instant now) {
        return isExpiredAt(now) ? List.of() : List.copyOf(items);
    }

    /**
     * バリアントを入れた後の数量を、変更せずに求める（販売可能数と比べるため）。
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
     * バリアントを入れる。カートにあれば数量を合算する。
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
                throw BusinessException.of(CartErrorCode.TOO_MANY_CART_ITEMS, List.of(MAX_ITEMS));
            }
            items.add(new CartItem(variantId, quantity, now));
        }
        touch(now);
    }

    /**
     * 品目の数量を変える。
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
     * 品目を削除する。カートになければ何もしない（冪等）。
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
     * 複数の品目を一度に削除する（注文した品目を除く）。カートにない品目は無視する。<br />
     * 1 つの変更として記録し、バージョンは 1 だけ上げる（保存の楽観ロックは 1 つ前のバージョンと比べるため）。
     *
     * @param variantIds 削除するバリアントの ID
     * @param now        現在時刻
     */
    public void removeAll(@NonNull Collection<UUID> variantIds, @NonNull Instant now) {
        if (!isExpiredAt(now) && items.removeIf(item -> variantIds.contains(item.variantId()))) {
            touch(now);
        }
    }

    /**
     * ログインせずに使っていたカートの品目を、このカート（会員のカート）へ移し、移したカートを空にする。<br />
     * 品目を入れた順に後ろへ入れ、同じバリアントは数量を合算して 10 で切り詰める。品目が 30 に達したら、残りの新しいバリアントは移さない。
     * 商品カタログ・在庫は確認しない。移すカートが空なら（2 回目）、どちらも変えない。
     *
     * @param source 移すカート（ログインせずに使っていたもの）
     * @param now    現在時刻
     * @return 切り詰めた品目と、移さなかった品目
     */
    public CartMergeResult mergeFrom(@NonNull Cart source, @NonNull Instant now) {
        var sourceItems = source.itemsAt(now);
        if (sourceItems.isEmpty()) {
            return CartMergeResult.EMPTY;
        }
        dropIfExpired(now);
        var truncated = new ArrayList<CartMergeResult.Item>();
        var skipped = new ArrayList<CartMergeResult.Item>();
        for (var item : sourceItems) {
            var index = indexOf(item.variantId());
            // 同じバリアントは数量を合算して上限で切り詰め、新しいバリアントは品目の数の上限を超える分を移さない
            if (index >= 0) {
                var merged = items.get(index).quantity() + item.quantity();
                if (merged > MAX_QUANTITY) {
                    merged = MAX_QUANTITY;
                    truncated.add(new CartMergeResult.Item(item.variantId(), merged));
                }
                items.set(index, items.get(index).withQuantity(merged));
            } else if (items.size() >= MAX_ITEMS) {
                skipped.add(new CartMergeResult.Item(item.variantId(), item.quantity()));
            } else {
                items.add(new CartItem(item.variantId(), Math.min(item.quantity(), MAX_QUANTITY), now));
            }
        }
        touch(now);
        source.clear(now);
        return new CartMergeResult(truncated, skipped);
    }

    /**
     * 品目をすべて消す（注文の成立）。
     *
     * @param now 現在時刻
     */
    public void clear(@NonNull Instant now) {
        items.clear();
        touch(now);
    }

    /**
     * 期限が指定した時刻より前に切れたカートの、最後の変更の時刻の上限（片付けで、消すカートを探すため）。<br />
     * 最後の変更がこの時刻より前のカートは、指定した時刻より前に期限が切れている。
     *
     * @param expiredBefore 期限が切れた時刻の上限
     * @return 最後の変更の時刻の上限
     */
    public static Instant lastUpdateExpiredBefore(@NonNull Instant expiredBefore) {
        return expiredBefore.minus(LIFETIME);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 期限が切れているか。最後の変更から 30 日ちょうどで切れる。
     */
    private boolean isExpiredAt(Instant now) {
        return !now.isBefore(updatedAt.plus(LIFETIME));
    }

    /**
     * 期限が切れていれば、書く前に品目を消す。
     */
    private void dropIfExpired(Instant now) {
        if (isExpiredAt(now)) {
            items.clear();
        }
    }

    /**
     * 数量が 1〜10 であることを確認する。
     */
    private static void verifyQuantity(int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw BusinessException.of(CartErrorCode.INVALID_CART_QUANTITY, List.of(MAX_QUANTITY));
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
