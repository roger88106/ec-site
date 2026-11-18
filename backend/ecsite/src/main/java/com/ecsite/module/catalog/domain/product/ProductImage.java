package com.ecsite.module.catalog.domain.product;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.Identity;

import java.util.UUID;

/**
 * 商品画像
 *
 * <p>商品集約の一部。ファイルはメディアモジュールのアセットで、ここにはアセット ID だけを持つ。
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@AllArgsConstructor
@Entity
public final class ProductImage {

    /**
     * 画像 ID
     */
    @Identity
    @EqualsAndHashCode.Include
    @NonNull
    private final UUID id;

    /**
     * メディアモジュールのアセット ID
     */
    @NonNull
    private final UUID assetId;

    /**
     * 商品の中の表示順（先頭が代表画像）
     */
    private final int displayOrder;

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
