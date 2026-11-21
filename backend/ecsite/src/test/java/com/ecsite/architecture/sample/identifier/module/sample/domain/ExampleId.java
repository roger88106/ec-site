package com.ecsite.architecture.sample.identifier.module.sample.domain;

import com.ecsite.common.model.base.EntityId;

import java.util.UUID;

/**
 * 識別子の型の検査の例：識別子の型は、UUID の項目と value() を持ってよい
 *
 * <p>構成の検査が、例外を違反にしないことを確認するための例であり、本番のコードではない。
 *
 * @param value 識別子の値
 */
public record ExampleId(UUID value) implements EntityId {
}
