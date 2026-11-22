package com.ecsite.architecture.sample.identifier.common.model;

import java.util.UUID;

/**
 * 識別子の型の検査の例：共通のモデルのクラスに、UUID の項目がある
 *
 * <p>共通のモデルも検査の対象であることを確認するための例であり、本番のコードではない。
 *
 * @param ownerId 持ち主の ID（UUID なので規則に合わない）
 */
public record UuidInCommonModel(UUID ownerId) {
}
