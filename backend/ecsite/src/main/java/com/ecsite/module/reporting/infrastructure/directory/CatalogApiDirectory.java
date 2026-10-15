package com.ecsite.module.reporting.infrastructure.directory;

import com.ecsite.module.catalog.api.ReportCatalogApi;
import com.ecsite.module.reporting.application.port.CatalogDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 商品カタログの腐敗防止層の実装。商品カタログモジュールの API（{@link ReportCatalogApi}）を呼ぶ。
 */
@Component
@RequiredArgsConstructor
class CatalogApiDirectory implements CatalogDirectory {

    /**
     * レポート向けの商品カタログの API
     */
    private final ReportCatalogApi reportCatalogApi;

    @Override
    public Map<UUID, UUID> productIdsOf(Collection<UUID> variantIds) {
        return reportCatalogApi.productIdsOf(variantIds);
    }
}
