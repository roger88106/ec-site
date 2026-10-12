package com.ecsite.module.tax.domain.taxcategory;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.base.AggregateRoot;
import com.ecsite.common.model.value.BoundedText;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.jmolecules.ddd.annotation.Identity;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 税区分の Aggregate Root。<br />
 * 名称と、税率の一覧を持つ。税率は 1 件ずつではなく、一覧ごと置き換える（docs/modules/tax.md）。
 */
@Getter
@Accessors(fluent = true)
@org.jmolecules.ddd.annotation.AggregateRoot
public class TaxCategory extends AggregateRoot {

    /**
     * 名称の最大の文字数
     */
    static final int NAME_MAX_LENGTH = 50;

    /**
     * 税率の並び順：適用開始日時の古い順（空白が先頭）
     */
    private static final Comparator<TaxRate> BY_EFFECTIVE_FROM = Comparator.comparing(
            (TaxRate rate) -> rate.period().effectiveFrom(), Comparator.nullsFirst(Comparator.naturalOrder()));

// ============================== フィールド定義 ==============================

    /**
     * 税区分 ID
     */
    @Identity
    private final UUID id;

    /**
     * 名称
     */
    private final String name;

    /**
     * 税率の一覧（適用開始日時の古い順）
     */
    private List<TaxRate> rates;

    /**
     * バージョン（楽観ロック）。リポジトリが保存するときに上げる
     */
    private final long version;

// ============================== コンストラクタ ==============================

    /**
     * 全属性コンストラクタ
     *
     * @param id      税区分 ID
     * @param name    名称
     * @param rates   税率の一覧
     * @param version バージョン
     */
    private TaxCategory(@NonNull UUID id, String name, @NonNull List<TaxRate> rates, long version) {
        if (!BoundedText.isFilledWithin(name, NAME_MAX_LENGTH)) {
            throw BusinessException.of(TaxErrorCode.INVALID_TAX_CATEGORY_NAME, List.of(NAME_MAX_LENGTH));
        }
        this.id = id;
        this.name = name;
        this.rates = rates.stream().sorted(BY_EFFECTIVE_FROM).toList();
        this.version = version;
    }

    /**
     * 税率のない税区分を作る（AC-1）。
     *
     * @param id   税区分 ID
     * @param name 名称
     * @return 税区分
     */
    static TaxCategory create(UUID id, String name) {
        return new TaxCategory(id, name, List.of(), 0);
    }

// ============================== ドメインの振る舞い ==============================

    /**
     * 指定した時点の税率を返す（AC-4）。期間は重複しないので、該当は 0 件か 1 件。
     *
     * @param instant 時点
     * @return その時点を含む税率。なければ空
     */
    public Optional<TaxRate> rateAt(Instant instant) {
        return rates.stream().filter(rate -> rate.period().contains(instant)).findFirst();
    }

    /**
     * 税率の一覧を置き換える（AC-3〜5）。<br />
     * 検証に失敗した場合は、一覧を変えずに業務のエラーを投げる。
     *
     * @param drafts 置き換え後の税率の一覧。既存の税率を残す場合は ID を指定する
     * @param now    現在時刻
     */
    public void replaceRates(@NonNull List<TaxRateDraft> drafts, @NonNull Instant now) {
        if (drafts.isEmpty()) {
            throw BusinessException.of(TaxErrorCode.TAX_RATES_REQUIRED);
        }
        var currentById = rates.stream().collect(Collectors.toMap(TaxRate::id, Function.identity()));
        var draftsById = verifyReferences(drafts, currentById);

        // 既存の税率と照らし合わせて、変更できるかを判定する（AC-5）
        rates.forEach(current -> verifyChangeable(current, draftsById.get(current.id()), now));
        drafts.forEach(draft -> verifyNoPastDateTime(draft, currentById.get(draft.id()), now));

        // 一覧の形を検証する（AC-3）
        var replaced = drafts.stream()
                // 新しい税率（ID なし）には ID を振る
                .map(draft -> new TaxRate(Objects.requireNonNullElseGet(draft.id(), UUID::randomUUID),
                        draft.ratePercent(), draft.period()))
                .sorted(BY_EFFECTIVE_FROM)
                .toList();
        verifyContinuous(replaced);

        this.rates = replaced;
    }

// ============================== プライベートメソッド ==============================

    /**
     * 指定された既存の税率の ID が、この税区分にあり、重複していないことを検証する。
     *
     * @param drafts      置き換え後の税率の一覧
     * @param currentById 既存の税率
     * @return 既存の税率を指定した要素（ID → 要素）
     */
    private static Map<UUID, TaxRateDraft> verifyReferences(List<TaxRateDraft> drafts, Map<UUID, TaxRate> currentById) {
        var seen = new HashSet<UUID>();
        for (var draft : drafts) {
            // ID のある税率（今の税率の変更）は、税区分にあって重複しないこと。新しい税率は ID を持たない
            if (draft.id() != null && (!currentById.containsKey(draft.id()) || !seen.add(draft.id()))) {
                throw BusinessException.of(TaxErrorCode.INVALID_TAX_RATE_ID);
            }
        }
        return drafts.stream()
                .filter(draft -> draft.id() != null)
                .collect(Collectors.toMap(TaxRateDraft::id, Function.identity()));
    }

    /**
     * 既存の税率を、置き換え後の内容に変更できるかを検証する（AC-5）。
     * <ul>
     *     <li>終了済み：変更も削除もできない</li>
     *     <li>開始済み（開始が空白のものを含む）：率と開始は変更できず、削除もできない。終了は変更できる</li>
     *     <li>未開始：変更も削除もできる</li>
     * </ul>
     *
     * @param current 既存の税率
     * @param draft   置き換え後の内容。削除する場合は null
     * @param now     現在時刻
     */
    private static void verifyChangeable(TaxRate current, TaxRateDraft draft, Instant now) {
        var period = current.period();
        if (period.hasEnded(now)) {
            if (draft == null || !current.sameAs(draft)) {
                throw BusinessException.of(TaxErrorCode.TAX_RATE_ENDED_UNCHANGEABLE);
            }
        } else if (period.hasStarted(now)) {
            if (draft == null
                    || !current.ratePercent().equals(draft.ratePercent())
                    || !Objects.equals(period.effectiveFrom(), draft.period().effectiveFrom())) {
                throw BusinessException.of(TaxErrorCode.TAX_RATE_STARTED_UNCHANGEABLE);
            }
        }
    }

    /**
     * 新たに設定する開始・終了が、空白か現在より後であることを検証する（AC-5）。
     * 既存の税率で変えていない日時は、新たに設定したものとみなさない。
     *
     * @param draft   置き換え後の税率
     * @param current 既存の税率。新しい税率なら null
     * @param now     現在時刻
     */
    private static void verifyNoPastDateTime(TaxRateDraft draft, TaxRate current, Instant now) {
        var from = draft.period().effectiveFrom();
        var to = draft.period().effectiveTo();
        boolean fromChanged = current == null || !Objects.equals(current.period().effectiveFrom(), from);
        boolean toChanged = current == null || !Objects.equals(current.period().effectiveTo(), to);
        // 変わった開始・終了の日時が現在以前なら断る（新しい税率は、両方とも変わったとみなす）
        if ((fromChanged && isNotFuture(from, now)) || (toChanged && isNotFuture(to, now))) {
            throw BusinessException.of(TaxErrorCode.TAX_RATE_PAST_DATETIME);
        }
    }

    /**
     * 日時が設定されていて、現在より後でないか。
     *
     * @param instant 日時。空白は null
     * @param now     現在時刻
     * @return 現在以前の日時なら true
     */
    private static boolean isNotFuture(Instant instant, Instant now) {
        return instant != null && !instant.isAfter(now);
    }

    /**
     * 期間が重複せず、途切れがなく、最後の税率の終了が空白であることを検証する（AC-3）。
     *
     * @param sorted 適用開始日時の古い順に並べた税率の一覧
     */
    private static void verifyContinuous(List<TaxRate> sorted) {
        for (int i = 1; i < sorted.size(); i++) {
            var previousEnd = sorted.get(i - 1).period().effectiveTo();
            var start = sorted.get(i).period().effectiveFrom();
            // どちらかが空白なら、無限に伸びる側と重なる
            if (previousEnd == null || start == null || previousEnd.isAfter(start)) {
                throw BusinessException.of(TaxErrorCode.TAX_RATE_PERIODS_OVERLAP);
            }
            if (previousEnd.isBefore(start)) {
                throw BusinessException.of(TaxErrorCode.TAX_RATE_PERIODS_GAP);
            }
        }
        if (sorted.getLast().period().effectiveTo() != null) {
            throw BusinessException.of(TaxErrorCode.TAX_RATE_LAST_PERIOD_NOT_OPEN_ENDED);
        }
    }

// ============================== スナップショット関連処理 ==============================

    /**
     * 税区分のスナップショット
     *
     * @param id      税区分 ID
     * @param name    名称
     * @param rates   税率の一覧
     * @param version バージョン
     */
    public record Snapshot(UUID id, String name, List<TaxRate> rates, long version) {
    }

    /**
     * スナップショットを取得する
     *
     * @return 税区分のスナップショット
     */
    public Snapshot toSnapshot() {
        return new Snapshot(id, name, rates, version);
    }

    /**
     * スナップショットから復元する
     *
     * @param snapshot 税区分のスナップショット
     */
    TaxCategory(Snapshot snapshot) {
        this(snapshot.id(), snapshot.name(), snapshot.rates(), snapshot.version());
    }
}
