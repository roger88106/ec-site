package com.ecsite.module.media.application;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

/**
 * アセット解放のハンドラー
 */
@Component
@RequiredArgsConstructor
class AssetFileHandler {

    /**
     * メディアのサービス
     */
    private final MediaService mediaService;

    /**
     * アセット解放：配信のファイルを消す。
     *
     * <p>コミットの後に受ける。DB を使わないので、トランザクションを始めない（ファイル保存を待つ間に DB の接続を持たない）。
     *
     * @param event アセット解放
     */
    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void on(AssetReleased event) {
        mediaService.deleteReleasedFile(event);
    }
}
