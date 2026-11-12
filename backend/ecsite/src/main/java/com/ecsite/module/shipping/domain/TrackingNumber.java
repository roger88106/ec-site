package com.ecsite.module.shipping.domain;

import com.ecsite.common.error.exception.BusinessException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 送り状番号
 *
 * <p>ハイフンを除いた数字だけを持つ。
 *
 * @param value 数字だけの送り状番号
 */
public record TrackingNumber(String value) {

    /**
     * 入力の長さの上限（ハイフンを含む）
     */
    public static final int MAX_LENGTH = 30;

    /**
     * 入力の形。半角の数字とハイフンだけ
     */
    private static final Pattern FORM = Pattern.compile("[0-9-]+");

    /**
     * 入力された送り状番号から作る。
     *
     * <p>前後の空白を除き、ハイフンを除く。
     *
     * @param input 入力（半角の数字とハイフン、前後の空白を除いて {@link #MAX_LENGTH} 文字まで）
     * @return 送り状番号
     * @throws BusinessException 入力がない、半角の数字とハイフン以外を含む、数字がない、{@link #MAX_LENGTH} 文字を超える（TRACKING_NUMBER_INVALID）
     */
    public static TrackingNumber of(String input) {
        var trimmed = input == null ? "" : input.strip();
        var digits = trimmed.replace("-", "");
        if (digits.isEmpty() || trimmed.length() > MAX_LENGTH || !FORM.matcher(trimmed).matches()) {
            throw BusinessException.of(ShippingErrorCode.TRACKING_NUMBER_INVALID, List.of(MAX_LENGTH));
        }
        return new TrackingNumber(digits);
    }
}
