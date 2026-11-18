package com.ecsite.module.media.infrastructure.persistence;

import com.ecsite.module.media.application.port.AssetRepository;
import com.ecsite.module.media.domain.Asset;
import com.ecsite.module.media.domain.ImageFormat;
import com.ecsite.support.config.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * jOOQ のアセットのリポジトリの統合テスト
 *
 * <p>テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class JooqAssetRepositoryTest {

    // ============================== 定数 ==============================

    /**
     * アセットの作成日時
     *
     * <p>日本時間の 2026-10-08 00:30。
     */
    private static final Instant CREATED_AT = Instant.parse("2026-10-07T15:30:00Z");

    // ============================== Bean ==============================

    @Autowired
    AssetRepository repository;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("記録")
    class Insert {

        @Test
        @DisplayName("アセットを記録すると、形式・保存先のキー・作成日時を含めて ID から取得できる")
        void insertsAndFindsAsset() {
            // 準備：PNG のアセット
            var asset = asset(ImageFormat.PNG);

            // 操作：記録する
            repository.insert(asset);

            // 確認：全ての項目が同じアセットを取得できる
            assertThat(repository.findAllById(List.of(asset.id()))).usingRecursiveFieldByFieldElementComparator().containsExactly(asset);
        }

        @Test
        @DisplayName("同じ保存先のキーのアセットは記録できない")
        void rejectsDuplicateStorageKey() {
            // 準備：記録したアセット
            var first = asset(ImageFormat.PNG);
            repository.insert(first);

            // 操作と確認：ID が違っても、保存先のキーが同じなら一意の制約で拒否される
            assertThatThrownBy(() -> repository.insert(new Asset(UUID.randomUUID(), ImageFormat.PNG, first.storageKey(), CREATED_AT)))
                    .isInstanceOf(DuplicateKeyException.class);
        }
    }

    @Nested
    @DisplayName("まとめての取得")
    class FindAll {

        @Test
        @DisplayName("指定した ID のアセットだけを取得し、ないアセットは含めない")
        void findsAssetsByIds() {
            // 準備：3 つのアセットを記録する
            var first = asset(ImageFormat.JPEG);
            var second = asset(ImageFormat.WEBP);
            var other = asset(ImageFormat.PNG);
            repository.insert(first);
            repository.insert(second);
            repository.insert(other);

            // 操作：2 つと、ない ID を指定して取得する
            var found = repository.findAllById(List.of(first.id(), second.id(), UUID.randomUUID()));

            // 確認：指定した 2 つだけ
            assertThat(found).usingRecursiveFieldByFieldElementComparator().containsExactlyInAnyOrder(first, second);
        }

        @Test
        @DisplayName("ID がなければ、空を返す")
        void returnsEmptyForNoIds() {
            // 操作と確認：空
            assertThat(repository.findAllById(List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("まとめての削除")
    class DeleteAll {

        @Test
        @DisplayName("指定した ID のアセットの記録だけが消える")
        void deletesOnlySpecifiedAssets() {
            // 準備：3 つのアセットを記録する
            var first = asset(ImageFormat.JPEG);
            var second = asset(ImageFormat.PNG);
            var kept = asset(ImageFormat.WEBP);
            repository.insert(first);
            repository.insert(second);
            repository.insert(kept);

            // 操作：2 つを消す
            repository.deleteAllById(Set.of(first.id(), second.id()));

            // 確認：指定しなかった 1 つだけが残る
            assertThat(repository.findAllById(List.of(first.id(), second.id(), kept.id()))).usingRecursiveFieldByFieldElementComparator().containsExactly(kept);
        }

        @Test
        @DisplayName("ないアセットの ID を含めても、ある分だけを消す")
        void ignoresMissingIds() {
            // 準備：1 つのアセットを記録する
            var asset = asset(ImageFormat.PNG);
            repository.insert(asset);

            // 操作：記録した ID と、ない ID を指定して消す
            repository.deleteAllById(Set.of(asset.id(), UUID.randomUUID()));

            // 確認：記録した分が消えている
            assertThat(repository.findAllById(List.of(asset.id()))).isEmpty();
        }

        @Test
        @DisplayName("ID がなければ、何も消さない")
        void deletesNothingForNoIds() {
            // 準備：1 つのアセットを記録する
            var kept = asset(ImageFormat.WEBP);
            repository.insert(kept);

            // 操作：空の ID で消す
            repository.deleteAllById(List.of());

            // 確認：記録が残る
            assertThat(repository.findAllById(List.of(kept.id()))).usingRecursiveFieldByFieldElementComparator().containsExactly(kept);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 指定した形式の画像のアセットを作る。
     *
     * <p>保存先のキーは、ID ごとに別にする。
     *
     * @param format 形式
     * @return アセット
     */
    private static Asset asset(ImageFormat format) {
        var id = UUID.randomUUID();
        return new Asset(id, format, "images/" + id + ".img", CREATED_AT);
    }
}
