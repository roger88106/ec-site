package com.ecsite.common.persistence;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.util.UUID;

/**
 * PostgreSQL のアドバイザリロック
 *
 * <p>行のないもの（会員、まだ写しのない注文など）を、トランザクションの終わりまで排他にする。
 * キーは、用途の番号と ID のハッシュの 2 つ（{@code pg_advisory_xact_lock(int, int)}）にする。
 * ハッシュが衝突しても短く待つだけで、正しさに影響しない。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdvisoryLocks {

    /**
     * 用途と ID のロックを、トランザクションの終わりまで取る。
     *
     * <p>同じ用途と ID のロックを別のトランザクションが持っていれば、外れるまで待つ。
     *
     * @param dsl   DSLContext
     * @param space 用途
     * @param id    ID
     */
    public static void lock(DSLContext dsl, AdvisoryLockSpace space, UUID id) {
        dsl.select(DSL.function("pg_advisory_xact_lock", Object.class,
                DSL.val(space.key()), DSL.val(hash(id)))).fetch();
    }

    /**
     * ID のハッシュ（ロックのキーの 2 つ目）を返す。
     *
     * @param id ID
     * @return ハッシュ
     */
    public static int hash(UUID id) {
        long bits = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        return (int) (bits >>> 32) ^ (int) bits;
    }
}
