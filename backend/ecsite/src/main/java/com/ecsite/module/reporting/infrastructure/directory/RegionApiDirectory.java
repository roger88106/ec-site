package com.ecsite.module.reporting.infrastructure.directory;

import com.ecsite.module.region.api.RegionLookupApi;
import com.ecsite.module.region.api.RegionPrefecture;
import com.ecsite.module.reporting.application.port.RegionDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

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
    public Map<String, String> prefectureNames() {
        return regionLookupApi.prefectures().stream()
                .collect(Collectors.toMap(RegionPrefecture::code, RegionPrefecture::name));
    }
}
