package com.ecsite.module.catalog.domain.product;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.base.AggregateRoot;
import com.ecsite.common.model.id.AssetId;
import com.ecsite.common.model.id.CategoryId;
import com.ecsite.common.model.id.ProductId;
import com.ecsite.common.model.id.TaxCategoryId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.common.model.value.BoundedText;
import com.ecsite.module.catalog.domain.ProductErrorCode;
import com.ecsite.module.catalog.domain.product.vo.PriceExclTax;
import com.ecsite.module.catalog.domain.product.vo.ProductActions;
import com.ecsite.module.catalog.domain.product.vo.ProductActions.DeleteBlockedReason;
import com.ecsite.module.catalog.domain.product.vo.ProductCode;
import com.ecsite.module.catalog.domain.product.vo.ProductImageId;
import com.ecsite.module.catalog.domain.product.vo.Publication;
import com.ecsite.module.catalog.domain.product.vo.PublicationPeriod;
import com.ecsite.module.catalog.domain.product.vo.PublicationStatus;
import com.ecsite.module.catalog.domain.product.vo.VariantCode;
import com.ecsite.module.catalog.event.ProductDeleted;
import com.ecsite.module.catalog.event.ProductDiscontinued;
import com.ecsite.module.catalog.event.ProductPriceChanged;
import com.ecsite.module.catalog.event.ProductPublished;
import com.ecsite.module.catalog.event.ProductUnpublished;
import com.ecsite.module.catalog.event.VariantDeleted;
import com.ecsite.module.catalog.event.VariantRegistered;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.jmolecules.ddd.annotation.Identity;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 商品
 *
 * <p>バリアントを含む集約のルート。バリアントを含めるのは、軸の値の組み合わせが商品の中で一意であることが、商品単位の不変条件であるため。
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
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

// ============================== フィールド定義 ==============================

    /**
     * 商品 ID
     */
    @Identity
    @EqualsAndHashCode.Include
    private final ProductId id;

    /**
     * 商品品番
     *
     * <p>登録後は変更できない。
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
    private TaxCategoryId taxCategoryId;

    /**
     * 税抜価格
     */
    private PriceExclTax priceExclTax;

    /**
     * 所属カテゴリの ID（変更できない集合）
     */
    private Set<CategoryId> categoryIds;

    /**
     * バリアント軸の名前
     *
     * <p>並び順に並んだ、変更できない一覧。登録後は変わらない。
     */
    private final List<String> axes;

    /**
     * 公開（公開状態・公開期間・初回表示日時）
     */
    @Getter(AccessLevel.NONE)
    private Publication publication;

    /**
     * 画像
     *
     * <p>表示順に並んだ、変更できない一覧。先頭が代表画像。
     */
    @Getter(AccessLevel.NONE)
    private ProductImages images;

    /**
     * バリアント
     *
     * <p>表示順に並んだ、変更できない一覧。
     */
    @Getter(AccessLevel.NONE)
    private Variants variants;

    /**
     * バージョン（楽観ロック）
     *
     * <p>リポジトリが保存するときに上げる。
     */
    private final long version;

// ============================== コンストラクタ ==============================

    /**
     * スナップショットから作る（登録・復元で共通）
     *
     * <p>画像の数・バリアントの数・軸の値の数、または公開に関する値の組み合わせ（公開状態・公開期間・初回表示日時）が条件に合わないとき、
     * IllegalArgumentException を投げる。
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
        this.images = new ProductImages(snapshot.images());
        this.variants = new Variants(snapshot.variants());
        this.version = snapshot.version();
        verifyInvariants();
        // 公開の条件の検査は、他の条件の検査の後にする（検査の順序を変えないため）
        this.publication = new Publication(snapshot.status(), snapshot.publicationPeriod(), snapshot.firstShownAt());
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
    static Product create(ProductId id, ProductCode productCode, String name, String description, TaxCategoryId taxCategoryId,
                          PriceExclTax priceExclTax, Collection<CategoryId> categoryIds, List<String> axes) {
        return new Product(new Snapshot(id, productCode, name, description, taxCategoryId, priceExclTax,
                Set.copyOf(categoryIds), List.copyOf(axes), PublicationStatus.UNPUBLISHED, null,
                PublicationPeriod.UNLIMITED, List.of(), List.of(), 0));
    }

// ============================== 画像とバリアントの項目 ==============================

    /**
     * 画像を返す。
     *
     * @return 表示順に並んだ、変更できない一覧。先頭が代表画像
     */
    public List<ProductImage> images() {
        return images.list();
    }

    /**
     * バリアントを返す。
     *
     * @return 表示順に並んだ、変更できない一覧
     */
    public List<Variant> variants() {
        return variants.list();
    }

// ============================== 公開の項目 ==============================

    /**
     * 公開状態を返す。
     *
     * @return 公開状態
     */
    public PublicationStatus status() {
        return publication.status();
    }

    /**
     * 初回表示日時を返す。
     *
     * @return 初回表示日時。一度も表示されていない（表示の予定もない）なら null
     */
    public @Nullable Instant firstShownAt() {
        return publication.firstShownAt();
    }

    /**
     * 公開期間を返す。
     *
     * @return 公開期間
     */
    public PublicationPeriod publicationPeriod() {
        return publication.publicationPeriod();
    }

// ============================== ドメインの振る舞い ==============================

    /**
     * C 側に表示される条件を満たすかを返す。
     *
     * <p>条件は、公開状態が「公開」で、公開期間内であること。
     *
     * @param now 現在時刻
     * @return 表示されるなら true
     */
    public boolean isVisibleAt(@NonNull Instant now) {
        return publication.isVisibleAt(now);
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
        return publication.hasBeenShown(now);
    }

    /**
     * 今の商品にできる操作を返す。
     *
     * @param now 現在時刻
     * @return できる操作
     */
    public ProductActions actions(@NonNull Instant now) {
        return actionsOf(publication.status(), publication.firstShownAt(), axes.size(), variants.size(), now);
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
        var neverShown = !Publication.hasBeenShown(firstShownAt, now);
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

    /**
     * 画像を加えられるかを確認する。
     *
     * <p>上限の {@value ProductImages#MAX_IMAGES} 枚に達していなければ加えられる。
     *
     * @throws BusinessException 画像が上限に達している（{@code TOO_MANY_PRODUCT_IMAGES}）
     */
    public void verifyCanAddImage() {
        if (images.size() >= ProductImages.MAX_IMAGES) {
            throw BusinessException.of(ProductErrorCode.TOO_MANY_PRODUCT_IMAGES, List.of(ProductImages.MAX_IMAGES));
        }
    }

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
    public void update(String name, String description, @NonNull TaxCategoryId taxCategoryId, @NonNull PriceExclTax priceExclTax,
                       @NonNull Collection<CategoryId> categoryIds, @NonNull Instant now) {
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
     * @param variantId   バリアント ID
     * @param variantCode バリアント品番
     * @param axisValues  軸の値（商品の軸と同じ並び）
     * @param now         現在時刻
     * @return 登録したバリアントの ID
     * @throws BusinessException 軸の値が正しくない（{@code INVALID_AXIS_VALUES}）
     * @throws BusinessException 軸を持たない商品に 2 つ目のバリアントを登録しようとした（{@code SINGLE_VARIANT_ONLY}）
     * @throws BusinessException バリアント品番が重複している（{@code VARIANT_CODE_ALREADY_EXISTS}）
     * @throws BusinessException 軸の値の組み合わせが重複している（{@code VARIANT_AXIS_VALUES_ALREADY_EXISTS}）
     */
    public VariantId addVariant(@NonNull VariantId variantId, @NonNull VariantCode variantCode, @NonNull List<String> axisValues,
                                @NonNull Instant now) {
        verifyAxisValues(axisValues);
        if (!canAddVariant(axes.size(), variants.size())) {
            throw BusinessException.of(ProductErrorCode.SINGLE_VARIANT_ONLY);
        }
        if (variants.containsCode(variantCode)) {
            throw BusinessException.of(ProductErrorCode.VARIANT_CODE_ALREADY_EXISTS);
        }
        var variant = new Variant(variantId, variantCode, axisValues, variants.nextDisplayOrder(), false, null);
        if (variants.containsAxisKey(variant.axisKey())) {
            throw BusinessException.of(ProductErrorCode.VARIANT_AXIS_VALUES_ALREADY_EXISTS);
        }

        this.variants = variants.add(variant);

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
    public void deleteVariant(@NonNull VariantId variantId, @NonNull Instant now) {
        variants.require(variantId);
        if (hasBeenShown(now)) {
            throw BusinessException.of(ProductErrorCode.PRODUCT_ALREADY_SHOWN);
        }
        // 公開中の商品を、バリアントのない状態にしない
        if (publication.status() == PublicationStatus.PUBLISHED && variants.size() == 1) {
            throw BusinessException.of(ProductErrorCode.PRODUCT_HAS_NO_VARIANTS);
        }

        this.variants = variants.remove(variantId);

        registerEvent(VariantDeleted.of(now, id, variantId));
    }

    /**
     * バリアントの並び順を変更する。
     *
     * <p>指定された順に 1 から表示順を振り直す。
     *
     * @param orderedIds 並べたい順のバリアント ID
     * @throws BusinessException 全てのバリアントと過不足なく一致しない（{@code INVALID_VARIANT_ORDER}）。このときは何も変えない
     */
    public void reorderVariants(@NonNull List<VariantId> orderedIds) {
        this.variants = variants.reorder(orderedIds);
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
    public boolean suspendVariant(@NonNull VariantId variantId) {
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
    public boolean resumeVariant(@NonNull VariantId variantId) {
        return changeSalesSuspended(variantId, false);
    }

    /**
     * 画像を並びの最後に加える。
     *
     * @param imageId 画像 ID
     * @param assetId メディアモジュールのアセット ID
     * @throws BusinessException 画像が上限に達している（{@code TOO_MANY_PRODUCT_IMAGES}）
     */
    public void addImage(@NonNull ProductImageId imageId, @NonNull AssetId assetId) {
        verifyCanAddImage();
        this.images = images.add(new ProductImage(imageId, assetId, images.nextDisplayOrder()));
    }

    /**
     * 画像の並び順を変更する。
     *
     * <p>指定された順に 1 から表示順を振り直す。先頭が代表画像になる。
     *
     * @param orderedIds 並べたい順の画像 ID
     * @throws BusinessException 全ての画像と過不足なく一致しない（{@code INVALID_IMAGE_ORDER}）。このときは何も変えない
     */
    public void reorderImages(@NonNull List<ProductImageId> orderedIds) {
        this.images = images.reorder(orderedIds);
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
    public AssetId deleteImage(@NonNull ProductImageId imageId) {
        var image = images.require(imageId);

        this.images = images.remove(imageId);
        this.variants = variants.withoutImage(imageId);
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
    public void setVariantImage(@NonNull VariantId variantId, @Nullable ProductImageId imageId) {
        variants.require(variantId);
        if (imageId != null && !images.contains(imageId)) {
            throw BusinessException.of(ProductErrorCode.INVALID_VARIANT_IMAGE);
        }
        this.variants = variants.replace(variantId, variant -> variant.withImageId(imageId));
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
        var published = publication.publish(now);
        if (published.equals(publication)) {
            return false;
        }
        if (variants.isEmpty()) {
            throw BusinessException.of(ProductErrorCode.PRODUCT_HAS_NO_VARIANTS);
        }

        this.publication = published;

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
        var unpublished = publication.unpublish(now);
        if (unpublished.equals(publication)) {
            return false;
        }

        this.publication = unpublished;

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
        var discontinued = publication.discontinue(now);
        if (discontinued.equals(publication)) {
            return false;
        }

        this.publication = discontinued;

        registerEvent(ProductDiscontinued.of(now, id));
        return true;
    }

    /**
     * 公開期間を変える。
     *
     * <p>公開中なら、表示前の初回表示日時を計算する。表示されて確定した初回表示日時は変わらない。
     *
     * @param period 公開期間
     * @param now    現在時刻
     */
    public void changePublicationPeriod(@NonNull PublicationPeriod period, @NonNull Instant now) {
        this.publication = publication.changePublicationPeriod(period, now);
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

// ============================== プライベートメソッド ==============================

    /**
     * 集約として成り立つ条件を検査する。
     *
     * <p>状態を変えるメソッドの検査（業務のエラー）は、操作の前の判定として別にある。ここで、条件に合わない状態の集約を作れないようにする。
     * 軸のない商品のバリアントが 2 つ以上、軸の値の数が軸の数と違う、のどれかのとき、IllegalArgumentException を投げる。
     * 例外のメッセージには値を入れない。画像の数の上限は {@link ProductImages}、公開の条件は {@link Publication} が検査する。
     */
    private void verifyInvariants() {
        if (axes.isEmpty() && variants.size() > 1) {
            throw new IllegalArgumentException("軸のない商品にバリアントが 2 つ以上ある");
        }
        if (variants.hasAxisValueCountOtherThan(axes.size())) {
            throw new IllegalArgumentException("バリアントの軸の値の数が商品の軸の数と違う");
        }
    }

    /**
     * バリアントの販売停止を変える。
     *
     * @param variantId      バリアント ID
     * @param salesSuspended 販売停止
     * @return 変わったら true。既にその状態なら false
     * @throws BusinessException 商品にないバリアント（{@code NOT_FOUND}）
     */
    private boolean changeSalesSuspended(VariantId variantId, boolean salesSuspended) {
        var variant = variants.require(variantId);
        if (variant.salesSuspended() == salesSuspended) {
            return false;
        }
        this.variants = variants.replace(variantId, target -> target.withSalesSuspended(salesSuspended));
        return true;
    }

    /**
     * 軸の値が、商品の軸と同じ数で、それぞれ 1〜{@value #AXIS_VALUE_MAX_LENGTH} 文字であることを検証する。
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
     * 商品名が 1〜{@value #NAME_MAX_LENGTH} 文字（空白のみは不可）であることを検証する。
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
     * 説明が {@value #DESCRIPTION_MAX_LENGTH} 文字以内であることを検証する。
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
     * バリアント軸が 0〜{@value #MAX_AXES} つで、名前が 1〜{@value #AXIS_NAME_MAX_LENGTH} 文字、重複がないことを検証する。
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
    public record Snapshot(ProductId id, ProductCode productCode, String name, String description, TaxCategoryId taxCategoryId,
                           PriceExclTax priceExclTax, Set<CategoryId> categoryIds, List<String> axes,
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
                publication.status(), publication.firstShownAt(), publication.publicationPeriod(), images.list(),
                variants.list(), version);
    }
}
