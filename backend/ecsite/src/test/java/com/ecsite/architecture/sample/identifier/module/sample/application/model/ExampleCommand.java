package com.ecsite.architecture.sample.identifier.module.sample.application.model;

import java.util.UUID;

/**
 * 識別子の型の検査の例：application のクラスに、UUID の項目がある
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 *
 * @param productId 商品の ID（UUID なので規則に合わない）
 */
public record ExampleCommand(UUID productId) {
}
