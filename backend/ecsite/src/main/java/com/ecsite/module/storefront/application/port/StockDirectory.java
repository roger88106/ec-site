package com.ecsite.module.storefront.application.port;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * 在庫の腐敗防止層の口（constitution 4.1、docs/modules/storefront.md）。<br />
 * 在庫があるバリアントを返す。
 */
public interface StockDirectory {

    /**
     * 在庫のあるバリアントを返す。在庫がまだないバリアントは「在庫なし」とする（docs/modules/inventory.md）。
     *
     * @param variantIds バリアント ID
     * @return 指定したうち、在庫のあるバリアントの ID
     */
    Set<UUID> inStock(Collection<UUID> variantIds);
}
