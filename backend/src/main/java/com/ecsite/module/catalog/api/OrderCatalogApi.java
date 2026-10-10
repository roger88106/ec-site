package com.ecsite.module.catalog.api;

import com.ecsite.module.catalog.application.OrderableVariantQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * カートと注文に公開する商品カタログの API（docs/modules/catalog.md「他モジュールへの公開」）。<br />
 * 表示されない商品・販売停止のバリアントも返し、判定は呼ぶ側が行う（カートの表示で理由を示すため。AC-80）。
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderCatalogApi {

    /**
     * 注文に使うバリアントの情報のクエリリポジトリ
     */
    private final OrderableVariantQueryRepository queryRepository;

    /**
     * 時計（表示されるかの判定）
     */
    private final Clock clock;

    /**
     * 複数のバリアントについて、注文に使う情報を返す。
     *
     * @param variantIds バリアント ID
     * @return バリアント ID → 情報。存在しないバリアントは含めない
     */
    public Map<UUID, OrderableVariant> findVariants(Collection<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        return queryRepository.findVariants(variantIds, clock.instant());
    }
}
