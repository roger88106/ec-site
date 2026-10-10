package com.ecsite.module.catalog.domain.product;

import lombok.NonNull;
import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.Identity;

import java.util.UUID;

/**
 * 商品画像（商品集約の一部。AC-37）。<br />
 * ファイルはオブジェクトストレージの配信のバケットに置き、ここにはキーだけを持つ（plan 001 第 7 節）。
 *
 * @param id           画像 ID
 * @param storageKey   配信のバケットのキー。上書きしない
 * @param displayOrder 商品の中の表示順（先頭が代表画像）
 */
@Entity
public record ProductImage(@Identity @NonNull UUID id, @NonNull String storageKey, int displayOrder) {

    /**
     * 表示順を変えた画像を返す。
     *
     * @param displayOrder 表示順
     * @return 画像
     */
    ProductImage withDisplayOrder(int displayOrder) {
        return new ProductImage(id, storageKey, displayOrder);
    }
}
