package com.ecsite.architecture.sample.identifier.module.sample.domain;

import java.util.UUID;

/**
 * 識別子の型の検査の例：domain のクラスの、コンストラクターの引数に UUID がある
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public class UuidConstructor {

    /**
     * 例のコンストラクター。
     *
     * @param id UUID の引数（規則に合わない）
     */
    UuidConstructor(UUID id) {
    }
}
