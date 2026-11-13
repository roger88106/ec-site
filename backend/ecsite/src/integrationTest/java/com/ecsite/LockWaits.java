package com.ecsite;

import com.ecsite.common.persistence.AdvisoryLockSpace;
import com.ecsite.common.persistence.AdvisoryLocks;
import org.jooq.DSLContext;

import java.util.UUID;

import static org.awaitility.Awaitility.await;

/**
 * 別のセッションがロックを待ち始めるのを待つ補助。同時の処理のテストで、待ちが現れたことを確認してから次に進むために使う。<br />
 * 決まった時間だけ待つ代わりに、PostgreSQL のロックの状態（pg_locks）を見る。
 */
public final class LockWaits {

    private LockWaits() {
    }

    /**
     * このトランザクションのロックを、別のセッションが待ち始めるまで待つ
     *
     * @param dsl このトランザクションの接続で動く DSLContext
     */
    public static void awaitBlockedByThisTransaction(DSLContext dsl) {
        var pid = dsl.fetchValue("select pg_backend_pid()");
        await().atMost(AsyncEvents.TIMEOUT).until(() -> dsl.fetchExists(dsl.selectOne().from("pg_locks")
                .where("not granted and ? = any(pg_blocking_pids(pid))", pid)));
    }

    /**
     * 会員のアドバイザリロック（JooqOrderRepository.lockForMember と同じキー）を、未取得のまま待つセッションが現れるまで待つ
     *
     * @param dsl      DSLContext
     * @param memberId 会員 ID
     */
    public static void awaitMemberLockWaiter(DSLContext dsl, UUID memberId) {
        // 2 つの int のキーは、classid と objid に符号なしで入る（objsubid は 2）
        var space = Integer.toUnsignedLong(AdvisoryLockSpace.ORDER.key());
        var hash = Integer.toUnsignedLong(AdvisoryLocks.hash(memberId));
        await().atMost(AsyncEvents.TIMEOUT).until(() -> dsl.fetchExists(dsl.selectOne().from("pg_locks")
                .where("locktype = 'advisory' and not granted and objsubid = 2 and classid::bigint = ? and objid::bigint = ?",
                        space, hash)));
    }
}
