package com.ecsite.module.catalog.domain.product;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.AggregateRoot;
import com.ecsite.module.catalog.domain.product.vo.PriceExclTax;
import com.ecsite.module.catalog.domain.product.vo.ProductCode;
import com.ecsite.module.catalog.domain.product.vo.PublicationPeriod;
import com.ecsite.module.catalog.domain.product.vo.VariantCode;
import com.ecsite.module.catalog.event.ProductDeleted;
import com.ecsite.module.catalog.event.ProductDiscontinued;
import com.ecsite.module.catalog.event.ProductImageDeleted;
import com.ecsite.module.catalog.event.ProductPriceChanged;
import com.ecsite.module.catalog.event.ProductPublished;
import com.ecsite.module.catalog.event.ProductUnpublished;
import com.ecsite.module.catalog.event.VariantDeleted;
import com.ecsite.module.catalog.event.VariantRegistered;
import lombok.NonNull;
import org.jmolecules.ddd.annotation.Identity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 商品の Aggregate Root。<br />
 * バリアントを含む。軸の値の組み合わせが商品内で一意であることは、商品単位の不変条件のため（docs/modules/catalog.md）。
 */
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
     * バリアント軸の最大の数（AC-13）
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
     * 1 商品あたりの画像の上限（AC-40）
     */
    static final int MAX_IMAGES = 20;

// ============================== フィールド定義 ==============================

    /**
     * 商品 ID
     */
    @Identity
    private final UUID id;

    /**
     * 商品品番。登録後は変更できない（AC-15）
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
     * 所属カテゴリの ID
     */
    private Set<UUID> categoryIds;

    /**
     * バリアント軸の名前（並び順）。登録後は変更できない
     */
    private final List<String> axes;

    /**
     * 公開状態
     */
    private PublicationStatus status;

    /**
     * 初回表示日時。null は一度も表示されていない（表示の予定もない）
     */
    private Instant firstShownAt;

    /**
     * 公開期間
     */
    private PublicationPeriod publicationPeriod;

    /**
     * 画像（表示順。先頭が代表画像）
     */
    private List<ProductImage> images;

    /**
     * バリアント（表示順）
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
     */
    Product(@NonNull Snapshot snapshot) {
        this.id = snapshot.id();
        this.productCode = snapshot.productCode();
        this.name = verifyName(snapshot.name());
        this.description = verifyDescription(snapshot.description());
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
     * 商品を作る（AC-13、17）。登録直後は非公開で、バリアントはない。
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
     */
    static Product create(UUID id, ProductCode productCode, String name, String description, UUID taxCategoryId,
                          PriceExclTax priceExclTax, Collection<UUID> categoryIds, List<String> axes) {
        return new Product(new Snapshot(id, productCode, name, description, taxCategoryId, priceExclTax,
                Set.copyOf(categoryIds), List.copyOf(axes), PublicationStatus.UNPUBLISHED, null,
                PublicationPeriod.UNLIMITED, List.of(), List.of(), 0));
    }

// ============================== Getter ==============================

    /**
     * @return 商品 ID
     */
    public UUID id() {
        return id;
    }

    /**
     * @return 商品品番
     */
    public ProductCode productCode() {
        return productCode;
    }

    /**
     * @return 商品名
     */
    public String name() {
        return name;
    }

    /**
     * @return 説明
     */
    public String description() {
        return description;
    }

    /**
     * @return 税区分 ID
     */
    public UUID taxCategoryId() {
        return taxCategoryId;
    }

    /**
     * @return 税抜価格
     */
    public PriceExclTax priceExclTax() {
        return priceExclTax;
    }

    /**
     * @return 所属カテゴリの ID（変更不可）
     */
    public Set<UUID> categoryIds() {
        return categoryIds;
    }

    /**
     * @return バリアント軸の名前（並び順、変更不可）
     */
    public List<String> axes() {
        return axes;
    }

    /**
     * @return 公開状態
     */
    public PublicationStatus status() {
        return status;
    }

    /**
     * @return 初回表示日時。null は一度も表示されていない
     */
    public Instant firstShownAt() {
        return firstShownAt;
    }

    /**
     * @return 公開期間
     */
    public PublicationPeriod publicationPeriod() {
        return publicationPeriod;
    }

    /**
     * @return 画像（表示順、変更不可）
     */
    public List<ProductImage> images() {
        return images;
    }

    /**
     * @return 代表画像（画像の並びの先頭。AC-37）。画像がなければ空
     */
    public Optional<ProductImage> representativeImage() {
        return images.stream().findFirst();
    }

    /**
     * @return バリアント（表示順、変更不可）
     */
    public List<Variant> variants() {
        return variants;
    }

    /**
     * @return バージョン
     */
    public long version() {
        return version;
    }

// ============================== ドメインの振る舞い ==============================

    /**
     * 商品を編集する（AC-18、19、25）。税抜価格が変わったら、商品価格変更のイベントを登録する。<br />
     * 税区分に有効な税率があること（AC-6）は、税モジュールに確かめる必要があるため、アプリケーション層で検証する。
     *
     * @param name          商品名
     * @param description   説明
     * @param taxCategoryId 税区分 ID
     * @param priceExclTax  税抜価格
     * @param categoryIds   所属カテゴリの ID
     * @param now           現在時刻
     */
    public void update(String name, String description, @NonNull UUID taxCategoryId, @NonNull PriceExclTax priceExclTax,
                       @NonNull Collection<UUID> categoryIds, @NonNull Instant now) {
        var verifiedName = verifyName(name);
        var verifiedDescription = verifyDescription(description);
        boolean priceChanged = !this.priceExclTax.equals(priceExclTax);

        this.name = verifiedName;
        this.description = verifiedDescription;
        this.taxCategoryId = taxCategoryId;
        this.priceExclTax = priceExclTax;
        this.categoryIds = Set.copyOf(categoryIds);

        if (priceChanged) {
            registerEvent(ProductPriceChanged.of(now, id, priceExclTax.yen()));
        }
    }

    /**
     * バリアントを登録する（AC-20〜24）。並びの最後に加える。
     *
     * @param variantCode バリアント品番
     * @param axisValues  軸の値（商品の軸と同じ並び）
     * @param now         現在時刻
     * @return 登録したバリアントの ID
     */
    public UUID addVariant(@NonNull VariantCode variantCode, @NonNull List<String> axisValues, @NonNull Instant now) {
        verifyAxisValues(axisValues);
        if (axes.isEmpty() && !variants.isEmpty()) {
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
     * バリアントを削除する（AC-71）。C 側に表示されたことのある商品のバリアントは削除できない。<br />
     * 公開中の商品の最後のバリアントも削除できない（AC-31 と同じ理由）。
     *
     * @param variantId バリアント ID
     * @param now       現在時刻
     */
    public void deleteVariant(@NonNull UUID variantId, @NonNull Instant now) {
        if (variants.stream().noneMatch(variant -> variant.id().equals(variantId))) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND);
        }
        if (hasBeenShown(now)) {
            throw BusinessException.of(ProductErrorCode.PRODUCT_ALREADY_SHOWN);
        }
        if (status == PublicationStatus.PUBLISHED && variants.size() == 1) {
            throw BusinessException.of(ProductErrorCode.PRODUCT_HAS_NO_VARIANTS);
        }

        this.variants = variants.stream().filter(variant -> !variant.id().equals(variantId)).toList();

        registerEvent(VariantDeleted.of(now, id, variantId));
    }

    /**
     * バリアントの並び順を変更する（AC-27）。送られた順に 1 から表示順を振り直す。<br />
     * すべてのバリアントと過不足なく一致しなければ、何も変えずに業務のエラーを投げる。
     *
     * @param orderedIds 並べたい順のバリアント ID
     */
    public void reorderVariants(@NonNull List<UUID> orderedIds) {
        var currentIds = variants.stream().map(Variant::id).collect(Collectors.toSet());
        if (orderedIds.size() != currentIds.size() || !currentIds.equals(new HashSet<>(orderedIds))) {
            throw BusinessException.of(ProductErrorCode.INVALID_VARIANT_ORDER);
        }

        var reordered = new ArrayList<Variant>();
        for (int i = 0; i < orderedIds.size(); i++) {
            var variantId = orderedIds.get(i);
            var variant = variants.stream().filter(v -> v.id().equals(variantId)).findFirst().orElseThrow();
            reordered.add(variant.withDisplayOrder(i + 1));
        }
        this.variants = List.copyOf(reordered);
    }

    /**
     * バリアントを販売停止にする（AC-28）。すでに販売停止なら何もしない（冪等）。<br />
     * 販売停止のバリアントも C 側の選択肢に出るが、選べない。販売終了の商品のバリアントも停止できる。
     *
     * @param variantId バリアント ID
     * @return 変わったら true。すでに販売停止なら false
     */
    public boolean suspendVariant(@NonNull UUID variantId) {
        return changeSalesSuspended(variantId, true);
    }

    /**
     * バリアントの販売停止を解除する（AC-28）。販売停止でなければ何もしない（冪等）。
     *
     * @param variantId バリアント ID
     * @return 変わったら true。販売停止でなければ false
     */
    public boolean resumeVariant(@NonNull UUID variantId) {
        return changeSalesSuspended(variantId, false);
    }

    /**
     * 画像を加えられるか（上限の 20 枚に達していないか）を確かめる（AC-40）。<br />
     * アップロードの受付の前に呼ぶ。登録のときも {@link #addImage} が確かめ直す。
     */
    public void verifyCanAddImage() {
        if (images.size() >= MAX_IMAGES) {
            throw BusinessException.of(ProductErrorCode.TOO_MANY_PRODUCT_IMAGES);
        }
    }

    /**
     * 画像を並びの最後に加える（AC-37、40）。ファイルは、呼び出す前に配信のバケットに置いておく。
     *
     * @param imageId    画像 ID
     * @param storageKey 配信のバケットのキー
     */
    public void addImage(@NonNull UUID imageId, @NonNull String storageKey) {
        verifyCanAddImage();
        var nextOrder = images.stream().mapToInt(ProductImage::displayOrder).max().orElse(0) + 1;
        var added = new ArrayList<>(images);
        added.add(new ProductImage(imageId, storageKey, nextOrder));
        this.images = List.copyOf(added);
    }

    /**
     * 画像の並び順を変更する（AC-37）。送られた順に 1 から表示順を振り直す。先頭が代表画像になる。<br />
     * すべての画像と過不足なく一致しなければ、何も変えずに業務のエラーを投げる。
     *
     * @param orderedIds 並べたい順の画像 ID
     */
    public void reorderImages(@NonNull List<UUID> orderedIds) {
        var currentIds = images.stream().map(ProductImage::id).collect(Collectors.toSet());
        if (orderedIds.size() != currentIds.size() || !currentIds.equals(new HashSet<>(orderedIds))) {
            throw BusinessException.of(ProductErrorCode.INVALID_IMAGE_ORDER);
        }

        var reordered = new ArrayList<ProductImage>();
        for (int i = 0; i < orderedIds.size(); i++) {
            var imageId = orderedIds.get(i);
            var image = images.stream().filter(candidate -> candidate.id().equals(imageId)).findFirst().orElseThrow();
            reordered.add(image.withDisplayOrder(i + 1));
        }
        this.images = List.copyOf(reordered);
    }

    /**
     * 画像を削除する。その画像を指定していたバリアントの指定は外す（代表画像に戻る）。<br />
     * 配信のバケットのファイルは、登録したイベントで消す。C 側に表示されたことのある商品の画像も削除できる。
     *
     * @param imageId 画像 ID
     * @param now     現在時刻
     */
    public void deleteImage(@NonNull UUID imageId, @NonNull Instant now) {
        var image = images.stream().filter(candidate -> candidate.id().equals(imageId)).findFirst()
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));

        this.images = images.stream().filter(candidate -> !candidate.id().equals(imageId)).toList();
        this.variants = variants.stream()
                .map(variant -> imageId.equals(variant.imageId()) ? variant.withImageId(null) : variant)
                .toList();

        registerEvent(ProductImageDeleted.of(now, id, imageId, image.storageKey()));
    }

    /**
     * バリアントの画像を指定する（AC-38）。指定できるのは、その商品の画像だけ。
     *
     * @param variantId バリアント ID
     * @param imageId   画像 ID。null で指定を外す（代表画像を使う）
     */
    public void setVariantImage(@NonNull UUID variantId, UUID imageId) {
        if (variants.stream().noneMatch(variant -> variant.id().equals(variantId))) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND);
        }
        if (imageId != null && images.stream().noneMatch(image -> image.id().equals(imageId))) {
            throw BusinessException.of(ProductErrorCode.INVALID_VARIANT_IMAGE);
        }
        this.variants = variants.stream()
                .map(variant -> variant.id().equals(variantId) ? variant.withImageId(imageId) : variant)
                .toList();
    }

    /**
     * 公開する（AC-29、31）。すでに公開なら何もしない（冪等）。
     *
     * @param now 現在時刻
     * @return 状態が変わったら true。すでにその状態なら false
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
     * 非公開にする（AC-30）。すでに非公開なら何もしない（冪等）。
     *
     * @param now 現在時刻
     * @return 状態が変わったら true。すでにその状態なら false
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
     * 販売終了にする（AC-32）。すでに販売終了なら何もしない（冪等）。
     *
     * @param now 現在時刻
     * @return 状態が変わったら true。すでにその状態なら false
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
     * 公開期間を変える（AC-34〜36）。終了が開始より後であることは、公開期間（値オブジェクト）が検証する。<br />
     * 公開中なら、表示前の初回表示日時を計算し直す（docs/modules/catalog.md）。表示されて確定した値は変わらない。
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
     * 削除する（AC-33）。C 側に表示されたことのある商品は削除できない。<br />
     * 削除そのものはリポジトリが行う。ここでは削除できるかを検証し、イベントを登録する。
     *
     * @param now 現在時刻
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
     * C 側に表示されたことがあるかを返す。初回表示日時が空か未来なら、まだ表示されていない。
     *
     * @param now 現在時刻
     * @return 表示されたことがあれば true
     */
    public boolean hasBeenShown(@NonNull Instant now) {
        return firstShownAt != null && !firstShownAt.isAfter(now);
    }

// ============================== プライベートメソッド ==============================

    /**
     * バリアントの販売停止を変える。商品にないバリアントなら、見つからない業務のエラーを投げる。
     *
     * @param variantId      バリアント ID
     * @param salesSuspended 販売停止
     * @return 変わったら true。すでにその状態なら false
     */
    private boolean changeSalesSuspended(UUID variantId, boolean salesSuspended) {
        var variant = variants.stream().filter(v -> v.id().equals(variantId)).findFirst()
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
        if (variant.salesSuspended() == salesSuspended) {
            return false;
        }
        this.variants = variants.stream()
                .map(v -> v.id().equals(variantId) ? v.withSalesSuspended(salesSuspended) : v)
                .toList();
        return true;
    }

    /**
     * 公開したとき（または公開中に公開期間を変えたとき）の初回表示日時を求める（docs/modules/catalog.md）。<br />
     * 確定済み（過去）ならそのまま。そうでなければ「現在時刻と公開開始日時の遅いほう」。
     * その日時が公開終了日時以降なら、表示されないので空にする。
     *
     * @param now 現在時刻
     * @return 初回表示日時
     */
    private Instant calculateFirstShownAt(Instant now) {
        if (hasBeenShown(now)) {
            return firstShownAt;
        }
        var start = publicationPeriod.publishStart();
        var candidate = start != null && start.isAfter(now) ? start : now;
        var end = publicationPeriod.publishEnd();
        return end != null && !candidate.isBefore(end) ? null : candidate;
    }

    /**
     * 未来（表示予定）の初回表示日時を空に戻す。確定済みなら何もしない。
     *
     * @param now 現在時刻
     */
    private void clearScheduledFirstShownAt(Instant now) {
        if (!hasBeenShown(now)) {
            this.firstShownAt = null;
        }
    }

    /**
     * 販売終了でないことを検証する（AC-32）。
     */
    private void verifyNotDiscontinued() {
        if (status == PublicationStatus.DISCONTINUED) {
            throw BusinessException.of(ProductErrorCode.PRODUCT_DISCONTINUED);
        }
    }

    /**
     * @return 新しいバリアントの表示順（最大＋1。なければ 1）
     */
    private int nextVariantDisplayOrder() {
        return variants.stream().mapToInt(Variant::displayOrder).max().orElse(0) + 1;
    }

    /**
     * 軸の値が、商品の軸と同じ数で、それぞれ 1〜50 文字であることを検証する（AC-22）。<br />
     * 軸キーの区切り文字は、値に含められない。
     *
     * @param axisValues 軸の値
     */
    private void verifyAxisValues(List<String> axisValues) {
        if (axisValues.size() != axes.size()
                || !axisValues.stream().allMatch(value -> isValidText(value, AXIS_VALUE_MAX_LENGTH)
                && !value.contains(Variant.AXIS_KEY_SEPARATOR))) {
            throw BusinessException.of(ProductErrorCode.INVALID_AXIS_VALUES);
        }
    }

    /**
     * 商品名が 1〜200 文字（空白のみは不可）であることを検証する。
     *
     * @param name 商品名
     * @return 商品名
     */
    private static String verifyName(String name) {
        if (!isValidText(name, NAME_MAX_LENGTH)) {
            throw BusinessException.of(ProductErrorCode.INVALID_PRODUCT_NAME);
        }
        return name;
    }

    /**
     * 説明が 10,000 文字以内であることを検証する。空でもよい。
     *
     * @param description 説明
     * @return 説明
     */
    private static String verifyDescription(String description) {
        if (description == null || description.codePointCount(0, description.length()) > DESCRIPTION_MAX_LENGTH) {
            throw BusinessException.of(ProductErrorCode.INVALID_PRODUCT_DESCRIPTION);
        }
        return description;
    }

    /**
     * バリアント軸が 0〜3 つで、名前が 1〜30 文字、重複がないことを検証する（AC-13）。
     *
     * @param axes バリアント軸の名前
     * @return 変更できない一覧
     */
    private static List<String> verifyAxes(List<String> axes) {
        if (axes.size() > MAX_AXES
                || !axes.stream().allMatch(axis -> isValidText(axis, AXIS_NAME_MAX_LENGTH))
                || new LinkedHashSet<>(axes).size() != axes.size()) {
            throw BusinessException.of(ProductErrorCode.INVALID_VARIANT_AXES);
        }
        return List.copyOf(axes);
    }

    /**
     * 文字列が、空白のみでなく、最大の文字数以内かを返す。
     *
     * @param text      文字列
     * @param maxLength 最大の文字数
     * @return 正しければ true
     */
    private static boolean isValidText(String text, int maxLength) {
        return text != null && !text.isBlank() && text.codePointCount(0, text.length()) <= maxLength;
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
     * @param variants          バリアント
     * @param version           バージョン
     */
    public record Snapshot(UUID id, ProductCode productCode, String name, String description, UUID taxCategoryId,
                           PriceExclTax priceExclTax, Set<UUID> categoryIds, List<String> axes,
                           PublicationStatus status, Instant firstShownAt, PublicationPeriod publicationPeriod,
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
