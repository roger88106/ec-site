package com.ecsite.architecture.sample.identifier.module.sample.infrastructure;

import java.util.UUID;

/**
 * 識別子の型の検査の例：infrastructure は UUID を使ってよい
 *
 * <p>構成の検査が、検査の対象の外のパッケージを違反にしないことを確認するための例であり、本番のコードではない。
 */
public class UsesUuid {

    /**
     * UUID の項目（infrastructure なので規則に合う）
     */
    UUID id;
}
