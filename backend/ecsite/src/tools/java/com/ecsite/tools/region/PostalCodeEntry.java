package com.ecsite.tools.region;

import java.util.Objects;

/**
 * 郵便番号の表の 1 行（docs/design/modules/region.md「マスターデータ」）
 *
 * @param postalCode       郵便番号（数字 7 桁）
 * @param municipalityCode 市区町村のコード
 * @param townName         町域名（注記を除いたもの。注記だけなら空）
 * @param displayOrder     同じ郵便番号の中の並び順（1 から）
 */
public record PostalCodeEntry(String postalCode, String municipalityCode, String townName, int displayOrder) {

    /**
     * 郵便番号の表の 1 行を作る。文字列の項目は null を認めない
     */
    public PostalCodeEntry {
        Objects.requireNonNull(postalCode, "postalCode");
        Objects.requireNonNull(municipalityCode, "municipalityCode");
        Objects.requireNonNull(townName, "townName");
    }
}
