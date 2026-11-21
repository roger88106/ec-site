package com.ecsite.architecture.sample.identifier.module.sample.domain;

import com.ecsite.common.model.base.EntityId;

import java.util.UUID;

/**
 * 識別子の型の検査の例：識別子の型でも、value() 以外のメソッドの UUID は違反になる
 *
 * <p>構成の検査が、例外を value() の項目とメソッドだけにしていることを確認するための例であり、本番のコードではない。
 *
 * @param value 識別子の値
 */
public record ExampleIdWithOther(UUID value) implements EntityId {

    /**
     * 例のメソッド。
     *
     * @return UUID の戻り値（規則に合わない）
     */
    UUID other() {
        return value;
    }
}
