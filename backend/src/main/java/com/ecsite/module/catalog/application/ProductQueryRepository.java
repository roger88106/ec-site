package com.ecsite.module.catalog.application;

import com.ecsite.module.catalog.application.model.view.ProductDetailView;

import java.util.Optional;
import java.util.UUID;

/**
 * 商品照会のクエリリポジトリ。<br />
 * 返すビューの税込価格は空のまま。時刻と税率で決まるので、クエリサービスが設定する。
 */
public interface ProductQueryRepository {

    /**
     * 商品の詳細を取得する。
     *
     * @param id 商品 ID
     * @return 商品の詳細（バリアントは表示順）。なければ空
     */
    Optional<ProductDetailView> findDetailById(UUID id);
}
