package com.ecsite.module.media.application;

import com.ecsite.module.media.application.port.AssetRepository;
import com.ecsite.module.media.application.port.ImageConverter;
import com.ecsite.module.media.application.port.ObjectStorage;
import com.ecsite.module.media.domain.Asset;
import com.ecsite.module.media.domain.ImageFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * アセットの解放の単体テスト
 *
 * <p>イベントを発行するのは、リポジトリが実際に削除したアセットだけであることを確認する。
 */
class MediaServiceReleaseTest {

    // ============================== Bean ==============================

    /**
     * アセットのリポジトリ（mock）
     */
    private final AssetRepository assetRepository = mock(AssetRepository.class);

    /**
     * イベントの発行（mock）
     */
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    /**
     * メディアのサービス
     */
    private final MediaService service = new MediaService(assetRepository, mock(ObjectStorage.class),
            mock(ImageConverter.class), eventPublisher, Clock.systemUTC());

    // ============================== テスト ==============================

    @Test
    @DisplayName("DC-489-7 リポジトリが渡された ID の一部だけを返すとき、返した分だけアセット解放を発行する")
    void publishesOnlyForDeletedAssets() {
        // 準備：2 つの ID のうち、1 つだけ削除したと返すリポジトリ
        var deleted = new Asset(UUID.randomUUID(), ImageFormat.WEBP, "images/deleted.webp", Instant.parse("2026-10-07T15:30:00Z"));
        var alreadyReleased = UUID.randomUUID();
        var ids = List.of(deleted.id(), alreadyReleased);
        when(assetRepository.deleteAllById(ids)).thenReturn(List.of(deleted));

        // 操作：2 つを解放する
        service.release(ids);

        // 確認：削除した 1 つの分だけ発行する
        var event = ArgumentCaptor.forClass(AssetReleased.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getAllValues()).singleElement().satisfies(published -> {
            assertThat(published.assetId()).isEqualTo(deleted.id());
            assertThat(published.storageKey()).isEqualTo(deleted.storageKey());
        });
    }

    @Test
    @DisplayName("DC-489-7 リポジトリが何も削除しなかったと返すとき、アセット解放を発行しない")
    void publishesNothingWhenNothingDeleted() {
        // 準備：何も削除しなかったと返すリポジトリ
        var ids = List.of(UUID.randomUUID());
        when(assetRepository.deleteAllById(ids)).thenReturn(List.of());

        // 操作：解放する
        service.release(ids);

        // 確認：発行しない
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("DC-489-7 ID が空のとき、リポジトリを呼ばず、アセット解放を発行しない")
    void doesNothingForNoIds() {
        // 操作：空の ID で解放する
        service.release(List.of());

        // 確認：リポジトリを呼ばず、発行もしない
        verifyNoInteractions(assetRepository, eventPublisher);
    }
}
