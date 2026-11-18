package com.ecsite.module.catalog.domain;

import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.exception.BusinessException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * 表示順の振り直し
 *
 * <p>表示順は 1 から始まる連番。並べ替えは、今の要素の全てを、指定された順に並べ直す。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DisplayOrders {

    /**
     * 並べたい順の ID に、1 から表示順を振る。
     *
     * @param currentIds 今の要素の ID
     * @param orderedIds 並べたい順の ID
     * @param error      一致しないときのエラー
     * @return ID → 新しい表示順
     * @throws BusinessException 今の要素と過不足なく一致しない（重複・漏れ・ないものを含む。{@code error} のエラー）
     */
    public static Map<UUID, Integer> assign(Collection<UUID> currentIds, List<UUID> orderedIds, ErrorCodeProvider error) {
        // 数が同じで、集合としても等しければ、重複も漏れもない
        if (orderedIds.size() != currentIds.size() || !new HashSet<>(currentIds).equals(new HashSet<>(orderedIds))) {
            throw BusinessException.of(error);
        }
        Map<UUID, Integer> orders = new HashMap<>();
        var order = 1;
        for (var id : orderedIds) {
            orders.put(id, order++);
        }
        return orders;
    }

    /**
     * 要素に、指定された順の表示順を付ける。
     *
     * <p>指定された順に、1 から表示順を振る。返す一覧の要素の順序は元のままで、表示順で整列するのは受け取る側が行う。
     *
     * @param items            今の要素
     * @param idOf             要素の ID
     * @param withDisplayOrder 表示順を変えた要素を作る
     * @param orderedIds       並べたい順の ID
     * @param error            一致しないときのエラー
     * @param <T>              要素の型
     * @return 新しい表示順を付けた要素（変更できない一覧）
     * @throws BusinessException 今の要素と過不足なく一致しない（重複・漏れ・ないものを含む。{@code error} のエラー）
     */
    public static <T> List<T> reorder(List<T> items, Function<T, UUID> idOf, BiFunction<T, Integer, T> withDisplayOrder,
                                      List<UUID> orderedIds, ErrorCodeProvider error) {
        // 指定された順に新しい表示順を振る
        var orders = assign(items.stream().map(idOf).toList(), orderedIds, error);
        return items.stream()
                .map(item -> withDisplayOrder.apply(item, orders.get(idOf.apply(item))))
                .toList();
    }

    /**
     * 末尾に追加する要素の表示順を返す。
     *
     * @param currentOrders 今の要素の表示順
     * @return 最大の次。要素がなければ 1
     */
    public static int next(IntStream currentOrders) {
        return currentOrders.max().orElse(0) + 1;
    }
}
