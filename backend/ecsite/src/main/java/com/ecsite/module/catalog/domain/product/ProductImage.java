package com.ecsite.module.catalog.domain.product;

import com.ecsite.common.model.id.AssetId;
import com.ecsite.module.catalog.domain.product.vo.ProductImageId;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.Identity;

/**
 * 商品画像
 *
 * <p>商品集約の一部。ファイルはメディアモジュールのアセットで、ここにはアセット ID だけを持つ。
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
public final class ProductImage {

    /**
     * 画像 ID
     */
    @Identity
    @EqualsAndHashCode.Include
    @NonNull
    private final ProductImageId id;

    /**
     * メディアモジュールのアセット ID
     */
    @NonNull
    private final AssetId assetId;

    /**
     * 商品の中の表示順（先頭が代表画像）
     */
    private final int displayOrder;

    /**
     * コンストラクタ
     *
     * <p>表示順が負のとき、IllegalArgumentException を投げる。
     *
     * @param id           画像 ID
     * @param assetId      メディアモジュールのアセット ID
     * @param displayOrder 商品の中の表示順
     */
    public ProductImage(@NonNull ProductImageId id, @NonNull AssetId assetId, int displayOrder) {
        if (displayOrder < 0) {
            throw new IllegalArgumentException("表示順は 0 以上にする");
        }
        this.id = id;
        this.assetId = assetId;
        this.displayOrder = displayOrder;
    }

    /**
     * 表示順を変えた画像を返す。
     *
     * @param displayOrder 表示順
     * @return 画像
     */
    ProductImage withDisplayOrder(int displayOrder) {
        return new ProductImage(id, assetId, displayOrder);
    }
}
