package com.ecsite.module.media.application;

import com.ecsite.module.media.application.port.ObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

/**
 * 手放したアセットの配信のファイルを、コミットの後に消す（docs/design/modules/media.md「アセットを手放す」）。<br />
 * イベントは重ねて届くことがある（バックエンドのアーキテクチャ 第 5 節）。削除は何度行っても同じ結果になる。
 * DB を使わないので、トランザクションを始めない（ファイル保存を待つ間に DB の接続を持たない）。
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
    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void on(AssetReleased event) {
        storage.deletePublished(event.storageKey());
    }
}
