package com.ecsite.module.region.api;

/**
 * 都道府県
 *
 * @param code 都道府県のコード（2 桁）
 * @param name 都道府県名
 */
public record RegionPrefecture(String code, String name) {
}
