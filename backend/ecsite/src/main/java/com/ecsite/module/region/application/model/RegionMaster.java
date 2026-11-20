package com.ecsite.module.region.application.model;

import com.ecsite.module.region.domain.vo.Municipality;
import com.ecsite.module.region.domain.vo.Prefecture;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 地域のマスターデータ（都道府県の全件と市区町村の全件）
 *
 * <p>作るときに、コードで引く索引を組み立てて持つ。
 */
public final class RegionMaster {

    /**
     * コードの順の都道府県
     */
    private final List<Prefecture> prefectures;

    /**
     * 都道府県のコード → 都道府県
     */
    private final Map<String, Prefecture> prefectureByCode;

    /**
     * 都道府県のコード → コードの順の市区町村
     */
    private final Map<String, List<Municipality>> municipalitiesByPrefecture;

    /**
     * 市区町村のコード → 市区町村と、属する都道府県
     */
    private final Map<String, MunicipalityView> municipalityByCode;

    /**
     * 全件から作る。
     *
     * @param prefectures    コードの順の都道府県
     * @param municipalities コードの順の市区町村
     */
    public RegionMaster(Collection<Prefecture> prefectures, Collection<Municipality> municipalities) {
        this.prefectures = List.copyOf(prefectures);
        this.prefectureByCode = new LinkedHashMap<>();
        this.prefectures.forEach(prefecture -> prefectureByCode.put(prefecture.code(), prefecture));
        this.municipalitiesByPrefecture = municipalities.stream()
                .collect(Collectors.groupingBy(Municipality::prefectureCode, LinkedHashMap::new,
                        Collectors.toUnmodifiableList()));
        this.municipalityByCode = new LinkedHashMap<>();
        for (Municipality municipality : municipalities) {
            var prefecture = prefectureByCode.get(municipality.prefectureCode());
            if (prefecture != null) {
                municipalityByCode.put(municipality.code(), new MunicipalityView(prefecture, municipality));
            }
        }
    }

    /**
     * 都道府県の一覧を返す。
     *
     * @return コードの順の都道府県
     */
    public List<Prefecture> prefectures() {
        return prefectures;
    }

    /**
     * 都道府県をコードで引く。
     *
     * @param code 都道府県のコード
     * @return 都道府県。なければ空
     */
    public Optional<Prefecture> findPrefecture(String code) {
        return Optional.ofNullable(prefectureByCode.get(code));
    }

    /**
     * 都道府県と、その市区町村の一覧を返す。
     *
     * @param prefectureCode 都道府県のコード
     * @return 都道府県と、コードの順の市区町村。都道府県がなければ空
     */
    public Optional<PrefectureMunicipalitiesView> municipalitiesOf(String prefectureCode) {
        return findPrefecture(prefectureCode).map(prefecture -> new PrefectureMunicipalitiesView(prefecture,
                municipalitiesByPrefecture.getOrDefault(prefectureCode, List.of())));
    }

    /**
     * 市区町村と、属する都道府県をコードで引く。
     *
     * @param code 市区町村のコード
     * @return 都道府県と市区町村。なければ空
     */
    public Optional<MunicipalityView> findMunicipality(String code) {
        return Optional.ofNullable(municipalityByCode.get(code));
    }
}
