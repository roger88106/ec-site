package com.ecsite.architecture.sample.identifier.module.sample.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 識別子の型の検査の例：domain のクラスの、型引数と配列に UUID がある
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public class UuidTypeArguments {

    /**
     * 型引数の UUID の項目（規則に合わない）
     */
    List<UUID> ids;

    /**
     * 例のメソッド。
     *
     * @param byId Map のキーの UUID の引数（規則に合わない）
     * @return 型引数の UUID の戻り値（規則に合わない）
     */
    Optional<UUID> find(Map<UUID, String> byId) {
        return Optional.empty();
    }

    /**
     * 配列の UUID を返す例のメソッド。
     *
     * @return 配列の UUID の戻り値（規則に合わない）
     */
    UUID[] array() {
        return new UUID[0];
    }
}
