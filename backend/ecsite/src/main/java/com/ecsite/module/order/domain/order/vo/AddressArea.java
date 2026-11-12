package com.ecsite.module.order.domain.order.vo;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * お届け先の都道府県と市区町村
 *
 * <p>コードと、注文の時点の名前の写しを持つ。後でマスターデータが変わっても、写しは変えない。
 *
 * @param prefectureCode   都道府県のコード（2 桁）
 * @param prefectureName   都道府県名
 * @param municipalityCode 市区町村のコード（5 桁）
 * @param municipalityName 市区町村名
 */
@ValueObject
public record AddressArea(String prefectureCode, String prefectureName, String municipalityCode, String municipalityName) {
}
