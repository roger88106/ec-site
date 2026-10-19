package com.ecsite.module.order.application.port;

import java.util.Optional;

/**
 * 地域の腐敗防止層（docs/backend/modules/order.md「注文の成立」）。<br />
 * 地域のモジュールを別のサービスに切り出しても、注文はこの型だけを使う。
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

    /**
     * 市区町村
     *
     * @param code           市区町村のコード
     * @param name           市区町村名
     * @param prefectureCode 属する都道府県のコード
     */
    record Municipality(String code, String name, String prefectureCode) {
    }
}
