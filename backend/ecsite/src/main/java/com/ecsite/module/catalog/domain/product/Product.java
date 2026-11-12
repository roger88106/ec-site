package com.ecsite.module.catalog.domain.product;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.base.AggregateRoot;
import com.ecsite.common.model.value.BoundedText;
import com.ecsite.module.catalog.domain.DisplayOrders;
import com.ecsite.module.catalog.domain.ProductErrorCode;
import com.ecsite.module.catalog.domain.product.vo.PriceExclTax;
import com.ecsite.module.catalog.domain.product.vo.ProductActions;
import com.ecsite.module.catalog.domain.product.vo.ProductActions.DeleteBlockedReason;
import com.ecsite.module.catalog.domain.product.vo.ProductCode;
import com.ecsite.module.catalog.domain.product.vo.PublicationPeriod;
import com.ecsite.module.catalog.domain.product.vo.VariantCode;
import com.ecsite.module.catalog.event.ProductDeleted;
import com.ecsite.module.catalog.event.ProductDiscontinued;
import com.ecsite.module.catalog.event.ProductPriceChanged;
import com.ecsite.module.catalog.event.ProductPublished;
import com.ecsite.module.catalog.event.ProductUnpublished;
import com.ecsite.module.catalog.event.VariantDeleted;
import com.ecsite.module.catalog.event.VariantRegistered;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.jmolecules.ddd.annotation.Identity;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 商品
 *
 * <p>バリアントを含む集約のルート。バリアントを含めるのは、軸の値の組み合わせが商品の中で一意であることが、商品単位の不変条件であるため。
 */
@Getter
@Accessors(fluent = true)
@org.jmolecules.ddd.annotation.AggregateRoot
public class Product extends AggregateRoot {

    /**
     * 商品名の最大の文字数
     */
    static final int NAME_MAX_LENGTH = 200;

    /**
     * 説明の最大の文字数
     */
    static final int DESCRIPTION_MAX_LENGTH = 10_000;

    /**
     * バリアント軸の最大の数
     */
    static final int MAX_AXES = 3;

    /**
     * 軸の名前の最大の文字数
     */
    static final int AXIS_NAME_MAX_LENGTH = 30;

    /**
     * 軸の値の最大の文字数
     */
    static final int AXIS_VALUE_MAX_LENGTH = 50;

    /**
     * 1 商品あたりの画像の上限
     */
    static final int MAX_IMAGES = 20;

// ============================== フィールド定義 ==============================

    /**
     * 商品 ID
     */
    @Identity
    private final UUID id;

    /**
     * 商品品番。登録後は変更できない
     */
    private final ProductCode productCode;

    /**
     * 商品名
     */
    private String name;

    /**
     * 説明
     */
    private String description;

    /**
     * 税区分 ID（税モジュール）
     */
    private UUID taxCategoryId;

    /**
     * 税抜価格
     */
    private PriceExclTax priceExclTax;

    /**
     * 所属カテゴリの ID（変更できない集合）
     */
    private Set<UUID> categoryIds;

    /**
     * バリアント軸の名前（並び順。変更できない一覧）。登録後は変わらない
     */
    private final List<String> axes;

    /**
     * 公開状態
     */
    private PublicationStatus status;

    /**
     * 初回表示日時。null は一度も表示されていない（表示の予定もない）
     */
    @Nullable
    private Instant firstShownAt;

    /**
     * 公開期間
     */
    private PublicationPeriod publicationPeriod;

    /**
     * 画像（表示順。先頭が代表画像。変更できない一覧）
     */
    private List<ProductImage> images;

    /**
     * バリアント（表示順。変更できない一覧）
     */
    private List<Variant> variants;

    /**
     * バージョン（楽観ロック）。リポジトリが保存するときに上げる
     */
    private final long version;

// ============================== コンストラクタ ==============================

    /**
     * スナップショットから作る（登録・復元で共通）
     *
     * @param snapshot 商品のスナップショット
     * @throws BusinessException 商品名が範囲の外（{@code INVALID_PRODUCT_NAME}）
     * @throws BusinessException 説明が長すぎる（{@code INVALID_PRODUCT_DESCRIPTION}）
     * @throws BusinessException バリアント軸が正しくない（{@code INVALID_VARIANT_AXES}）
     */
    Product(@NonNull Snapshot snapshot) {
        this.id = snapshot.id();
        this.productCode = snapshot.productCode();
        verifyName(snapshot.name());
        verifyDescription(snapshot.description());
        this.name = snapshot.name();
        this.description = snapshot.description();
        this.taxCategoryId = snapshot.taxCategoryId();
        this.priceExclTax = snapshot.priceExclTax();
        this.categoryIds = Set.copyOf(snapshot.categoryIds());
        this.axes = verifyAxes(snapshot.axes());
        this.status = snapshot.status();
        this.firstShownAt = snapshot.firstShownAt();
        this.publicationPeriod = snapshot.publicationPeriod();
        this.images = snapshot.images().stream().sorted(Comparator.comparingInt(ProductImage::displayOrder)).toList();
        this.variants = snapshot.variants().stream().sorted(Comparator.comparingInt(Variant::displayOrder)).toList();
        this.version = snapshot.version();
    }

    /**
     * 商品を作る。
     *
     * <p>登録直後は非公開で、バリアントはない。
     *
     * @param id            商品 ID
     * @param productCode   商品品番
     * @param name          商品名
     * @param description   説明
     * @param taxCategoryId 税区分 ID
     * @param priceExclTax  税抜価格
     * @param categoryIds   所属カテゴリの ID
     * @param axes          バリアント軸の名前（並び順）
     * @return 商品
     * @throws BusinessException 商品名が範囲の外（{@code INVALID_PRODUCT_NAME}）
     * @throws BusinessException 説明が長すぎる（{@code INVALID_PRODUCT_DESCRIPTION}）
     * @throws BusinessException バリアント軸が正しくない（{@code INVALID_VARIANT_AXES}）
     */
    static Product create(UUID id, ProductCode productCode, String name, String description, UUID taxCategoryId,
                          PriceExclTax priceExclTax, Collection<UUID> categoryIds, List<String> axes) {
        return new Product(new Snapshot(id, productCode, name, description, taxCategoryId, priceExclTax,
                Set.copyOf(categoryIds), List.copyOf(axes), PublicationStatus.UNPUBLISHED, null,
                PublicationPeriod.UNLIMITED, List.of(), List.of(), 0));
    }

// ============================== ドメインの振る舞い ==============================

    /**
     * 商品を編集する。
     *
     * <p>税抜価格が変わったら、商品価格変更のイベントを登録する。税区分に有効な税率があるかは確認しない。
     *
     * @param name          商品名
     * @param description   説明
     * @param taxCategoryId 税区分 ID
     * @param priceExclTax  税抜価格
     * @param categoryIds   所属カテゴリの ID
     * @param now           現在時刻
     * @throws BusinessException 商品名が範囲の外（{@code INVALID_PRODUCT_NAME}）
     * @throws BusinessException 説明が長すぎる（{@code INVALID_PRODUCT_DESCRIPTION}）
     */
    public void update(String name, String description, @NonNull UUID taxCategoryId, @NonNull PriceExclTax priceExclTax,
                       @NonNull Collection<UUID> categoryIds, @NonNull Instant now) {
        verifyName(name);
        verifyDescription(description);
        boolean priceChanged = !this.priceExclTax.equals(priceExclTax);

        this.name = name;
        this.description = description;
        this.taxCategoryId = taxCategoryId;
        this.priceExclTax = priceExclTax;
        this.categoryIds = Set.copyOf(categoryIds);

        if (priceChanged) {
            registerEvent(ProductPriceChanged.of(now, id, priceExclTax.toMoney()));
        }
    }

    /**
     * バリアントを登録する。
     *
     * <p>並びの最後に加える。
     *
     * @param variantCode バリアント品番
     * @param axisValues  軸の値（商品の軸と同じ並び）
     * @param now         現在時刻
     * @return 登録したバリアントの ID
     * @throws BusinessException 軸の値が正しくない（{@code INVALID_AXIS_VALUES}）
     * @throws BusinessException 軸を持たない商品に 2 つ目のバリアントを登録しようとした（{@code SINGLE_VARIANT_ONLY}）
     * @throws BusinessException バリアント品番が重複している（{@code VARIANT_CODE_ALREADY_EXISTS}）
     * @throws BusinessException 軸の値の組み合わせが重複している（{@code VARIANT_AXIS_VALUES_ALREADY_EXISTS}）
     */
    public UUID addVariant(@NonNull VariantCode variantCode, @NonNull List<String> axisValues, @NonNull Instant now) {
        verifyAxisValues(axisValues);
        if (!canAddVariant(axes.size(), variants.size())) {
            throw BusinessException.of(ProductErrorCode.SINGLE_VARIANT_ONLY);
        }
        if (variants.stream().anyMatch(variant -> variant.variantCode().equals(variantCode))) {
            throw BusinessException.of(ProductErrorCode.VARIANT_CODE_ALREADY_EXISTS);
        }
        var variant = new Variant(UUID.randomUUID(), variantCode, axisValues, nextVariantDisplayOrder(), false, null);
        if (variants.stream().anyMatch(other -> other.axisKey().equals(variant.axisKey()))) {
            throw BusinessException.of(ProductErrorCode.VARIANT_AXIS_VALUES_ALREADY_EXISTS);
        }

        var added = new ArrayList<>(variants);
        added.add(variant);
        this.variants = List.copyOf(added);

        registerEvent(VariantRegistered.of(now, id, variant.id()));
        return variant.id();
    }

    /**
     * バリアントを削除する。
     *
     * @param variantId バリアント ID
     * @param now       現在時刻
     * @throws BusinessException 商品にないバリアント（{@code NOT_FOUND}）
     * @throws BusinessException C 側に表示されたことのある商品（{@code PRODUCT_ALREADY_SHOWN}）
     * @throws BusinessException 公開中の商品の最後のバリアント（{@code PRODUCT_HAS_NO_VARIANTS}）
     */
    public void deleteVariant(@NonNull UUID variantId, @NonNull Instant now) {
        requireVariant(variantId);
        if (hasBeenShown(now)) {
            throw BusinessException.of(ProductErrorCode.PRODUCT_ALREADY_SHOWN);
        }
        // 公開中の商品を、バリアントのない状態にしない
        if (status == PublicationStatus.PUBLISHED && variants.size() == 1) {
            throw BusinessException.of(ProductErrorCode.PRODUCT_HAS_NO_VARIANTS);
        }

        this.variants = variants.stream().filter(variant -> !variant.id().equals(variantId)).toList();

        registerEvent(VariantDeleted.of(now, id, variantId));
    }

    /**
     * バリアントの並び順を変更する。
     *
     * <p>送られた順に 1 から表示順を振り直す。
     *
     * @param orderedIds 並べたい順のバリアント ID
     * @throws BusinessException 全てのバリアントと過不足なく一致しない（{@code INVALID_VARIANT_ORDER}）。このときは何も変えない
     */
    public void reorderVariants(@NonNull List<UUID> orderedIds) {
        this.variants = reorder(variants, Variant::id, Variant::withDisplayOrder, orderedIds,
                ProductErrorCode.INVALID_VARIANT_ORDER);
    }

    /**
     * バリアントを販売停止にする。
     *
     * <p>既に販売停止なら何もしない（冪等）。販売終了の商品のバリアントも停止できる。
     *
     * @param variantId バリアント ID
     * @return 変わったら true。既に販売停止なら false
     * @throws BusinessException 商品にないバリアント（{@code NOT_FOUND}）
     */
    public boolean suspendVariant(@NonNull UUID variantId) {
        return changeSalesSuspended(variantId, true);
    }

    /**
     * バリアントの販売停止を解除する。
     *
     * <p>販売停止でなければ何もしない（冪等）。
     *
     * @param variantId バリアント ID
     * @return 変わったら true。販売停止でなければ false
     * @throws BusinessException 商品にないバリアント（{@code NOT_FOUND}）
     */
    public boolean resumeVariant(@NonNull UUID variantId) {
        return changeSalesSuspended(variantId, false);
    }

    /**
     * 画像を加えられるか（上限の {@link #MAX_IMAGES} 枚に達していないか）を確認する。
     *
     * @throws BusinessException 画像が上限に達している（{@code TOO_MANY_PRODUCT_IMAGES}）
     */
    public void verifyCanAddImage() {
        if (images.size() >= MAX_IMAGES) {
            throw BusinessException.of(ProductErrorCode.TOO_MANY_PRODUCT_IMAGES, List.of(MAX_IMAGES));
        }
    }

    /**
     * 画像を並びの最後に加える。
     *
     * @param imageId 画像 ID
     * @param assetId メディアモジュールのアセット ID
     * @throws BusinessException 画像が上限に達している（{@code TOO_MANY_PRODUCT_IMAGES}）
     */
    public void addImage(@NonNull UUID imageId, @NonNull UUID assetId) {
        verifyCanAddImage();
        var nextOrder = DisplayOrders.next(images.stream().mapToInt(ProductImage::displayOrder));
        var added = new ArrayList<>(images);
        added.add(new ProductImage(imageId, assetId, nextOrder));
        this.images = List.copyOf(added);
    }

    /**
     * 画像の並び順を変更する。
     *
     * <p>送られた順に 1 から表示順を振り直す。先頭が代表画像になる。
     *
     * @param orderedIds 並べたい順の画像 ID
     * @throws BusinessException 全ての画像と過不足なく一致しない（{@code INVALID_IMAGE_ORDER}）。このときは何も変えない
     */
    public void reorderImages(@NonNull List<UUID> orderedIds) {
        this.images = reorder(images, ProductImage::id, ProductImage::withDisplayOrder, orderedIds,
                ProductErrorCode.INVALID_IMAGE_ORDER);
    }

    /**
     * 画像を削除する。
     *
     * <p>その画像を指定していたバリアントの指定は外す（代表画像に戻る）。C 側に表示されたことのある商品の画像も削除できる。
     *
     * @param imageId 画像 ID
     * @return 削除した画像のアセット ID
     * @throws BusinessException 商品にない画像（{@code NOT_FOUND}）
     */
    public UUID deleteImage(@NonNull UUID imageId) {
        var image = requireImage(imageId);

        this.images = images.stream().filter(candidate -> !candidate.id().equals(imageId)).toList();
        this.variants = variants.stream()
                .map(variant -> imageId.equals(variant.imageId()) ? variant.withImageId(null) : variant)
                .toList();
        return image.assetId();
    }

    /**
     * バリアントの画像を指定する。
     *
     * <p>指定できるのは、その商品の画像だけ。
     *
     * @param variantId バリアント ID
     * @param imageId   画像 ID。null で指定を外す（代表画像を使う）
     * @throws BusinessException 商品にないバリアント（{@code NOT_FOUND}）
     * @throws BusinessException 商品にない画像（{@code INVALID_VARIANT_IMAGE}）
     */
    public void setVariantImage(@NonNull UUID variantId, @Nullable UUID imageId) {
        requireVariant(variantId);
        if (imageId != null && images.stream().noneMatch(image -> image.id().equals(imageId))) {
            throw BusinessException.of(ProductErrorCode.INVALID_VARIANT_IMAGE);
        }
        this.variants = variants.stream()
                .map(variant -> variant.id().equals(variantId) ? variant.withImageId(imageId) : variant)
                .toList();
    }

    /**
     * 公開する。
     *
     * <p>既に公開なら何もしない（冪等）。
     *
     * @param now 現在時刻
     * @return 状態が変わったら true。既にその状態なら false
     * @throws BusinessException 販売終了の商品（{@code PRODUCT_DISCONTINUED}）
     * @throws BusinessException バリアントがない（{@code PRODUCT_HAS_NO_VARIANTS}）
     */
    public boolean publish(@NonNull Instant now) {
        verifyNotDiscontinued();
        if (status == PublicationStatus.PUBLISHED) {
            return false;
        }
        if (variants.isEmpty()) {
            throw BusinessException.of(ProductErrorCode.PRODUCT_HAS_NO_VARIANTS);
        }

        this.status = PublicationStatus.PUBLISHED;
        this.firstShownAt = calculateFirstShownAt(now);

        registerEvent(ProductPublished.of(now, id));
        return true;
    }

    /**
     * 非公開にする。
     *
     * <p>既に非公開なら何もしない（冪等）。
     *
     * @param now 現在時刻
     * @return 状態が変わったら true。既にその状態なら false
     * @throws BusinessException 販売終了の商品（{@code PRODUCT_DISCONTINUED}）
     */
    public boolean unpublish(@NonNull Instant now) {
        verifyNotDiscontinued();
        if (status == PublicationStatus.UNPUBLISHED) {
            return false;
        }

        this.status = PublicationStatus.UNPUBLISHED;
        clearScheduledFirstShownAt(now);

        registerEvent(ProductUnpublished.of(now, id));
        return true;
    }

    /**
     * 販売終了にする。
     *
     * <p>既に販売終了なら何もしない（冪等）。
     *
     * @param now 現在時刻
     * @return 状態が変わったら true。既にその状態なら false
     */
    public boolean discontinue(@NonNull Instant now) {
        if (status == PublicationStatus.DISCONTINUED) {
            return false;
        }

        this.status = PublicationStatus.DISCONTINUED;
        clearScheduledFirstShownAt(now);

        registerEvent(ProductDiscontinued.of(now, id));
        return true;
    }

    /**
     * 公開期間を変える。
     *
     * <p>公開中なら、表示前の初回表示日時を計算し直す。表示されて確定した初回表示日時は変わらない。
     *
     * @param period 公開期間
     * @param now    現在時刻
     */
    public void changePublicationPeriod(@NonNull PublicationPeriod period, @NonNull Instant now) {
        this.publicationPeriod = period;
        if (status == PublicationStatus.PUBLISHED) {
            this.firstShownAt = calculateFirstShownAt(now);
        }
    }

    /**
     * 削除できるかを検証し、商品削除のイベントを登録する。
     *
     * <p>商品のデータは消さない。
     *
     * @param now 現在時刻
     * @throws BusinessException C 側に表示されたことのある商品（{@code PRODUCT_ALREADY_SHOWN}）
     */
    public void delete(@NonNull Instant now) {
        if (hasBeenShown(now)) {
            throw BusinessException.of(ProductErrorCode.PRODUCT_ALREADY_SHOWN);
        }
        registerEvent(ProductDeleted.of(now, id));
    }

    /**
     * C 側に表示される条件（公開状態が「公開」で、公開期間内）を満たすかを返す。
     *
     * @param now 時点
     * @return 表示されるなら true
     */
    public boolean isVisibleAt(@NonNull Instant now) {
        return status == PublicationStatus.PUBLISHED && publicationPeriod.contains(now);
    }

    /**
     * C 側に表示されたことがあるかを返す。
     *
     * <p>初回表示日時が空か未来なら、まだ表示されていない。
     *
     * @param now 現在時刻
     * @return 表示されたことがあれば true
     */
    public boolean hasBeenShown(@NonNull Instant now) {
        return hasBeenShown(firstShownAt, now);
    }

    /**
     * 今の商品にできる操作を返す。
     *
     * @param now 現在時刻
     * @return できる操作
     */
    public ProductActions actions(@NonNull Instant now) {
        return actionsOf(status, firstShownAt, axes.size(), variants.size(), now);
    }

    /**
     * 商品の状態から、できる操作を決める。
     *
     * @param status       公開状態
     * @param firstShownAt 初回表示日時。なければ null
     * @param axisCount    バリアント軸の数
     * @param variantCount バリアントの数
     * @param now          現在時刻
     * @return できる操作
     */
    public static ProductActions actionsOf(@NonNull PublicationStatus status, @Nullable Instant firstShownAt,
                                           int axisCount, int variantCount, @NonNull Instant now) {
        var neverShown = !hasBeenShown(firstShownAt, now);
        return new ProductActions(
                status == PublicationStatus.UNPUBLISHED,
                status == PublicationStatus.PUBLISHED,
                status != PublicationStatus.DISCONTINUED,
                neverShown,
                neverShown,
                canAddVariant(axisCount, variantCount),
                neverShown ? null : DeleteBlockedReason.ALREADY_SHOWN);
    }

    /**
     * C 側に表示されたことがあるかを返す。
     *
     * <p>初回表示日時が空か未来なら、まだ表示されていない。
     *
     * @param firstShownAt 初回表示日時。なければ null
     * @param now          現在時刻
     * @return 表示されたことがあれば true
     */
    static boolean hasBeenShown(@Nullable Instant firstShownAt, @NonNull Instant now) {
        return firstShownAt != null && !firstShownAt.isAfter(now);
    }

    /**
     * バリアントを追加できるかを返す。
     *
     * <p>軸を持たない商品のバリアントは 1 つだけ。
     *
     * @param axisCount    バリアント軸の数
     * @param variantCount 今のバリアントの数
     * @return 追加できれば true
     */
    static boolean canAddVariant(int axisCount, int variantCount) {
        return axisCount > 0 || variantCount == 0;
    }

// ============================== プライベートメソッド ==============================

    /**
     * バリアントの販売停止を変える。
     *
     * @param variantId      バリアント ID
     * @param salesSuspended 販売停止
     * @return 変わったら true。既にその状態なら false
     * @throws BusinessException 商品にないバリアント（{@code NOT_FOUND}）
     */
    private boolean changeSalesSuspended(UUID variantId, boolean salesSuspended) {
        var variant = requireVariant(variantId);
        if (variant.salesSuspended() == salesSuspended) {
            return false;
        }
        this.variants = variants.stream()
                .map(v -> v.id().equals(variantId) ? v.withSalesSuspended(salesSuspended) : v)
                .toList();
        return true;
    }

    /**
     * 商品のバリアントを ID で探す。
     *
     * @param variantId バリアント ID
     * @return バリアント
     * @throws BusinessException 商品にないバリアント（{@code NOT_FOUND}）
     */
    private Variant requireVariant(UUID variantId) {
        return variants.stream().filter(variant -> variant.id().equals(variantId)).findFirst()
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
    }

    /**
     * 商品の画像を ID で探す。
     *
     * @param imageId 画像 ID
     * @return 画像
     * @throws BusinessException 商品にない画像（{@code NOT_FOUND}）
     */
    private ProductImage requireImage(UUID imageId) {
        return images.stream().filter(image -> image.id().equals(imageId)).findFirst()
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
    }

    /**
     * 公開したとき（または公開中に公開期間を変えたとき）の初回表示日時を求める。
     *
     * @param now 現在時刻
     * @return 初回表示日時。表示されないなら null
     */
    private @Nullable Instant calculateFirstShownAt(Instant now) {
        // 表示されて確定した値（過去）は変えない
        if (hasBeenShown(now)) {
            return firstShownAt;
        }
        // 現在時刻と公開開始日時の遅いほう。それが公開終了日時以降なら、表示されないので null にする
        var start = publicationPeriod.publishStart();
        var candidate = start != null && start.isAfter(now) ? start : now;
        var end = publicationPeriod.publishEnd();
        return end != null && !candidate.isBefore(end) ? null : candidate;
    }

    /**
     * 未来（表示予定）の初回表示日時を空に戻す。
     *
     * <p>確定済みなら何もしない。
     *
     * @param now 現在時刻
     */
    private void clearScheduledFirstShownAt(Instant now) {
        if (!hasBeenShown(now)) {
            this.firstShownAt = null;
        }
    }

    /**
     * 並び替える。
     *
     * <p>送られた順に 1 から表示順を振り直す。
     *
     * @param items            今の要素
     * @param idOf             要素の ID
     * @param withDisplayOrder 表示順を変えた要素を作る
     * @param orderedIds       並べたい順の ID
     * @param error            一致しないときのエラー
     * @param <T>              要素の型
     * @return 並び替えた要素（変更できない一覧）
     * @throws BusinessException 今の要素と過不足なく一致しない（重複・漏れ・ないものを含む。{@code error} のエラー）
     */
    private static <T> List<T> reorder(List<T> items, Function<T, UUID> idOf, BiFunction<T, Integer, T> withDisplayOrder,
                                       List<UUID> orderedIds, ProductErrorCode error) {
        // 送られた順に新しい表示順を振り、その順に並べ直す
        var orders = DisplayOrders.assign(items.stream().map(idOf).toList(), orderedIds, error);
        return items.stream()
                .map(item -> withDisplayOrder.apply(item, orders.get(idOf.apply(item))))
                .sorted(Comparator.comparing(idOf, Comparator.comparing(orders::get)))
                .toList();
    }

    /**
     * 販売終了でないことを検証する。
     *
     * @throws BusinessException 販売終了の商品（{@code PRODUCT_DISCONTINUED}）
     */
    private void verifyNotDiscontinued() {
        if (status == PublicationStatus.DISCONTINUED) {
            throw BusinessException.of(ProductErrorCode.PRODUCT_DISCONTINUED);
        }
    }

    /**
     * 新しいバリアントの表示順（最大＋1。なければ 1）を返す。
     */
    private int nextVariantDisplayOrder() {
        return DisplayOrders.next(variants.stream().mapToInt(Variant::displayOrder));
    }

    /**
     * 軸の値が、商品の軸と同じ数で、それぞれ 1〜{@link #AXIS_VALUE_MAX_LENGTH} 文字であることを検証する。
     *
     * <p>軸キーの区切り文字は、値に含められない。
     *
     * @param axisValues 軸の値
     * @throws BusinessException 軸の値が正しくない（{@code INVALID_AXIS_VALUES}）
     */
    private void verifyAxisValues(List<String> axisValues) {
        if (axisValues.size() != axes.size()
                || !axisValues.stream().allMatch(value -> BoundedText.isFilledWithin(value, AXIS_VALUE_MAX_LENGTH)
                && !value.contains(Variant.AXIS_KEY_SEPARATOR))) {
            throw BusinessException.of(ProductErrorCode.INVALID_AXIS_VALUES, List.of(AXIS_VALUE_MAX_LENGTH));
        }
    }

    /**
     * 商品名が 1〜{@link #NAME_MAX_LENGTH} 文字（空白のみは不可）であることを検証する。
     *
     * @param name 商品名
     * @throws BusinessException 商品名が範囲の外（{@code INVALID_PRODUCT_NAME}）
     */
    private static void verifyName(String name) {
        if (!BoundedText.isFilledWithin(name, NAME_MAX_LENGTH)) {
            throw BusinessException.of(ProductErrorCode.INVALID_PRODUCT_NAME, List.of(NAME_MAX_LENGTH));
        }
    }

    /**
     * 説明が {@link #DESCRIPTION_MAX_LENGTH} 文字以内であることを検証する。
     *
     * <p>空でもよい。
     *
     * @param description 説明
     * @throws BusinessException 説明が長すぎる（{@code INVALID_PRODUCT_DESCRIPTION}）
     */
    private static void verifyDescription(String description) {
        if (!BoundedText.fitsWithin(description, DESCRIPTION_MAX_LENGTH)) {
            throw BusinessException.of(ProductErrorCode.INVALID_PRODUCT_DESCRIPTION, List.of(DESCRIPTION_MAX_LENGTH));
        }
    }

    /**
     * バリアント軸が 0〜{@link #MAX_AXES} つで、名前が 1〜{@link #AXIS_NAME_MAX_LENGTH} 文字、重複がないことを検証する。
     *
     * @param axes バリアント軸の名前
     * @return 変更できない一覧
     * @throws BusinessException バリアント軸が正しくない（{@code INVALID_VARIANT_AXES}）
     */
    private static List<String> verifyAxes(List<String> axes) {
        if (axes.size() > MAX_AXES
                || !axes.stream().allMatch(axis -> BoundedText.isFilledWithin(axis, AXIS_NAME_MAX_LENGTH))
                || new LinkedHashSet<>(axes).size() != axes.size()) {
            throw BusinessException.of(ProductErrorCode.INVALID_VARIANT_AXES, List.of(MAX_AXES, AXIS_NAME_MAX_LENGTH));
        }
        return List.copyOf(axes);
    }

// ============================== スナップショット関連処理 ==============================

    /**
     * 商品のスナップショット
     *
     * @param id                商品 ID
     * @param productCode       商品品番
     * @param name              商品名
     * @param description       説明
     * @param taxCategoryId     税区分 ID
     * @param priceExclTax      税抜価格
     * @param categoryIds       所属カテゴリの ID
     * @param axes              バリアント軸の名前（並び順）
     * @param status            公開状態
     * @param firstShownAt      初回表示日時
     * @param publicationPeriod 公開期間
     * @param images            画像
     * @param variants          バリアント
     * @param version           バージョン
     */
    public record Snapshot(UUID id, ProductCode productCode, String name, String description, UUID taxCategoryId,
                           PriceExclTax priceExclTax, Set<UUID> categoryIds, List<String> axes,
                           PublicationStatus status, @Nullable Instant firstShownAt, PublicationPeriod publicationPeriod,
                           List<ProductImage> images, List<Variant> variants, long version) {
    }

    /**
     * スナップショットを取得する
     *
     * @return 商品のスナップショット
     */
    public Snapshot toSnapshot() {
        return new Snapshot(id, productCode, name, description, taxCategoryId, priceExclTax, categoryIds, axes,
                status, firstShownAt, publicationPeriod, images, variants, version);
    }
}
