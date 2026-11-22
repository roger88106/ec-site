package com.ecsite.module.storefront.application.port;

import com.ecsite.common.model.id.VariantId;

import java.util.Collection;
import java.util.Set;

/**
 * 在庫の腐敗防止層の port
 *
 * <p>在庫のあるバリアントの ID を返す。
 */
public interface StockDirectory {

    /**
     * 在庫のあるバリアントを返す。
     *
     * <p>在庫が作られていないバリアントは「在庫なし」とする。
     *
     * @param variantIds バリアント ID
     * @return 指定したうち、在庫のあるバリアントの ID
     */
    Set<VariantId> inStock(Collection<VariantId> variantIds);
}
