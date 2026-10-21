package com.ecsite.module.region.api;

import com.ecsite.module.region.application.RegionQueryService;
import com.ecsite.module.region.domain.Municipality;
import com.ecsite.module.region.domain.Prefecture;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 他のモジュールに公開する、地域のマスターデータの参照（docs/design/modules/region.md「他のモジュールへの公開」）。<br />
 * 参照だけで、EC に固有の判断を持たない。使う側は腐敗防止層で包む（別のサービスに切り出せるようにするため）。
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
    public Optional<RegionPrefecture> findPrefecture(String code) {
        return queryService.findPrefecture(code).map(RegionLookupApi::toPrefecture);
    }

    /**
     * 都道府県の市区町村の一覧
     *
     * @param prefectureCode 都道府県のコード
     * @return コードの順の市区町村。都道府県がなければ空
     */
    public Optional<List<RegionMunicipality>> municipalitiesOf(String prefectureCode) {
        return queryService.municipalitiesOf(prefectureCode)
                .map(found -> found.municipalities().stream()
                        .map(municipality -> toMunicipality(found.prefecture(), municipality))
                        .toList());
    }

    /**
     * 市区町村をコードで引く
     *
     * @param code 市区町村のコード
     * @return 市区町村と、属する都道府県。なければ空
     */
    public Optional<RegionMunicipality> findMunicipality(String code) {
        return queryService.findMunicipality(code)
                .map(found -> toMunicipality(found.prefecture(), found.municipality()));
    }

    /**
     * 郵便番号の住所
     *
     * @param postalCode 郵便番号（数字 7 桁）
     * @return 並び順の住所。なければ空の一覧
     */
    public List<RegionPostalAddress> findByPostalCode(String postalCode) {
        return queryService.findPostalAddresses(postalCode).stream()
                .map(address -> new RegionPostalAddress(address.postalCode(),
                        toMunicipality(address.prefecture(), address.municipality()), address.townName()))
                .toList();
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
