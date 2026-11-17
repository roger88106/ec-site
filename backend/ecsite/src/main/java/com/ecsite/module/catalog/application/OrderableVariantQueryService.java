package com.ecsite.module.catalog.application;

import com.ecsite.module.catalog.application.model.view.OrderableVariantView;
import com.ecsite.module.catalog.application.port.OrderableVariantQueryRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 注文に使うバリアントの情報の参照
 *
 * <p>表示されない商品・販売停止のバリアントも返す。
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
    public Map<UUID, OrderableVariantView> findVariants(@NonNull Collection<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        return queryRepository.findVariants(variantIds, clock.instant());
    }
}
