package com.ecsite.module.order.application.port;

import com.ecsite.module.order.application.model.Municipality;

import java.util.Optional;

/**
 * 地域の腐敗防止層の port
 */
public interface RegionDirectory {

    /**
     * 都道府県の名前をコードで引く。
     *
     * @param prefectureCode 都道府県のコード
     * @return 都道府県名。なければ空
     */
    Optional<String> findPrefectureName(String prefectureCode);

    /**
     * 市区町村をコードで引く。
     *
     * @param municipalityCode 市区町村のコード
     * @return 市区町村。なければ空
     */
    Optional<Municipality> findMunicipality(String municipalityCode);
}
