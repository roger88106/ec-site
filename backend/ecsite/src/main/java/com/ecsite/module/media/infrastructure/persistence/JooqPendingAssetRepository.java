package com.ecsite.module.media.infrastructure.persistence;

import com.ecsite.jooq.media.tables.records.PendingAssetRecord;
import com.ecsite.module.media.application.port.PendingAssetRepository;
import com.ecsite.module.media.domain.PendingAsset;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.jooq.media.Tables.PENDING_ASSET;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.select;

/**
 * jOOQ の仮のアセットのリポジトリ
 */
@Repository
@RequiredArgsConstructor
class JooqPendingAssetRepository implements PendingAssetRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * {@inheritDoc}
     */
    @Override
    public void insert(PendingAsset pendingAsset) {
        dsl.insertInto(PENDING_ASSET)
                .set(PENDING_ASSET.ID, pendingAsset.id())
                .set(PENDING_ASSET.STORAGE_KEY, pendingAsset.storageKey())
                .set(PENDING_ASSET.PREPARED_AT, pendingAsset.preparedAt())
                .execute();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean deleteById(UUID id) {
        return dsl.deleteFrom(PENDING_ASSET).where(PENDING_ASSET.ID.eq(id)).execute() > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PendingAsset> deleteExpiredBefore(Instant preparedBefore, int limit) {
        // 選ぶのと削除するのを 1 文にし、他のトランザクションがロックしている行はスキップする（次の回で削除する）
        // ※選ぶ行は MATERIALIZED の CTE で 1 回だけ確定させる。IN の副問い合わせに直接書くと、実行計画によっては
        //   1 文の中で繰り返し評価され、前の評価でロックした行をスキップして次の行を選ぶので、件数の上限より多く削除しうる
        var targets = name("targets").fields("id").asMaterialized(select(PENDING_ASSET.ID)
                .from(PENDING_ASSET)
                .where(PENDING_ASSET.PREPARED_AT.lt(preparedBefore))
                .orderBy(PENDING_ASSET.PREPARED_AT, PENDING_ASSET.ID)
                .limit(limit)
                .forUpdate()
                .skipLocked());
        return dsl.with(targets)
                .delete(PENDING_ASSET)
                .where(PENDING_ASSET.ID.in(select(targets.field("id", UUID.class)).from(targets)))
                .returning()
                .fetch(JooqPendingAssetRepository::toPendingAsset);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 仮のアセットのレコードを、仮のアセットに変換する。
     */
    private static PendingAsset toPendingAsset(PendingAssetRecord record) {
        return new PendingAsset(record.getId(), record.getStorageKey(), record.getPreparedAt());
    }
}
