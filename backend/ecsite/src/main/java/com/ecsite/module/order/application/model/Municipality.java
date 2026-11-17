package com.ecsite.module.order.application.model;

/**
 * 市区町村
 *
 * @param code           市区町村のコード
 * @param name           市区町村名
 * @param prefectureCode 属する都道府県のコード
 */
public record Municipality(String code, String name, String prefectureCode) {
}
