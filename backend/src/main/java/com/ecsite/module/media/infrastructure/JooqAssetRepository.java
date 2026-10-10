package com.ecsite.module.media.infrastructure;

import com.ecsite.jooq.media.tables.records.AssetRecord;
import com.ecsite.module.media.service.Asset;
import com.ecsite.module.media.service.AssetRepository;
import com.ecsite.module.media.service.ImageFormat;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.ecsite.jooq.media.Tables.ASSET;

/**
 * アセットのリポジトリの jOOQ の実装
 */
@Repository
@RequiredArgsConstructor
class JooqAssetRepository implements AssetRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    @Override
    public void insert(Asset asset) {
        dsl.insertInto(ASSET)
                .set(ASSET.ID, asset.id())
                .set(ASSET.FORMAT, asset.format().name())
                .set(ASSET.STORAGE_KEY, asset.storageKey())
                .set(ASSET.CREATED_AT, asset.createdAt())
                .execute();
    }

    @Override
    public List<Asset> findAllById(Collection<UUID> ids) {
        return dsl.selectFrom(ASSET).where(ASSET.ID.in(ids)).fetch(JooqAssetRepository::toAsset);
    }

    @Override
    public void deleteAllById(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        dsl.deleteFrom(ASSET).where(ASSET.ID.in(ids)).execute();
    }

// ============================== プライベートメソッド ==============================

    /**
     * アセットのレコードを、アセットに変換する。
     */
    private static Asset toAsset(AssetRecord record) {
        return new Asset(record.getId(), ImageFormat.valueOf(record.getFormat()), record.getStorageKey(),
                record.getCreatedAt());
    }
}
