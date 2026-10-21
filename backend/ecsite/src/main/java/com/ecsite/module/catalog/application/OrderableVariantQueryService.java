package com.ecsite.module.catalog.application;

import com.ecsite.module.catalog.api.OrderableVariant;
import com.ecsite.module.catalog.application.port.OrderableVariantQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * カートと注文に使うバリアントの情報の参照（docs/design/modules/catalog.md「他のモジュールへの公開」）。<br />
 * 表示されない商品・販売停止のバリアントも返し、判定は呼ぶ側が行う（カートの表示で、入れられない理由を示すため）。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderableVariantQueryService {

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
