package com.ecsite.module.reporting.infrastructure.directory;

import com.ecsite.module.region.api.RegionLookupApi;
import com.ecsite.module.region.api.model.RegionPrefecture;
import com.ecsite.module.reporting.application.port.RegionDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 地域の腐敗防止層
 *
 * <p>地域モジュールの API を呼び、都道府県のコードと名前の対応に変換する。
 */
@Component
@RequiredArgsConstructor
class RegionApiDirectory implements RegionDirectory {

    /**
     * 地域のマスターデータの参照
     */
    private final RegionLookupApi regionLookupApi;

    /**
     * 都道府県の名前を、地域の API で引く。
     *
     * @return 都道府県のコード → 都道府県名
     */
    @Override
    public Map<String, String> prefectureNames() {
        return regionLookupApi.prefectures().stream()
                .collect(Collectors.toMap(RegionPrefecture::code, RegionPrefecture::name));
    }
}
