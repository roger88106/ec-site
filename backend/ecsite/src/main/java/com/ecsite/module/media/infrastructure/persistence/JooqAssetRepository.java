package com.ecsite.module.media.infrastructure.persistence;

import com.ecsite.common.model.base.RestoreGuard;
import com.ecsite.common.model.id.AssetId;
import com.ecsite.jooq.media.tables.records.AssetRecord;
import com.ecsite.module.media.application.port.AssetRepository;
import com.ecsite.module.media.domain.Asset;
import com.ecsite.module.media.domain.ImageFormat;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

import static com.ecsite.jooq.media.Tables.ASSET;

/**
 * jOOQ のアセットのリポジトリ
 */
@Repository
@RequiredArgsConstructor
class JooqAssetRepository implements AssetRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * アセットを記録する。
     *
     * @param asset アセット
     */
    @Override
    public void insert(Asset asset) {
        dsl.insertInto(ASSET)
                .set(ASSET.ID, asset.id().value())
                .set(ASSET.FORMAT, asset.format().name())
                .set(ASSET.STORAGE_KEY, asset.storageKey())
                .set(ASSET.CREATED_AT, asset.createdAt())
                .execute();
    }

    /**
     * アセットをまとめて取得する。
     *
     * @param ids アセット ID
     * @return アセット。ないものは含めない
     */
    @Override
    public List<Asset> findAllById(Collection<AssetId> ids) {
        return dsl.selectFrom(ASSET).where(ASSET.ID.in(ids.stream().map(AssetId::value).toList())).fetch(JooqAssetRepository::toAsset);
    }

    /**
     * アセットの記録をまとめて消す。
     *
     * <p>ないものは無視する。削除した行を {@code DELETE ... RETURNING} で受け取り、実際に削除したアセットだけを返す。
     *
     * @param ids アセット ID
     * @return 実際に削除したアセット。ないものは含めない
     */
    @Override
    public List<Asset> deleteAllById(Collection<AssetId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return dsl.deleteFrom(ASSET).where(ASSET.ID.in(ids.stream().map(AssetId::value).toList())).returning().fetch(JooqAssetRepository::toAsset);
    }

// ============================== プライベートメソッド ==============================

    /**
     * アセットのレコードを、アセットに変換する。
     */
    private static Asset toAsset(AssetRecord record) {
        return new Asset(
                new AssetId(record.getId()),
                RestoreGuard.restore("asset", () -> ImageFormat.valueOf(record.getFormat())),
                record.getStorageKey(),
                record.getCreatedAt());
    }
}
