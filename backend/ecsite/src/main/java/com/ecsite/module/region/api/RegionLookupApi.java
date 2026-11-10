package com.ecsite.module.region.api;

import com.ecsite.module.region.api.model.RegionMunicipality;
import com.ecsite.module.region.api.model.RegionPrefecture;
import com.ecsite.module.region.application.RegionQueryService;
import com.ecsite.module.region.domain.Municipality;
import com.ecsite.module.region.domain.Prefecture;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 地域モジュールの公開の API
 *
 * <p>都道府県と市区町村を参照するだけで、EC に固有の判断を持たない（別のサービスに切り出せるようにするため）。
 */
@Component
@RequiredArgsConstructor
public class RegionLookupApi {

    /**
     * 地域のマスターデータの参照
     */
    private final RegionQueryService queryService;

    /**
     * 都道府県の一覧
     *
     * @return コードの順の都道府県
     */
    public List<RegionPrefecture> prefectures() {
        return queryService.prefectures().stream().map(RegionLookupApi::toPrefecture).toList();
    }

    /**
     * 都道府県をコードで引く
     *
     * @param code 都道府県のコード
     * @return 都道府県。なければ空
     */
    public Optional<RegionPrefecture> findPrefecture(@NonNull String code) {
        return queryService.findPrefecture(code).map(RegionLookupApi::toPrefecture);
    }

    /**
     * 市区町村をコードで引く
     *
     * @param code 市区町村のコード
     * @return 市区町村と、属する都道府県。なければ空
     */
    public Optional<RegionMunicipality> findMunicipality(@NonNull String code) {
        return queryService.findMunicipality(code)
                .map(found -> toMunicipality(found.prefecture(), found.municipality()));
    }

    /**
     * 公開する都道府県の型に変換する
     *
     * @param prefecture 都道府県
     * @return 公開する都道府県
     */
    private static RegionPrefecture toPrefecture(Prefecture prefecture) {
        return new RegionPrefecture(prefecture.code(), prefecture.name());
    }

    /**
     * 公開する市区町村の型に変換する
     *
     * @param prefecture   属する都道府県
     * @param municipality 市区町村
     * @return 公開する市区町村
     */
    private static RegionMunicipality toMunicipality(Prefecture prefecture, Municipality municipality) {
        return new RegionMunicipality(municipality.code(), municipality.name(), prefecture.code(), prefecture.name());
    }
}
