package com.ecsite.module.order.domain.order;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.value.BoundedText;
import org.jmolecules.ddd.annotation.ValueObject;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * お届け先（AC-86）。郵便番号と電話番号はハイフンを除いた数字で持つ。
 *
 * @param name       氏名（50 文字まで）
 * @param postalCode 郵便番号（数字 7 桁）
 * @param prefecture 都道府県（47 都道府県のいずれか）
 * @param address    市区町村以降の住所（200 文字まで）
 * @param phone      電話番号（数字 10〜11 桁）
 */
@ValueObject
public record ShippingAddress(String name, String postalCode, String prefecture, String address, String phone) {

    /**
     * 47 都道府県
     */
    public static final Set<String> PREFECTURES = Set.of(
            "北海道", "青森県", "岩手県", "宮城県", "秋田県", "山形県", "福島県",
            "茨城県", "栃木県", "群馬県", "埼玉県", "千葉県", "東京都", "神奈川県",
            "新潟県", "富山県", "石川県", "福井県", "山梨県", "長野県", "岐阜県", "静岡県", "愛知県",
            "三重県", "滋賀県", "京都府", "大阪府", "兵庫県", "奈良県", "和歌山県",
            "鳥取県", "島根県", "岡山県", "広島県", "山口県",
            "徳島県", "香川県", "愛媛県", "高知県",
            "福岡県", "佐賀県", "長崎県", "熊本県", "大分県", "宮崎県", "鹿児島県", "沖縄県");

    /**
     * 氏名の最大の文字数
     */
    static final int NAME_MAX_LENGTH = 50;

    /**
     * 住所の最大の文字数
     */
    static final int ADDRESS_MAX_LENGTH = 200;

    /**
     * 郵便番号の形（ハイフンは 3 桁目の後だけ）
     */
    private static final Pattern POSTAL_CODE = Pattern.compile("\\d{3}-?\\d{4}");

    /**
     * 電話番号の形（ハイフンを除いて数字 10〜11 桁）
     */
    private static final Pattern PHONE_DIGITS = Pattern.compile("\\d{10,11}");

    /**
     * 入力からお届け先を作る。郵便番号と電話番号のハイフンを除く。
     *
     * @param name       氏名
     * @param postalCode 郵便番号（ハイフンは任意）
     * @param prefecture 都道府県
     * @param address    住所
     * @param phone      電話番号（ハイフンは任意）
     * @return お届け先
     */
    public static ShippingAddress of(String name, String postalCode, String prefecture, String address, String phone) {
        if (!BoundedText.isFilledWithin(name, NAME_MAX_LENGTH)) {
            throw BusinessException.of(OrderErrorCode.INVALID_RECIPIENT_NAME);
        }
        if (postalCode == null || !POSTAL_CODE.matcher(postalCode).matches()) {
            throw BusinessException.of(OrderErrorCode.INVALID_POSTAL_CODE);
        }
        if (!PREFECTURES.contains(prefecture)) {
            throw BusinessException.of(OrderErrorCode.INVALID_PREFECTURE);
        }
        if (!BoundedText.isFilledWithin(address, ADDRESS_MAX_LENGTH)) {
            throw BusinessException.of(OrderErrorCode.INVALID_ADDRESS);
        }
        var phoneDigits = phone == null ? "" : phone.replace("-", "");
        if (!PHONE_DIGITS.matcher(phoneDigits).matches()) {
            throw BusinessException.of(OrderErrorCode.INVALID_PHONE_NUMBER);
        }
        return new ShippingAddress(name, postalCode.replace("-", ""), prefecture, address, phoneDigits);
    }
}
