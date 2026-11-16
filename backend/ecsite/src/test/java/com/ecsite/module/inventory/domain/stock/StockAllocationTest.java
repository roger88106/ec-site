package com.ecsite.module.inventory.domain.stock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在庫の割り当ての単体テスト
 *
 * <p>求める数量に販売可能数が足りない品目の判定（{@link StockAllocation#shortages}）だけを確認する。販売可能数の求め方と、
 * 引当・減算に使う流れは {@code StockReservationApiTest}・{@code StockOrderApiTest} が確認する。
 */
class StockAllocationTest {

    // ============================== 定数 ==============================

    /**
     * T シャツのバリアント ID
     */
    private static final UUID SHIRT = UUID.randomUUID();

    /**
     * 帽子のバリアント ID
     */
    private static final UUID CAP = UUID.randomUUID();

    // ============================== テスト ==============================

    @Test
    @DisplayName("AC-82 販売可能数が求める数量とちょうど同じなら、足りる")
    void exactQuantityIsEnough() {
        // 操作と確認：2 個に販売可能数 2 は足りる
        assertThat(StockAllocation.shortages(Map.of(SHIRT, 2), Map.of(SHIRT, 2))).isEmpty();
    }

    @Test
    @DisplayName("AC-82 販売可能数が求める数量より 1 少なければ、足りない")
    void oneLessIsShort() {
        // 操作と確認：2 個に販売可能数 1 は足りない
        assertThat(StockAllocation.shortages(Map.of(SHIRT, 2), Map.of(SHIRT, 1))).containsExactly(SHIRT);
    }

    @Test
    @DisplayName("AC-82 販売可能数にないバリアントは、販売可能数 0 として足りない")
    void missingSellableIsShort() {
        // 操作と確認：販売可能数の一覧にない T シャツは足りない
        assertThat(StockAllocation.shortages(Map.of(SHIRT, 1), Map.of())).containsExactly(SHIRT);
    }

    @Test
    @DisplayName("AC-82 販売可能数が負（引当の後に在庫数を減らした）のバリアントは、足りない")
    void negativeSellableIsShort() {
        // 操作と確認：販売可能数 -1 は 1 個に足りない
        assertThat(StockAllocation.shortages(Map.of(SHIRT, 1), Map.of(SHIRT, -1))).containsExactly(SHIRT);
    }

    @Test
    @DisplayName("AC-82 全ての品目が足りれば、足りない品目はない")
    void allEnoughIsEmpty() {
        // 操作と確認：T シャツと帽子のどちらも足りる
        assertThat(StockAllocation.shortages(Map.of(SHIRT, 1, CAP, 3), Map.of(SHIRT, 5, CAP, 3))).isEmpty();
    }

    @Test
    @DisplayName("AC-82 足りない品目だけを、求めた並びで返す")
    void returnsOnlyShortInRequestedOrder() {
        // 準備：帽子（足りない）、T シャツ（足りる）、3 つ目（足りない）の順に求める
        var third = UUID.randomUUID();
        var requested = new LinkedHashMap<UUID, Integer>();
        requested.put(CAP, 2);
        requested.put(SHIRT, 1);
        requested.put(third, 1);

        // 操作と確認：帽子と 3 つ目だけを、この順で返す
        assertThat(StockAllocation.shortages(requested, Map.of(SHIRT, 1, CAP, 1, third, 0))).containsExactly(CAP, third);
    }
}
