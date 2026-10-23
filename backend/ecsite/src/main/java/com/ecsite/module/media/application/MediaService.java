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
 * メディアのサービス（docs/design/modules/media.md）。公開の API（`api/` の MediaApi）が委ねる先。<br />
 * ファイルは署名付き URL でブラウザからアップロードのバケットへ直接送られ、用意のときに中身を確認し、WebP に変換して配信のバケットへ置き、登録でアセットを記録する。
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
     * @param scope         受付の範囲（呼ぶ側が決める。例：商品ごと）
     * @param contentType   ファイルの形式
     * @param contentLength ファイルの大きさ（バイト）
     * @return 受付の情報
     */
    public ImageUploadView requestImageUpload(String scope, String contentType, long contentLength) {
        var uploadId = UUID.randomUUID();
        var uploadKey = AssetKeys.upload(scope, uploadId);
        if (ImageFormat.ofContentType(contentType).isEmpty()) {
            throw BusinessException.of(MediaErrorCode.UNSUPPORTED_IMAGE_FORMAT, List.of(ImageFormat.displayNames()));
        }
        verifySize(contentLength);

        // 申告された形式のまま署名する（image/heic と image/heif のどちらでも、ブラウザが付ける Content-Type と合う）
        var presigned = storage.presignUpload(uploadKey, contentType.toLowerCase(Locale.ROOT), contentLength);
        return new ImageUploadView(uploadId, presigned.url(), presigned.headers(), presigned.expiresAt());
    }

    /**
     * 受け付けた画像を用意する（MediaApi の同じメソッドの説明を参照）。<br />
     * DB を使わないので、トランザクションを始めない。
     */
    public UUID prepareImage(String scope, UUID uploadId) {
        var uploadKey = AssetKeys.upload(scope, uploadId);
        var size = storage.findUploadSize(uploadKey)
                .orElseThrow(() -> BusinessException.of(MediaErrorCode.IMAGE_UPLOAD_NOT_FOUND));
        verifySize(size);
        // 変換の前に、受け付ける形式であることを確認する（判定できないものは変換に回さない）
        if (ImageFormat.detect(storage.readUploadHead(uploadKey, ImageFormat.SIGNATURE_LENGTH)).isEmpty()) {
            throw BusinessException.of(MediaErrorCode.INVALID_IMAGE_FILE, List.of(ImageFormat.displayNames()));
        }
        var webp = imageConverter.convertToWebp(uploadKey);

        var assetId = UUID.randomUUID();
        storage.putPublished(storageKey(assetId), webp, ImageFormat.WEBP.contentType());
        return assetId;
    }

    /**
     * 用意した画像をアセットとして記録する（MediaApi の同じメソッドの説明を参照）。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID registerImage(UUID assetId) {
        assetRepository.insert(new Asset(assetId, ImageFormat.WEBP, storageKey(assetId), clock.instant()));
        return assetId;
    }

    /**
     * 用意した画像の配信のファイルを消す（MediaApi の同じメソッドの説明を参照）。<br />
     * 消せなかったファイルは、だれからも参照されずに残る（登録のないファイルを探して消すことはしない）。
     */
    public void discardImage(UUID assetId) {
        var storageKey = storageKey(assetId);
        try {
            storage.deletePublished(storageKey);
        } catch (RuntimeException ex) {
            log.warn("用意した画像の配信のファイルを消せない：storageKey={}", storageKey, ex);
        }
    }

    /**
     * アセットを手放す（MediaApi の同じメソッドの説明を参照）。<br />
     * イベントはコミットの後に届き、ファイルを消す（{@link AssetFileHandler}）。呼んだ側の保存が失敗すれば、イベントも取り消される。
     */
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
     * アセットの配信の URL をまとめて返す（MediaApi の同じメソッドの説明を参照）。<br />
     * URL は配信の基準 URL とキーから組み立てる（ストレージへの問い合わせはしない）。
     */
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
