package com.ecsite.module.media.infrastructure.persistence;

import com.ecsite.RestoreFailures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.ecsite.RestoreFailures.RECORDS;
import static com.ecsite.RestoreFailures.assertValueIsHidden;
import static com.ecsite.jooq.media.Tables.ASSET;

/**
 * アセットを DB の値から戻すときの失敗の単体テスト
 *
 * <p>例外の出力に DB の値が出ないことを確認する。
 */
class AssetRestoreFailureTest {

    @Test
    @DisplayName("画像の形式が列挙にないとき、例外の出力に値が出ない")
    void hidesFormat() {
        var value = RestoreFailures.value();
        var asset = RECORDS.newRecord(ASSET);
        asset.setFormat(value);
        var repository = new JooqAssetRepository(new RestoreFailures().with("asset", asset).dsl());

        assertValueIsHidden(() -> repository.findAllById(List.of(UUID.randomUUID())), value);
    }
}
