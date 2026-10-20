package com.ecsite.module.media.api;

import com.ecsite.module.media.application.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * メディアモジュールが公開する API（docs/backend/modules/media.md「他のモジュールへの公開」）。<br />
 * 使う側はアセット ID だけを持つ。配信のキーや URL の形は、このモジュールの外に出さない。<br />
 * 処理はメディアのサービスに委ねる（バックエンドの書き方 第 1.1 節）。
 */
@Component
@RequiredArgsConstructor
public class MediaApi {

    /**
     * メディアのサービス
     */
    private final MediaService mediaService;

    /**
     * 画像のアップロードを受け付ける。形式と大きさを確かめ、受付のバケットへの署名付き URL を返す。<br />
     * 受付は DB に記録しない。枚数の上限など、使う側の規則は、呼ぶ前に使う側が確かめる。
     *
     * @param scope         範囲（持ち主。例：商品 ID）。英数字とハイフンだけ。確定のときに同じ範囲を渡す
     * @param contentType   ファイルの形式（申告）
     * @param contentLength ファイルの大きさ（申告。バイト）
     * @return 受付
     * @throws com.ecsite.common.error.exception.BusinessException 形式が対象外、または大きさが範囲の外
     */
    public ImageUpload requestImageUpload(String scope, String contentType, long contentLength) {
        return mediaService.requestImageUpload(scope, contentType, contentLength);
    }

    /**
     * 受け付けた画像を確定する。<br />
     * 受付のファイルの大きさと、先頭のバイトで判定した形式を確かめ（申告は信用しない）、配信のバケットへ置いてアセットを記録する。
     * 呼んだ側のトランザクションの中で呼ぶ。呼んだ側の保存が失敗すると、アセットの記録も取り消される。
     *
     * @param scope    受付のときの範囲
     * @param uploadId 受付 ID
     * @return アセット ID
     * @throws com.ecsite.common.error.exception.BusinessException 受付がない（範囲の違うものを含む）、大きさが範囲の外、中身が対象の画像でない
     */
    public UUID confirmImage(String scope, UUID uploadId) {
        return mediaService.confirmImage(scope, uploadId);
    }

    /**
     * 受け付けた画像を用意する。<br />
     * 受付のファイルの大きさと、先頭のバイトで判定した形式を確かめ（申告は信用しない）、WebP に変換して配信のバケットへ置く。
     * アセットは記録しない。トランザクションの外で呼ぶ（変換と保存の間、DB の接続を持たない）。<br />
     * 用意の後は、{@link #registerImage} で登録するか、使わなければ {@link #discardImage} で破棄する。
     *
     * @param scope    受付のときの範囲
     * @param uploadId 受付 ID
     * @return 用意した画像
     * @throws com.ecsite.common.error.exception.BusinessException 受付がない（範囲の違うものを含む）、大きさが範囲の外、中身が対象の画像でない
     */
    public PreparedImage prepareImage(String scope, UUID uploadId) {
        return mediaService.prepareImage(scope, uploadId);
    }

    /**
     * 用意した画像をアセットとして記録する。<br />
     * 呼んだ側のトランザクションの中で呼ぶ。呼んだ側の保存が失敗すると、アセットの記録も取り消される（そのときは破棄する）。
     *
     * @param prepared 用意した画像
     * @return アセット ID
     * @throws org.springframework.transaction.IllegalTransactionStateException トランザクションの外で呼んだ
     */
    public UUID registerImage(PreparedImage prepared) {
        return mediaService.registerImage(prepared);
    }

    /**
     * 用意した画像の配信のファイルを消す。登録しなかったときに呼ぶ。<br />
     * 消すのに失敗しても例外を投げない（警告のログに保存先のキーを出す）。呼んだ側の元の失敗を隠さないため。
     *
     * @param prepared 用意した画像
     */
    public void discardImage(PreparedImage prepared) {
        mediaService.discardImage(prepared);
    }

    /**
     * アセットを手放す。記録を消し、配信のファイルはコミットの後に消す。<br />
     * 呼んだ側のトランザクションの中で呼ぶ。すでにないアセットは無視する（何度行っても同じ結果）。
     *
     * @param assetIds アセット ID
     */
    public void release(Collection<UUID> assetIds) {
        mediaService.release(assetIds);
    }

    /**
     * アセットの配信の URL をまとめて返す。
     *
     * @param assetIds アセット ID
     * @return アセット ID → 配信の URL。ないアセットは含まない
     */
    public Map<UUID, URI> urls(Collection<UUID> assetIds) {
        return mediaService.urls(assetIds);
    }
}
