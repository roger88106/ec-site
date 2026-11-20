package com.ecsite.module.region.application.port;

import com.ecsite.module.region.domain.vo.Municipality;
import com.ecsite.module.region.domain.vo.PostalAddress;
import com.ecsite.module.region.domain.vo.Prefecture;

import java.util.List;

/**
 * 地域のマスターデータのリポジトリ
 */
public interface RegionRepository {

    /**
     * 都道府県の一覧を返す。
     *
     * @return コードの順の都道府県
     */
    List<Prefecture> findPrefectures();

    /**
     * 全ての市区町村を返す。
     *
     * @return コードの順の市区町村
     */
    List<Municipality> findAllMunicipalities();

    /**
     * 郵便番号の住所を返す。
     *
     * @param postalCode 郵便番号（数字 7 桁）
     * @return 並び順の住所。なければ空の一覧
     */
    List<PostalAddress> findPostalAddresses(String postalCode);
}
