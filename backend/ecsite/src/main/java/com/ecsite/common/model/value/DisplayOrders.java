package com.ecsite.common.model.value;

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
import java.util.stream.IntStream;

/**
 * 表示順の振り直し（商品のバリアント・画像、カテゴリで共通）。<br />
 * 表示順は 1 から始まる連番。並べ替えは、今の要素のすべてを、送られた順に並べ直す。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DisplayOrders {

    /**
     * 並べたい順の ID に、1 から表示順を振る。<br />
     * 今の要素と過不足なく一致しなければ（重複・漏れ・ないものを含めば）、業務のエラーを投げる。
     *
     * @param currentIds 今の要素の ID
     * @param orderedIds 並べたい順の ID
     * @param error      一致しないときのエラー
     * @return ID → 新しい表示順
     */
    public static Map<UUID, Integer> assign(Collection<UUID> currentIds, List<UUID> orderedIds, ErrorCodeProvider error) {
        // 数が同じで、集合としても等しければ、重複も漏れもない
        if (orderedIds.size() != currentIds.size() || !new HashSet<>(currentIds).equals(new HashSet<>(orderedIds))) {
            throw BusinessException.of(error);
        }
        Map<UUID, Integer> orders = new HashMap<>();
        for (int i = 0; i < orderedIds.size(); i++) {
            orders.put(orderedIds.get(i), i + 1);
        }
        return orders;
    }

    /**
     * 末尾に足す要素の表示順を返す。
     *
     * @param currentOrders 今の要素の表示順
     * @return 最大の次。要素がなければ 1
     */
    public static int next(IntStream currentOrders) {
        return currentOrders.max().orElse(0) + 1;
    }
}
