package com.ecsite.common.model;

import org.jmolecules.ddd.annotation.ValueObject;

import java.util.Objects;
import java.util.UUID;

/**
 * 操作した運営者（運営者の realm のアカウント。AC-142、plan 003 第 3.5 節）。<br />
 * 氏名は操作の時点の写しで、記録に残す（運営者の氏名が後で変わっても、記録は変わらない。constitution 7.1）。
 *
 * @param id   運営者 ID（token の subject）
 * @param name 氏名（姓 名）
 */
@ValueObject
public record Operator(UUID id, String name) {

    /**
     * コンストラクタ。
     *
     * @param id   運営者 ID
     * @param name 氏名
     */
    public Operator {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
    }
}
