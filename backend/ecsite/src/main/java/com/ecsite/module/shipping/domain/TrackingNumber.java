package com.ecsite.module.shipping.domain;

import com.ecsite.common.error.exception.BusinessException;
import org.jspecify.annotations.Nullable;

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
     * 保存する値の形
     *
     * <p>半角の数字だけ。
     */
    private static final Pattern DIGITS = Pattern.compile("[0-9]+");

    /**
     * コンストラクタ
     *
     * <p>ハイフンを除いた後の形で受け取る。
     *
     * @param value 数字だけの送り状番号（1 桁以上 {@value #MAX_LENGTH} 桁まで）
     * @throws BusinessException 数字だけでない、または桁数が範囲の外（{@code TRACKING_NUMBER_INVALID}）
     */
    public TrackingNumber {
        if (value == null || value.length() > MAX_LENGTH || !DIGITS.matcher(value).matches()) {
            throw BusinessException.of(ShippingErrorCode.TRACKING_NUMBER_INVALID, List.of(MAX_LENGTH));
        }
    }

    /**
     * 入力された送り状番号から作る。
     *
     * <p>前後の空白を除き、ハイフンを除く。
     *
     * @param input 入力（半角の数字とハイフン、前後の空白を除いて {@value #MAX_LENGTH} 文字まで）
     * @return 送り状番号
     * @throws BusinessException 入力がない（{@code TRACKING_NUMBER_INVALID}）
     * @throws BusinessException 半角の数字とハイフン以外を含む（{@code TRACKING_NUMBER_INVALID}）
     * @throws BusinessException 数字がない（{@code TRACKING_NUMBER_INVALID}）
     * @throws BusinessException {@value #MAX_LENGTH} 文字を超える（{@code TRACKING_NUMBER_INVALID}）
     */
    public static TrackingNumber of(@Nullable String input) {
        var trimmed = input == null ? "" : input.strip();
        // ハイフンを含む入力の長さは、ハイフンを除く前に確認する。形（数字だけか）は、ハイフンを除いた後にコンストラクターが検査する
        if (trimmed.length() > MAX_LENGTH) {
            throw BusinessException.of(ShippingErrorCode.TRACKING_NUMBER_INVALID, List.of(MAX_LENGTH));
        }
        return new TrackingNumber(trimmed.replace("-", ""));
    }
}
