package com.ecsite.module.catalog.domain.product;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.module.catalog.domain.DisplayOrders;
import com.ecsite.module.catalog.domain.ProductErrorCode;
import com.ecsite.module.catalog.domain.product.vo.ProductImageId;
import com.ecsite.module.catalog.domain.product.vo.VariantCode;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * バリアントの一覧
 *
 * <p>表示順に並べて持つ。
 *
 * @param list バリアントの一覧
 */
record Variants(List<Variant> list) {

    /**
     * コンストラクタ。
     *
     * <p>表示順に並べ替える。
     *
     * @param list バリアントの一覧（空でもよい）
     */
    Variants(@NonNull List<Variant> list) {
        this.list = list.stream().sorted(Comparator.comparingInt(Variant::displayOrder)).toList();
    }

    /**
     * バリアントを ID で探す。
     *
     * @param variantId バリアント ID
     * @return バリアント
     * @throws BusinessException 一覧にないバリアント（{@code NOT_FOUND}）
     */
    Variant require(VariantId variantId) {
        return list.stream().filter(variant -> variant.id().equals(variantId)).findFirst()
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
    }

    /**
     * バリアントの数を返す。
     *
     * @return バリアントの数
     */
    int size() {
        return list.size();
    }

    /**
     * バリアントがないかを返す。
     *
     * @return ないなら true
     */
    boolean isEmpty() {
        return list.isEmpty();
    }

    /**
     * 同じバリアント品番のバリアントを含むかを返す。
     *
     * @param variantCode バリアント品番
     * @return 同じ品番のバリアントがあれば true
     */
    boolean containsCode(VariantCode variantCode) {
        return list.stream().anyMatch(variant -> variant.variantCode().equals(variantCode));
    }

    /**
     * 同じ軸の値の組み合わせのバリアントを含むかを返す。
     *
     * @param axisKey 軸キー（{@link Variant#axisKey()}）
     * @return 同じ軸キーのバリアントがあれば true
     */
    boolean containsAxisKey(String axisKey) {
        return list.stream().anyMatch(variant -> variant.axisKey().equals(axisKey));
    }

    /**
     * 軸の値の数が、指定した数と違うバリアントを含むかを返す。
     *
     * <p>軸の数は商品が持つので、商品が渡す。
     *
     * @param axisCount 軸の数
     * @return 軸の値の数が違うバリアントがあれば true
     */
    boolean hasAxisValueCountOtherThan(int axisCount) {
        return list.stream().anyMatch(variant -> variant.axisValues().size() != axisCount);
    }

    /**
     * 新しいバリアントの表示順（最大＋1。なければ 1）を返す。
     *
     * @return 表示順
     */
    int nextDisplayOrder() {
        return DisplayOrders.next(list.stream().mapToInt(Variant::displayOrder));
    }

    /**
     * バリアントを 1 件置き換えた一覧を返す。
     *
     * @param variantId 置き換えるバリアントの ID
     * @param change    バリアントから、置き換えたバリアントを作る関数
     * @return 置き換えた一覧
     * @throws BusinessException 一覧にないバリアント（{@code NOT_FOUND}）
     */
    Variants replace(VariantId variantId, UnaryOperator<Variant> change) {
        require(variantId);
        return new Variants(list.stream().map(variant -> variant.id().equals(variantId) ? change.apply(variant) : variant).toList());
    }

    /**
     * 画像の指定を外した一覧を返す。
     *
     * <p>その画像を指定しているバリアントだけ、画像の指定が外れて代表画像に戻る。
     *
     * @param imageId 指定を外す画像の ID
     * @return 外した一覧
     */
    Variants withoutImage(ProductImageId imageId) {
        return new Variants(list.stream()
                .map(variant -> imageId.equals(variant.imageId()) ? variant.withImageId(null) : variant)
                .toList());
    }

    /**
     * バリアントを末尾に加えた一覧を返す。
     *
     * @param variant 加えるバリアント
     * @return 加えた一覧
     */
    Variants add(Variant variant) {
        var added = new ArrayList<>(list);
        added.add(variant);
        return new Variants(added);
    }

    /**
     * バリアントを除いた一覧を返す。
     *
     * @param variantId 除くバリアントの ID
     * @return 除いた一覧
     * @throws BusinessException 一覧にないバリアント（{@code NOT_FOUND}）
     */
    Variants remove(VariantId variantId) {
        require(variantId);
        return new Variants(list.stream().filter(variant -> !variant.id().equals(variantId)).toList());
    }

    /**
     * 指定された順に並べ替えた一覧を返す。
     *
     * <p>指定された順に、1 から表示順を振る。
     *
     * @param orderedIds 並べたい順のバリアント ID
     * @return 並べ替えた一覧
     * @throws BusinessException 全てのバリアントと過不足なく一致しない（{@code INVALID_VARIANT_ORDER}）
     */
    Variants reorder(List<VariantId> orderedIds) {
        return new Variants(DisplayOrders.reorder(list, Variant::id, Variant::withDisplayOrder, orderedIds,
                ProductErrorCode.INVALID_VARIANT_ORDER));
    }
}
