package com.ecsite.module.order.application.port;

import com.ecsite.module.order.domain.quote.VariantInfo;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 商品カタログの腐敗防止層（バックエンドのアーキテクチャ 第 1 節）
 */
public interface ProductDirectory {

    /**
     * 複数のバリアントの情報を返す。表示されない商品・販売停止のバリアントも含める。
     *
     * @param variantIds バリアント ID
     * @return バリアント ID → 情報。存在しないバリアントは含めない
     */
    Map<UUID, VariantInfo> findVariants(Collection<UUID> variantIds);
}
