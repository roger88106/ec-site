package com.ecsite.module.shipping.domain;

/**
 * 送り状番号（docs/backend/modules/shipping.md「送り状番号」）。ハイフンを除いた数字だけを持つ。
 *
 * @param value 数字だけの送り状番号
 */
public record TrackingNumber(String value) {

    /**
     * 入力された送り状番号から作る。前後の空白を除き、ハイフンを除く。
     *
     * @param input 入力（半角の数字とハイフン、前後の空白を除いて 30 文字まで）
     * @return 送り状番号
     */
    public static TrackingNumber of(String input) {
        throw new UnsupportedOperationException("未実装");
    }
}
