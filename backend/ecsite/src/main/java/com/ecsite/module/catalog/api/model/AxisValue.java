package com.ecsite.module.catalog.api.model;

/**
 * 軸の名前と値
 *
 * @param name  軸の名前（例：色）
 * @param value 軸の値（例：白）
 */
public record AxisValue(String name, String value) {
}
