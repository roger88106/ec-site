package com.ecsite.module.region.infrastructure.source;

import com.ecsite.module.region.domain.Municipality;
import com.ecsite.module.region.domain.Prefecture;

import java.util.List;
import java.util.Set;

/**
 * 郵便番号のデータから作ったマスターデータ
 *
 * @param prefectures    都道府県（コードの順）
 * @param municipalities 市区町村（コードの順）
 * @param postalCodes    郵便番号の表の行（郵便番号と並び順の順）
 */
public record PostalCodeData(List<Prefecture> prefectures, List<Municipality> municipalities,
                             List<PostalCodeEntry> postalCodes) {

    /**
     * 郵便番号の表を、指定した郵便番号だけに限る。都道府県と市区町村は全件のまま
     *
     * @param postalCodes 残す郵便番号
     * @return 限ったデータ
     */
    public PostalCodeData restrictPostalCodes(Set<String> postalCodes) {
        return new PostalCodeData(prefectures, municipalities,
                this.postalCodes.stream().filter(entry -> postalCodes.contains(entry.postalCode())).toList());
    }
}
