package com.ecsite.module.region.application.port;

import com.ecsite.module.region.domain.Municipality;
import com.ecsite.module.region.domain.PostalAddress;
import com.ecsite.module.region.domain.Prefecture;

import java.util.List;
import java.util.Optional;

/**
 * 地域のマスターデータのリポジトリ（参照だけ）
 */
public interface RegionRepository {

    /**
     * 都道府県の一覧
     *
     * @return コードの順の都道府県
     */
    List<Prefecture> findPrefectures();

    /**
     * 都道府県をコードで引く
     *
     * @param code 都道府県のコード
     * @return 都道府県。なければ空
     */
    Optional<Prefecture> findPrefecture(String code);

    /**
     * 都道府県の市区町村の一覧
     *
     * @param prefectureCode 都道府県のコード
     * @return コードの順の市区町村。都道府県がなければ空の一覧
     */
    List<Municipality> findMunicipalities(String prefectureCode);

    /**
     * 市区町村をコードで引く
     *
     * @param code 市区町村のコード
     * @return 市区町村。なければ空
     */
    Optional<Municipality> findMunicipality(String code);

    /**
     * 郵便番号の住所
     *
     * @param postalCode 郵便番号（数字 7 桁）
     * @return 並び順の住所。なければ空の一覧
     */
    List<PostalAddress> findPostalAddresses(String postalCode);
}
