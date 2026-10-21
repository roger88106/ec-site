package com.ecsite.module.order.domain.order;

import com.ecsite.module.order.domain.order.vo.AddressArea;

/**
 * テストで使う、お届け先の都道府県と市区町村（地域の最初のデータにあるもの）
 */
public final class TestAreas {

    /**
     * 東京都千代田区
     */
    public static final AddressArea CHIYODA = new AddressArea("13", "東京都", "13101", "千代田区");

    /**
     * 北海道札幌市中央区
     */
    public static final AddressArea SAPPORO_CHUO = new AddressArea("01", "北海道", "01101", "札幌市中央区");

    private TestAreas() {
    }
}
