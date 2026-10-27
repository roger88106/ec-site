package com.ecsite.architecture.jooquse.module.sample.application;

import org.jooq.DSLContext;

/**
 * jOOQ の置き場所の検査の例：application 層に見立てたクラスから jOOQ を使う（規則に合わない）。<br />
 * 構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public class ApplicationUsesJooq {

    /**
     * application 層では使えない jOOQ の入口
     */
    DSLContext dsl;
}
