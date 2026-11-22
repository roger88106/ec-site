package com.ecsite.architecture.sample.identifier.module.sample.api;

import java.util.UUID;

/**
 * 識別子の型の検査の例：api のクラスに、UUID の項目がある
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public class ExampleApi {

    /**
     * UUID の項目（規則に合わない）
     */
    UUID memberId;
}
