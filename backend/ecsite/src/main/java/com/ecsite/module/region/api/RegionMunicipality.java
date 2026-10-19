package com.ecsite.module.region.api;

/**
 * 市区町村と、属する都道府県
 *
 * @param code           市区町村のコード（5 桁）
 * @param name           市区町村名
 * @param prefectureCode 都道府県のコード
 * @param prefectureName 都道府県名
 */
public record RegionMunicipality(String code, String name, String prefectureCode, String prefectureName) {
}
