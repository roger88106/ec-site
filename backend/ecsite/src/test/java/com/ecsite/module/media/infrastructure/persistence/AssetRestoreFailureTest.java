package com.ecsite.module.media.infrastructure.persistence;

import com.ecsite.support.RestoreFailures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.ecsite.jooq.media.Tables.ASSET;
import static com.ecsite.support.RestoreFailures.RECORDS;
import static com.ecsite.support.RestoreFailures.assertValueIsHidden;

/**
 * アセットを DB の値から戻すときの失敗の単体テスト
 *
 * <p>例外の出力に DB の値が出ないことを確認する。
 */
class AssetRestoreFailureTest {

    // ============================== テスト ==============================

    @Test
    @DisplayName("画像の形式が列挙にないとき、例外の出力に値が出ない")
    void hidesFormat() {
        // 準備：形式の列に、列挙にない値を持つアセットの行を返すリポジトリ
        var value = RestoreFailures.value();
        var asset = RECORDS.newRecord(ASSET);
        asset.setFormat(value);
        var repository = new JooqAssetRepository(new RestoreFailures().with("asset", asset).dsl());

        // 操作と確認：復元に失敗し、例外の出力にその値が出ない
        assertValueIsHidden(() -> repository.findAllById(List.of(UUID.randomUUID())), value);
    }

    @Test
    @DisplayName("削除したアセットの画像の形式が列挙にないとき、例外の出力に値が出ない")
    void hidesFormatOnDelete() {
        // 準備：形式の列に、列挙にない値を持つアセットの行を返すリポジトリ
        var value = RestoreFailures.value();
        var asset = RECORDS.newRecord(ASSET);
        asset.setFormat(value);
        var repository = new JooqAssetRepository(new RestoreFailures().with("asset", asset).dsl());

        // 操作と確認：復元に失敗し、例外の出力にその値が出ない
        assertValueIsHidden(() -> repository.deleteAllById(List.of(UUID.randomUUID())), value);
    }
}
