package com.ecsite.module.catalog.domain.product;

import lombok.NonNull;
import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.Identity;

import java.util.UUID;

/**
 * 商品画像（商品集約の一部）。<br />
 * ファイルはメディアモジュールのアセットで、ここにはアセット ID だけを持つ（docs/backend/modules/catalog.md「商品画像」）。
 *
 * @param id           画像 ID
 * @param assetId      メディアモジュールのアセット ID
 * @param displayOrder 商品の中の表示順（先頭が代表画像）
 */
@Entity
public record ProductImage(@Identity @NonNull UUID id, @NonNull UUID assetId, int displayOrder) {

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
