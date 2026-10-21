package com.ecsite.module.region.api;

/**
 * 郵便番号の住所
 *
 * @param postalCode   郵便番号（7 桁）
 * @param municipality 市区町村と都道府県
 * @param townName     町域名（注記を除く。注記だけなら空）
 */
public record RegionPostalAddress(String postalCode, RegionMunicipality municipality, String townName) {
}
