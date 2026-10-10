package com.ecsite.module.catalog.domain.product;

import com.ecsite.module.catalog.domain.product.vo.VariantCode;
import lombok.NonNull;
import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.Identity;

import java.util.List;
import java.util.UUID;

/**
 * バリアント（商品集約の一部）。<br />
 * 価格は持たず、商品の価格を使う。
 *
 * @param id             バリアント ID
 * @param variantCode    バリアント品番
 * @param axisValues     軸の値（商品の軸と同じ並び）
 * @param displayOrder   商品の中の表示順
 * @param salesSuspended 販売停止
 */
@Entity
public record Variant(@Identity @NonNull UUID id, @NonNull VariantCode variantCode, @NonNull List<String> axisValues,
                      int displayOrder, boolean salesSuspended) {

    /**
     * 軸キーで軸の値を区切る文字（U+001F 単位区切り文字）
     */
    public static final String AXIS_KEY_SEPARATOR = "\u001F";

    /**
     * コンストラクタ。軸の値は変更できない一覧にする。
     */
    public Variant {
        axisValues = List.copyOf(axisValues);
    }

    /**
     * 軸キー：軸の値を連結した値。商品内での組み合わせの一意性を DB で守るために使う。軸のない商品では空文字
     *
     * @return 軸キー
     */
    public String axisKey() {
        return String.join(AXIS_KEY_SEPARATOR, axisValues);
    }

    /**
     * 表示順を変えたバリアントを返す。
     *
     * @param displayOrder 表示順
     * @return バリアント
     */
    Variant withDisplayOrder(int displayOrder) {
        return new Variant(id, variantCode, axisValues, displayOrder, salesSuspended);
    }

    /**
     * 販売停止を変えたバリアントを返す。
     *
     * @param salesSuspended 販売停止
     * @return バリアント
     */
    Variant withSalesSuspended(boolean salesSuspended) {
        return new Variant(id, variantCode, axisValues, displayOrder, salesSuspended);
    }
}
