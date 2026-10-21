package com.ecsite.module.region.presentation;

import com.ecsite.api.store.model.Municipality;
import com.ecsite.api.store.model.MunicipalityList;
import com.ecsite.api.store.model.PostalCodeAddress;
import com.ecsite.api.store.model.PostalCodeAddressList;
import com.ecsite.api.store.model.Prefecture;
import com.ecsite.api.store.model.PrefectureList;
import com.ecsite.module.region.domain.PostalAddress;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 地域の API の型への変換（バックエンドの書き方 第 2.2 節）
 */
@Component
class RegionMapper {

    /**
     * 都道府県の一覧のレスポンスに変換する。
     *
     * @param prefectures 都道府県
     * @return 都道府県の一覧のレスポンス
     */
    PrefectureList toPrefectureList(List<com.ecsite.module.region.domain.Prefecture> prefectures) {
        return new PrefectureList().items(prefectures.stream().map(RegionMapper::toPrefecture).toList());
    }

    /**
     * 市区町村の一覧のレスポンスに変換する。
     *
     * @param municipalities 市区町村
     * @return 市区町村の一覧のレスポンス
     */
    MunicipalityList toMunicipalityList(List<com.ecsite.module.region.domain.Municipality> municipalities) {
        return new MunicipalityList().items(municipalities.stream().map(RegionMapper::toMunicipality).toList());
    }

    /**
     * 郵便番号の住所の一覧のレスポンスに変換する。
     *
     * @param addresses 郵便番号の住所
     * @return 郵便番号の住所の一覧のレスポンス
     */
    PostalCodeAddressList toPostalCodeAddressList(List<PostalAddress> addresses) {
        return new PostalCodeAddressList().items(addresses.stream()
                .map(address -> new PostalCodeAddress()
                        .prefecture(toPrefecture(address.prefecture()))
                        .municipality(toMunicipality(address.municipality()))
                        .townName(address.townName()))
                .toList());
    }

    /**
     * 都道府県の型に変換する。
     *
     * @param prefecture 都道府県
     * @return 都道府県の型
     */
    private static Prefecture toPrefecture(com.ecsite.module.region.domain.Prefecture prefecture) {
        return new Prefecture().code(prefecture.code()).name(prefecture.name());
    }

    /**
     * 市区町村の型に変換する。
     *
     * @param municipality 市区町村
     * @return 市区町村の型
     */
    private static Municipality toMunicipality(com.ecsite.module.region.domain.Municipality municipality) {
        return new Municipality().code(municipality.code()).name(municipality.name());
    }
}
