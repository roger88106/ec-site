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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * メディアのサービス（docs/modules/media.md）。{@link MediaApi} の実装。<br />
 * ファイルは署名付き URL でブラウザから受付のバケットへ直接送られ、確定のときに中身を確かめて配信のバケットへコピーする。
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
        var format = ImageFormat.ofContentType(contentType)
                .orElseThrow(() -> BusinessException.of(MediaErrorCode.UNSUPPORTED_IMAGE_FORMAT));
        verifySize(contentLength);

        var presigned = storage.presignUpload(uploadKey, format.contentType(), contentLength);
        return new ImageUpload(uploadId, presigned.url(), presigned.headers(), presigned.expiresAt());
    }

    /**
     * {@inheritDoc}<br />
     * コピーの後に呼んだ側の保存が失敗すると配信のファイルが残るが、だれからも参照されないだけで害はない。
     */
    @Override
    @Transactional
    public UUID confirmImage(String scope, UUID uploadId) {
        var uploadKey = AssetKeys.upload(scope, uploadId);
        var size = storage.findUploadSize(uploadKey)
                .orElseThrow(() -> BusinessException.of(MediaErrorCode.IMAGE_UPLOAD_NOT_FOUND));
        verifySize(size);
        var format = ImageFormat.detect(storage.readUploadHead(uploadKey, ImageFormat.SIGNATURE_LENGTH))
                .orElseThrow(() -> BusinessException.of(MediaErrorCode.INVALID_IMAGE_FILE));

        var assetId = UUID.randomUUID();
        var storageKey = AssetKeys.published(assetId, format);
        storage.publish(uploadKey, storageKey, format.contentType());

        assetRepository.insert(new Asset(assetId, format, storageKey, clock.instant()));
        return assetId;
    }

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
