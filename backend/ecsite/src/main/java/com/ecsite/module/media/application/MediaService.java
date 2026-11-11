package com.ecsite.module.media.application;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.media.application.model.ImageUploadView;
import com.ecsite.module.media.application.port.AssetRepository;
import com.ecsite.module.media.application.port.ImageConverter;
import com.ecsite.module.media.application.port.ObjectStorage;
import com.ecsite.module.media.domain.Asset;
import com.ecsite.module.media.domain.AssetKeys;
import com.ecsite.module.media.domain.ImageFormat;
import com.ecsite.module.media.domain.MediaErrorCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * メディアのサービス
 *
 * <p>アセットとして配信するのは、中身を確認して WebP に変換したファイルだけ。アップロードされたファイルはそのまま配信しない。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    /**
     * アセットのリポジトリ
     */
    private final AssetRepository assetRepository;

    /**
     * ファイルの置き場所
     */
    private final ObjectStorage storage;

    /**
     * 画像の変換
     */
    private final ImageConverter imageConverter;

    /**
     * イベントの発行
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 画像のアップロードの受付を始める。形式と大きさを確認し、アップロードのバケットへの署名付き URL を発行する。
     *
     * @param scope         受付の範囲（英数字とハイフン）
     * @param contentType   ファイルの形式
     * @param contentLength ファイルの大きさ（バイト）
     * @return 受付の情報
     */
    public ImageUploadView requestImageUpload(@NonNull String scope, @NonNull String contentType, long contentLength) {
        // 受付のキー作成
        var uploadId = UUID.randomUUID();
        var uploadKey = AssetKeys.upload(scope, uploadId);

        // 形式と大きさの確認（申告の値）
        if (ImageFormat.ofContentType(contentType).isEmpty()) {
            throw BusinessException.of(MediaErrorCode.UNSUPPORTED_IMAGE_FORMAT, List.of(ImageFormat.displayNames()));
        }
        verifySize(contentLength);

        // 署名付き URL の発行
        // ※申告された形式のまま署名する（image/heic と image/heif のどちらでも、ブラウザーが付ける Content-Type と合う）
        var presigned = storage.presignUpload(uploadKey, contentType.toLowerCase(Locale.ROOT), contentLength);
        return new ImageUploadView(uploadId, presigned.url(), presigned.headers(), presigned.expiresAt());
    }

    /**
     * 受け付けた画像を用意する。大きさと、先頭のバイトで判定した形式を確認し、WebP に変換して配信のバケットへ置く。
     *
     * <p>DB を使わないので、トランザクションを始めない。
     *
     * @param scope    受付の範囲
     * @param uploadId 受付 ID
     * @return 配信のバケットに置いた画像のアセット ID（まだ記録していない）
     * @throws BusinessException 受付がない、大きさが範囲の外、中身が対象の画像でない、変換できない
     */
    public UUID prepareImage(@NonNull String scope, @NonNull UUID uploadId) {
        // 受付の大きさの確認
        var uploadKey = AssetKeys.upload(scope, uploadId);
        var size = storage.findUploadSize(uploadKey)
                .orElseThrow(() -> BusinessException.of(MediaErrorCode.IMAGE_UPLOAD_NOT_FOUND));
        verifySize(size);

        // 形式の確認 ※判定できないものは変換に回さない
        if (ImageFormat.detect(storage.readUploadHead(uploadKey, ImageFormat.SIGNATURE_LENGTH)).isEmpty()) {
            throw BusinessException.of(MediaErrorCode.INVALID_IMAGE_FILE, List.of(ImageFormat.displayNames()));
        }

        // WebP への変換
        var webp = imageConverter.convertToWebp(uploadKey);

        // 配信のバケットへの保存
        var assetId = UUID.randomUUID();
        storage.putPublished(storageKey(assetId), webp, ImageFormat.WEBP.contentType());
        return assetId;
    }

    /**
     * 用意した画像をアセットとして記録する。呼んだ側のトランザクションの中で記録する。
     *
     * @param assetId 用意した画像のアセット ID
     * @throws org.springframework.transaction.IllegalTransactionStateException トランザクションの外で呼んだ
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registerImage(@NonNull UUID assetId) {
        assetRepository.insert(new Asset(assetId, ImageFormat.WEBP, storageKey(assetId), clock.instant()));
    }

    /**
     * 用意した画像の配信のファイルを消す。消すのに失敗しても例外を投げず、警告のログに保存先のキーを出す。
     *
     * <p>消せなかったファイルは、だれからも参照されずに残る（登録のないファイルを探して消すことはしない）。
     *
     * @param assetId 用意した画像のアセット ID
     */
    public void discardImage(@NonNull UUID assetId) {
        var storageKey = storageKey(assetId);
        try {
            storage.deletePublished(storageKey);
        } catch (RuntimeException e) {
            log.warn("用意した画像の配信のファイルを消せない：storageKey={}", storageKey, e);
        }
    }

    /**
     * アセットを手放す。記録を消し、配信のファイルはコミットの後に消す。すでにないアセットは無視する。
     *
     * <p>記録の削除と同じトランザクションで、アセット解放のイベントを記録する。配信のファイルはコミットの後に消え、
     * トランザクションが取り消されたときは残る。
     *
     * @param assetIds アセット ID
     */
    @Transactional
    public void release(@NonNull Collection<UUID> assetIds) {
        if (assetIds.isEmpty()) {
            return;
        }
        // アセットの記録の削除
        var assets = assetRepository.findAllById(assetIds);
        assetRepository.deleteAllById(assets.stream().map(Asset::id).toList());

        // アセット解放の発行 ※配信のファイルはコミットの後に消す
        var now = clock.instant();
        assets.forEach(asset -> eventPublisher.publishEvent(AssetReleased.of(now, asset)));
    }

    /**
     * アセットの配信の URL をまとめて返す。
     *
     * <p>URL は配信の基準 URL とキーから組み立てる（ストレージへの問い合わせはしない）。
     *
     * @param assetIds アセット ID
     * @return アセット ID → 配信の URL。ないアセットは含まない
     */
    @Transactional(readOnly = true)
    public Map<UUID, URI> urls(@NonNull Collection<UUID> assetIds) {
        if (assetIds.isEmpty()) {
            return Map.of();
        }
        return assetRepository.findAllById(assetIds).stream()
                .collect(Collectors.toUnmodifiableMap(Asset::id, asset -> storage.publicUrl(asset.storageKey())));
    }

    /**
     * 手放したアセットの配信のファイルを消す。ファイルがなくても成功とする（何度行っても同じ結果）。
     *
     * <p>イベントは重ねて届くことがある（失敗したイベントを再発行するため）。
     *
     * @param event アセット解放
     */
    void deleteReleasedFile(AssetReleased event) {
        storage.deletePublished(event.storageKey());
    }

// ============================== プライベートメソッド ==============================

    /**
     * 用意した画像の、配信のバケットでのキー（アセット ID と WebP で決まる）。
     */
    private static String storageKey(UUID assetId) {
        return AssetKeys.published(assetId, ImageFormat.WEBP);
    }

    /**
     * 大きさが 1 バイト以上、上限（10MB）以下であることを確認する。
     */
    private static void verifySize(long size) {
        if (size <= 0 || size > ImageFormat.MAX_BYTES) {
            throw BusinessException.of(MediaErrorCode.IMAGE_TOO_LARGE, List.of(ImageFormat.MAX_BYTES / (1024 * 1024)));
        }
    }
}
