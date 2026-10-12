package com.ecsite.module.order.domain.quote;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * 軸の名前と値（例：色＝白）
 *
 * @param name  軸の名前
 * @param value 軸の値
 */
@ValueObject
public record AxisValue(String name, String value) {
}
