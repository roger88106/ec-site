package com.ecsite.module.order.application.port;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 在庫の腐敗防止層の port
 *
 * <p>書き込みの操作は、呼んだ側のトランザクションに加わる。
 */
public interface StockDirectory {

    /**
     * 販売可能数を返す。
     *
     * <p>在庫のないバリアントは 0。
     *
     * @param variantIds       バリアント ID
     * @param excludedHolderId 数えない引当の持ち主（自分の注文手続き）。なければ null
     * @return バリアント ID → 販売可能数
     */
    Map<UUID, Integer> sellableQuantities(Collection<UUID> variantIds, @Nullable UUID excludedHolderId);

    /**
     * 全品目を引き当てる。
     *
     * <p>同じ持ち主の引当は置き換える。1 つでも足りなければ何も引き当てず、足りない品目を返す。そのとき前の引当は解いた状態で返る。
     *
     * @param holderId   持ち主 ID（注文手続き）
     * @param quantities バリアント ID → 数量
     * @param expiresAt  期限
     * @return 足りないバリアントの ID。引き当てたら空
     */
    Set<UUID> reserve(UUID holderId, Map<UUID, Integer> quantities, Instant expiresAt);

    /**
     * 持ち主の引当をまとめて解く。
     *
     * <p>持ち主の数によらず 1 回で解く。空なら何もしない。
     *
     * @param holderIds 持ち主 ID（注文手続き）
     */
    void release(Collection<UUID> holderIds);

    /**
     * 引当を在庫数の減算に置き換える。
     *
     * <p>1 つでも足りなければ在庫数を変えず、足りない品目を返す。そのとき引当は解いた状態で返る。
     *
     * @param holderId    持ち主 ID
     * @param quantities  バリアント ID → 数量
     * @param orderId     注文 ID
     * @param orderNumber 注文番号
     * @return 足りないバリアントの ID。減算したら空
     */
    Set<UUID> commit(UUID holderId, Map<UUID, Integer> quantities, UUID orderId, String orderNumber);

    /**
     * キャンセルした注文の数量を在庫数に戻す。
     *
     * @param quantities  バリアント ID → 数量
     * @param orderId     注文 ID
     * @param orderNumber 注文番号
     */
    void restore(Map<UUID, Integer> quantities, UUID orderId, String orderNumber);
}
