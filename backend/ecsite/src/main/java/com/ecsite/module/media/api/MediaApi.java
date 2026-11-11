package com.ecsite.module.media.api;

import com.ecsite.module.media.api.model.ImageUpload;
import com.ecsite.module.media.api.model.PreparedImage;
import com.ecsite.module.media.application.MediaService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * メディアの API
 *
 * <p>アセットはアセット ID だけで示す。配信のキーや URL の形は、このモジュールの外に出さない。
 */
@Component
@RequiredArgsConstructor
public class MediaApi {

    /**
     * メディアのサービス
     */
    private final MediaService mediaService;

    /**
     * 画像のアップロードを受け付ける。形式と大きさを確認し、アップロードのバケットへの署名付き URL を返す。
     *
     * <p>受付は DB に記録しない。枚数の上限は確認しない。
     *
     * @param scope         範囲（持ち主。例：商品 ID）。英数字とハイフンだけ
     * @param contentType   ファイルの形式（申告）
     * @param contentLength ファイルの大きさ（申告。バイト）
     * @return 受付
     * @throws com.ecsite.common.error.exception.BusinessException 形式が対象外、または大きさが範囲の外
     */
    public ImageUpload requestImageUpload(@NonNull String scope, @NonNull String contentType, long contentLength) {
        var view = mediaService.requestImageUpload(scope, contentType, contentLength);
        return new ImageUpload(view.uploadId(), view.uploadUrl(), view.headers(), view.expiresAt());
    }

    /**
     * 受け付けた画像を用意する。
     *
     * <p>アップロードしたファイルの大きさと、先頭のバイトで判定した形式を確認（申告は信用しない）、WebP に変換して配信のバケットへ置く。
     * アセットは記録しない。トランザクションを始めず、変換と保存の間に DB の接続を使わない。
     * 配信のファイルは、{@link #registerImage} で登録するか、{@link #discardImage} で消すまで、登録のないまま残る。
     *
     * @param scope    受付のときの範囲。範囲の違う受付は見つからない
     * @param uploadId 受付 ID
     * @return 用意した画像
     * @throws com.ecsite.common.error.exception.BusinessException 受付がない（範囲の違うものを含む）、大きさが範囲の外、中身が対象の画像でない、変換できない
     */
    public PreparedImage prepareImage(@NonNull String scope, @NonNull UUID uploadId) {
        return new PreparedImage(mediaService.prepareImage(scope, uploadId));
    }

    /**
     * 用意した画像をアセットとして記録する。
     *
     * <p>呼んだ側のトランザクションに加わる。呼んだ側の保存が失敗すると、アセットの記録も取り消される（配信のファイルは残る）。
     *
     * @param prepared 用意した画像
     * @return アセット ID
     * @throws org.springframework.transaction.IllegalTransactionStateException トランザクションの外で呼んだ
     */
    public UUID registerImage(@NonNull PreparedImage prepared) {
        mediaService.registerImage(prepared.assetId());
        return prepared.assetId();
    }

    /**
     * 用意した画像の配信のファイルを消す。
     *
     * <p>消すのに失敗しても例外を投げない（警告のログに保存先のキーを出す）。呼んだ側の元の失敗を隠さないため。
     *
     * @param prepared 用意した画像
     */
    public void discardImage(@NonNull PreparedImage prepared) {
        mediaService.discardImage(prepared.assetId());
    }

    /**
     * アセットを手放す。記録を消し、配信のファイルはコミットの後に消す。
     *
     * <p>呼んだ側のトランザクションがあれば、それに加わる。すでにないアセットは無視する（何度行っても同じ結果）。
     *
     * @param assetIds アセット ID
     */
    public void release(@NonNull Collection<UUID> assetIds) {
        mediaService.release(assetIds);
    }

    /**
     * アセットの配信の URL をまとめて返す。
     *
     * @param assetIds アセット ID
     * @return アセット ID → 配信の URL。ないアセットは含まない
     */
    public Map<UUID, URI> urls(@NonNull Collection<UUID> assetIds) {
        return mediaService.urls(assetIds);
    }
}
