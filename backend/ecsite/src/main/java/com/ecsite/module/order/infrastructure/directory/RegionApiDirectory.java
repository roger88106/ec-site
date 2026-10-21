package com.ecsite.module.order.infrastructure.directory;

import com.ecsite.module.order.application.port.RegionDirectory;
import com.ecsite.module.region.api.RegionLookupApi;
import com.ecsite.module.region.api.RegionPrefecture;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 地域の腐敗防止層の実装。地域モジュールの API（{@link RegionLookupApi}）を呼ぶ。
 */
@Component
@RequiredArgsConstructor
class RegionApiDirectory implements RegionDirectory {

    /**
     * 地域のマスターデータの参照
     */
    private final RegionLookupApi regionLookupApi;

    @Override
    public Optional<String> findPrefectureName(String prefectureCode) {
        return regionLookupApi.findPrefecture(prefectureCode).map(RegionPrefecture::name);
    }

    @Override
    public Optional<Municipality> findMunicipality(String municipalityCode) {
        return regionLookupApi.findMunicipality(municipalityCode)
                .map(found -> new Municipality(found.code(), found.name(), found.prefectureCode()));
    }
}
