package com.ecsite.module.media.service;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.media.api.ImageUpload;
import com.ecsite.module.media.api.MediaApi;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * メディアのサービス（docs/modules/media.md）。{@link MediaApi} の実装。<br />
 * ファイルは署名付き URL でブラウザから受付のバケットへ直接送られ、確定のときに中身を確かめ、WebP に変換して配信のバケットへ置く。
 */
@Service
@RequiredArgsConstructor
class MediaService implements MediaApi {

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

    @Override
    public ImageUpload requestImageUpload(String scope, String contentType, long contentLength) {
        var uploadId = UUID.randomUUID();
        var uploadKey = AssetKeys.upload(scope, uploadId);
        ImageFormat.ofContentType(contentType)
                .orElseThrow(() -> BusinessException.of(MediaErrorCode.UNSUPPORTED_IMAGE_FORMAT));
        verifySize(contentLength);

        // 申告された形式のまま署名する（image/heic と image/heif のどちらでも、ブラウザが付ける Content-Type と合う）
        var presigned = storage.presignUpload(uploadKey, contentType.toLowerCase(Locale.ROOT), contentLength);
        return new ImageUpload(uploadId, presigned.url(), presigned.headers(), presigned.expiresAt());
    }

    /**
     * {@inheritDoc}<br />
     * 中身を WebP に変換して（AC-68、69）配信のバケットへ置く。変換できなければ登録しない（AC-70）。<br />
     * 書き込みの後に呼んだ側の保存が失敗すると配信のファイルが残るが、だれからも参照されないだけで害はない。
     */
    @Override
    @Transactional
    public UUID confirmImage(String scope, UUID uploadId) {
        var uploadKey = AssetKeys.upload(scope, uploadId);
        var size = storage.findUploadSize(uploadKey)
                .orElseThrow(() -> BusinessException.of(MediaErrorCode.IMAGE_UPLOAD_NOT_FOUND));
        verifySize(size);
        // 変換の前に、受け付ける形式であることを確かめる（判定できないものは変換に回さない）
        ImageFormat.detect(storage.readUploadHead(uploadKey, ImageFormat.SIGNATURE_LENGTH))
                .orElseThrow(() -> BusinessException.of(MediaErrorCode.INVALID_IMAGE_FILE));
        var webp = imageConverter.convertToWebp(uploadKey);

        var assetId = UUID.randomUUID();
        var storageKey = AssetKeys.published(assetId, ImageFormat.WEBP);
        storage.putPublished(storageKey, webp, ImageFormat.WEBP.contentType());

        assetRepository.insert(new Asset(assetId, ImageFormat.WEBP, storageKey, clock.instant()));
        return assetId;
    }

    /**
     * {@inheritDoc}<br />
     * イベントはコミットの後に届き、ファイルを消す（{@link AssetFileHandler}）。呼んだ側の保存が失敗すれば、イベントも取り消される。
     */
    @Override
    @Transactional
    public void release(Collection<UUID> assetIds) {
        if (assetIds.isEmpty()) {
            return;
        }
        var assets = assetRepository.findAllById(assetIds);
        assetRepository.deleteAllById(assets.stream().map(Asset::id).toList());

        var now = clock.instant();
        assets.forEach(asset -> eventPublisher.publishEvent(AssetReleased.of(now, asset)));
    }

    /**
     * {@inheritDoc}<br />
     * URL は配信の基準 URL とキーから組み立てる（ストレージへの問い合わせはしない）。
     */
    @Override
    @Transactional(readOnly = true)
    public Map<UUID, URI> urls(Collection<UUID> assetIds) {
        if (assetIds.isEmpty()) {
            return Map.of();
        }
        return assetRepository.findAllById(assetIds).stream()
                .collect(Collectors.toUnmodifiableMap(Asset::id, asset -> storage.publicUrl(asset.storageKey())));
    }

// ============================== プライベートメソッド ==============================

    /**
     * 大きさが 1 バイト以上、上限（10MB）以下であることを確かめる（AC-39）。
     */
    private static void verifySize(long size) {
        if (size <= 0 || size > ImageFormat.MAX_BYTES) {
            throw BusinessException.of(MediaErrorCode.IMAGE_TOO_LARGE);
        }
    }
}
