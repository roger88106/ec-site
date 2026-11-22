package com.ecsite.module.catalog.domain.product;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.domain.DisplayOrders;
import com.ecsite.module.catalog.domain.ProductErrorCode;
import com.ecsite.module.catalog.domain.product.vo.ProductImageId;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 画像の一覧
 *
 * <p>表示順に並べて持つ。先頭が代表画像。
 *
 * @param list 画像の一覧
 */
record ProductImages(List<ProductImage> list) {

    /**
     * 1 商品あたりの画像の上限
     */
    static final int MAX_IMAGES = 20;

    /**
     * コンストラクタ。
     *
     * <p>表示順に並べ替える。画像の数が上限を超えるとき、IllegalArgumentException を投げる。例外のメッセージには値を入れない。
     *
     * @param list 画像の一覧（空でもよい）
     */
    ProductImages(@NonNull List<ProductImage> list) {
        if (list.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("画像の数が上限を超える");
        }
        this.list = list.stream().sorted(Comparator.comparingInt(ProductImage::displayOrder)).toList();
    }

    /**
     * 画像を ID で探す。
     *
     * @param imageId 画像 ID
     * @return 画像
     * @throws BusinessException 一覧にない画像（{@code NOT_FOUND}）
     */
    ProductImage require(ProductImageId imageId) {
        return list.stream().filter(image -> image.id().equals(imageId)).findFirst()
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
    }

    /**
     * 画像を含むかを返す。
     *
     * @param imageId 画像 ID
     * @return 含むなら true
     */
    boolean contains(ProductImageId imageId) {
        return list.stream().anyMatch(image -> image.id().equals(imageId));
    }

    /**
     * 画像の数を返す。
     *
     * @return 画像の数
     */
    int size() {
        return list.size();
    }

    /**
     * 新しい画像の表示順（最大＋1。なければ 1）を返す。
     *
     * @return 表示順
     */
    int nextDisplayOrder() {
        return DisplayOrders.next(list.stream().mapToInt(ProductImage::displayOrder));
    }

    /**
     * 画像を末尾に加えた一覧を返す。
     *
     * @param image 加える画像
     * @return 加えた一覧
     * @throws IllegalArgumentException 画像の数が上限を超える
     */
    ProductImages add(ProductImage image) {
        var added = new ArrayList<>(list);
        added.add(image);
        return new ProductImages(added);
    }

    /**
     * 画像を除いた一覧を返す。
     *
     * @param imageId 除く画像の ID
     * @return 除いた一覧
     * @throws BusinessException 一覧にない画像（{@code NOT_FOUND}）
     */
    ProductImages remove(ProductImageId imageId) {
        require(imageId);
        return new ProductImages(list.stream().filter(image -> !image.id().equals(imageId)).toList());
    }

    /**
     * 指定された順に並べ替えた一覧を返す。
     *
     * <p>指定された順に、1 から表示順を振る。
     *
     * @param orderedIds 並べたい順の画像 ID
     * @return 並べ替えた一覧
     * @throws BusinessException 全ての画像と過不足なく一致しない（{@code INVALID_IMAGE_ORDER}）
     */
    ProductImages reorder(List<ProductImageId> orderedIds) {
        return new ProductImages(DisplayOrders.reorder(list, ProductImage::id, ProductImage::withDisplayOrder, orderedIds,
                ProductErrorCode.INVALID_IMAGE_ORDER));
    }
}
