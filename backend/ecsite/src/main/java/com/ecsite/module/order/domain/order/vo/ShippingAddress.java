package com.ecsite.module.order.domain.order.vo;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.value.BoundedText;
import com.ecsite.module.order.domain.order.OrderErrorCode;
import org.jmolecules.ddd.annotation.ValueObject;

import java.util.List;
import java.util.regex.Pattern;

/**
 * お届け先。郵便番号と電話番号はハイフンを除いた数字で持つ。<br />
 * 都道府県と市区町村がマスターデータにあるかは、作る前に注文の確定で確かめる（地域の API）。
 *
 * @param name       氏名（50 文字まで）
 * @param postalCode 郵便番号（数字 7 桁）
 * @param area       都道府県と市区町村（コードと注文の時点の名前）
 * @param address    市区町村より下の住所（200 文字まで）
 * @param phone      電話番号（数字 10〜11 桁）
 */
@ValueObject
public record ShippingAddress(String name, String postalCode, AddressArea area, String address, String phone) {

    /**
     * 氏名の最大の文字数
     */
    static final int NAME_MAX_LENGTH = 50;

    /**
     * 住所の最大の文字数
     */
    static final int ADDRESS_MAX_LENGTH = 200;

    /**
     * 郵便番号の桁数（ハイフンを除く）
     */
    public static final int POSTAL_CODE_DIGITS = 7;

    /**
     * 電話番号の桁数の下限（ハイフンを除く）
     */
    public static final int PHONE_MIN_DIGITS = 10;

    /**
     * 電話番号の桁数の上限（ハイフンを除く）
     */
    public static final int PHONE_MAX_DIGITS = 11;

    /**
     * 郵便番号のハイフンの前の桁数
     */
    private static final int POSTAL_CODE_HEAD_DIGITS = 3;

    /**
     * 郵便番号の形（ハイフンは前の桁の後だけ）
     */
    private static final Pattern POSTAL_CODE = Pattern.compile("\\d{%d}-?\\d{%d}"
            .formatted(POSTAL_CODE_HEAD_DIGITS, POSTAL_CODE_DIGITS - POSTAL_CODE_HEAD_DIGITS));

    /**
     * 電話番号の形（ハイフンを除いた数字の桁数）
     */
    private static final Pattern PHONE_DIGITS = Pattern.compile("\\d{%d,%d}".formatted(PHONE_MIN_DIGITS, PHONE_MAX_DIGITS));

    /**
     * 入力からお届け先を作る。郵便番号と電話番号のハイフンを除く。
     *
     * @param name       氏名
     * @param postalCode 郵便番号（ハイフンは任意）
     * @param area       都道府県と市区町村（マスターデータで確かめたもの）
     * @param address    市区町村より下の住所
     * @param phone      電話番号（ハイフンは任意）
     * @return お届け先
     */
    public static ShippingAddress of(String name, String postalCode, AddressArea area, String address, String phone) {
        if (!BoundedText.isFilledWithin(name, NAME_MAX_LENGTH)) {
            throw BusinessException.of(OrderErrorCode.INVALID_RECIPIENT_NAME, List.of(NAME_MAX_LENGTH));
        }
        if (postalCode == null || !POSTAL_CODE.matcher(postalCode).matches()) {
            throw BusinessException.of(OrderErrorCode.INVALID_POSTAL_CODE, List.of(POSTAL_CODE_DIGITS));
        }
        if (!BoundedText.isFilledWithin(address, ADDRESS_MAX_LENGTH)) {
            throw BusinessException.of(OrderErrorCode.INVALID_ADDRESS, List.of(ADDRESS_MAX_LENGTH));
        }
        var phoneDigits = phone == null ? "" : phone.replace("-", "");
        if (!PHONE_DIGITS.matcher(phoneDigits).matches()) {
            throw BusinessException.of(OrderErrorCode.INVALID_PHONE_NUMBER, List.of(PHONE_MIN_DIGITS, PHONE_MAX_DIGITS));
        }
        return new ShippingAddress(name, postalCode.replace("-", ""), area, address, phoneDigits);
    }
}
