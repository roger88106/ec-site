package com.ecsite.common.model.base;

import org.jmolecules.ddd.types.Identifier;

import java.util.UUID;

/**
 * 識別子の型
 *
 * <p>種類ごとの識別子の型（例：{@code OrderId}）は、UUID を 1 つ持つ record にして、この interface を実装する。
 * JSON では UUID の文字列として書き、読む（{@code EntityIdModule}）。
 */
public interface EntityId extends Identifier {

    /**
     * 識別子の値を返す。
     *
     * @return UUID
     */
    UUID value();
}
