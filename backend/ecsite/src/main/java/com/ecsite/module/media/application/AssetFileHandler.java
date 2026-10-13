package com.ecsite.module.media.application;

import com.ecsite.module.media.application.port.ObjectStorage;
import com.ecsite.module.media.domain.AssetReleased;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 手放したアセットの配信のファイルを、コミットの後に消す（docs/backend/modules/media.md「アセットを手放す」）。<br />
 * イベントは重ねて届くことがある（バックエンドのアーキテクチャ 第 5 節）。削除は何度行っても同じ結果になる。
 */
@Component
@RequiredArgsConstructor
class AssetFileHandler {

    /**
     * ファイルの置き場所
     */
    private final ObjectStorage storage;

    /**
     * アセット解放：配信のファイルを消す。
     *
     * @param event アセット解放
     */
    @ApplicationModuleListener
    void on(AssetReleased event) {
        storage.deletePublished(event.storageKey());
    }
}
