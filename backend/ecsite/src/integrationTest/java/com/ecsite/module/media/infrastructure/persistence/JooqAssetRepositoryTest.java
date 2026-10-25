package com.ecsite.module.media.infrastructure.persistence;

import com.ecsite.IntegrationTest;
import com.ecsite.module.media.application.port.AssetRepository;
import com.ecsite.module.media.domain.Asset;
import com.ecsite.module.media.domain.ImageFormat;
import org.junit.jupiter.api.DisplayName;
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
 * アセットのリポジトリの統合テスト。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class JooqAssetRepositoryTest {

    /**
     * アセットの作成日時
     */
    private static final Instant CREATED_AT = Instant.parse("2026-10-07T15:30:00Z");

    @Autowired
    AssetRepository repository;

    @Test
    @DisplayName("アセットを記録すると、形式・保存先のキー・作成日時を含めて ID から取得できる")
    void insertsAndFindsAsset() {
        var asset = asset(ImageFormat.PNG);

        repository.insert(asset);

        assertThat(repository.findAllById(List.of(asset.id()))).containsExactly(asset);
    }

    @Test
    @DisplayName("アセットをまとめて取得でき、ないアセットは含めない。ID がなければ空")
    void findsAssetsByIds() {
        var first = asset(ImageFormat.JPEG);
        var second = asset(ImageFormat.WEBP);
        var other = asset(ImageFormat.PNG);
        repository.insert(first);
        repository.insert(second);
        repository.insert(other);

        var found = repository.findAllById(List.of(first.id(), second.id(), UUID.randomUUID()));

        assertThat(found).containsExactlyInAnyOrder(first, second);
        assertThat(repository.findAllById(List.of())).isEmpty();
    }

    @Test
    @DisplayName("アセットの記録をまとめて消すと、指定したものだけが消える。ないアセットは無視する。ID がなくても何も起きない")
    void deletesAssetsByIds() {
        var first = asset(ImageFormat.JPEG);
        var second = asset(ImageFormat.PNG);
        var kept = asset(ImageFormat.WEBP);
        repository.insert(first);
        repository.insert(second);
        repository.insert(kept);

        repository.deleteAllById(Set.of(first.id(), second.id(), UUID.randomUUID()));
        repository.deleteAllById(List.of());

        assertThat(repository.findAllById(List.of(first.id(), second.id(), kept.id()))).containsExactly(kept);
    }

    @Test
    @DisplayName("同じ保存先のキーのアセットは記録できない")
    void rejectsDuplicateStorageKey() {
        var first = asset(ImageFormat.PNG);
        repository.insert(first);

        assertThatThrownBy(() -> repository.insert(new Asset(UUID.randomUUID(), ImageFormat.PNG, first.storageKey(), CREATED_AT)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    // ============================== 補助 ==============================

    private static Asset asset(ImageFormat format) {
        var id = UUID.randomUUID();
        return new Asset(id, format, "images/" + id + ".img", CREATED_AT);
    }
}
