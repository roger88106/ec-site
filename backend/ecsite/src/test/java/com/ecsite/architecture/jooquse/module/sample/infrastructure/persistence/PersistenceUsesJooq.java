package com.ecsite.architecture.jooquse.module.sample.infrastructure.persistence;

import org.jooq.DSLContext;

/**
 * jOOQ の置き場所の検査の例：infrastructure/persistence に見立てたクラスから jOOQ を使う（規則に合う）。<br />
 * 構成の検査が、使ってよい場所を違反にしないことを確認するための例であり、本番のコードではない。
 */
public class PersistenceUsesJooq {

    /**
     * persistence で使える jOOQ の入口
     */
    DSLContext dsl;
}
