package com.ecsite.module.catalog.application.port;

import com.ecsite.module.catalog.api.OrderableVariant;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 注文に使うバリアントの情報のクエリリポジトリ（カート・注文向け）
 */
public interface OrderableVariantQueryRepository {

    /**
     * 複数のバリアントについて、注文に使う情報を取得する。表示されない商品のバリアントも含める。
     *
     * @param variantIds バリアント ID（1 つ以上）
     * @param now        表示されるかを判定する時刻
     * @return バリアント ID → 情報。存在しないバリアントは含めない
     */
    Map<UUID, OrderableVariant> findVariants(Collection<UUID> variantIds, Instant now);
}
