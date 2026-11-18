package com.ecsite.module.catalog.domain.product.vo;

import com.ecsite.module.catalog.domain.ProductErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;

import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 公開（公開状態・公開期間・初回表示日時）の単体テスト
 *
 * <p>各操作の遷移と結果、表示中の判定、一度でも表示したかの判定、不変条件を確認する。現在時刻は引数で固定する。
 */
class PublicationTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 1 日
     */
    private static final Duration ONE_DAY = Duration.ofDays(1);

    /**
     * 明日の同じ時刻
     */
    private static final Instant TOMORROW = NOW.plus(ONE_DAY);

    /**
     * 昨日の同じ時刻
     */
    private static final Instant YESTERDAY = NOW.minus(ONE_DAY);

    /**
     * 10 日後
     */
    private static final Instant TEN_DAYS_LATER = NOW.plus(ONE_DAY.multipliedBy(10));

    // ============================== テスト ==============================

    @Nested
    @DisplayName("公開")
    class Publish {

        @Test
        @DisplayName("非公開を公開にすると、公開状態が公開になる")
        void changesStatusToPublished() {
            // 準備：非公開
            var publication = unpublished(PublicationPeriod.UNLIMITED, null);

            // 操作：公開する
            var result = publication.publish(NOW);

            // 確認：公開
            assertThat(result.status()).isEqualTo(PublicationStatus.PUBLISHED);
        }

        @Test
        @DisplayName("非公開を公開にすると、初回表示日時は公開した時刻になる")
        void recordsFirstShownAt() {
            // 準備：期間に限りのない非公開
            var publication = unpublished(PublicationPeriod.UNLIMITED, null);

            // 操作：公開する
            var result = publication.publish(NOW);

            // 確認：初回表示日時が公開した時刻
            assertThat(result.firstShownAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("公開しても、元の値は変わらない")
        void keepsOriginalValue() {
            // 準備：非公開
            var publication = unpublished(PublicationPeriod.UNLIMITED, null);

            // 操作：公開する
            publication.publish(NOW);

            // 確認：元の値は非公開のまま
            assertThat(publication.status()).isEqualTo(PublicationStatus.UNPUBLISHED);
        }

        @Test
        @DisplayName("公開中を公開にしても、同じ値を返す")
        void returnsSameValueWhenAlreadyPublished() {
            // 準備：公開中
            var publication = published(PublicationPeriod.UNLIMITED, NOW);

            // 操作：翌日に公開する
            var result = publication.publish(TOMORROW);

            // 確認：同じ値で、初回表示日時も変わらない
            assertThat(result).isSameAs(publication);
        }

        @Test
        @DisplayName("販売終了を公開にすると、PRODUCT_DISCONTINUED で拒否される")
        void rejectsPublishingDiscontinued() {
            // 準備：販売終了
            var publication = discontinued(PublicationPeriod.UNLIMITED, null);

            // 操作と確認：業務のエラー PRODUCT_DISCONTINUED で拒否される
            assertBusinessError(() -> publication.publish(NOW), ProductErrorCode.PRODUCT_DISCONTINUED);
        }

        @Test
        @DisplayName("表示されて確定した初回表示日時は、非公開から再び公開にしても変わらない")
        void keepsConfirmedFirstShownAt() {
            // 準備：昨日表示されて、非公開になった
            var publication = new Publication(PublicationStatus.UNPUBLISHED, PublicationPeriod.UNLIMITED, YESTERDAY);

            // 操作：今日公開する
            var result = publication.publish(NOW);

            // 確認：最初に表示した日時のまま
            assertThat(result.firstShownAt()).isEqualTo(YESTERDAY);
        }

        @Test
        @DisplayName("公開開始日時が未来なら、初回表示日時は公開開始日時になる")
        void usesFuturePublishStart() {
            // 準備：公開開始日時が明日の非公開
            var publication = unpublished(PublicationPeriod.of(TOMORROW, null), null);

            // 操作：今日公開する
            var result = publication.publish(NOW);

            // 確認：初回表示日時が公開開始日時
            assertThat(result.firstShownAt()).isEqualTo(TOMORROW);
        }

        @Test
        @DisplayName("公開開始日時が過去なら、初回表示日時は公開した時刻になる")
        void usesNowWhenPublishStartIsPast() {
            // 準備：公開開始日時が昨日の非公開
            var publication = unpublished(PublicationPeriod.of(YESTERDAY, null), null);

            // 操作：今日公開する
            var result = publication.publish(NOW);

            // 確認：初回表示日時が公開した時刻
            assertThat(result.firstShownAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("公開開始日時が公開した時刻ちょうどなら、初回表示日時は公開した時刻になる")
        void usesNowWhenPublishStartIsNow() {
            // 準備：公開開始日時が今の非公開
            var publication = unpublished(PublicationPeriod.of(NOW, null), null);

            // 操作：今公開する
            var result = publication.publish(NOW);

            // 確認：初回表示日時が公開した時刻
            assertThat(result.firstShownAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("公開した時刻が公開終了日時ちょうどなら、表示されないので初回表示日時は空のまま")
        void leavesFirstShownAtEmptyAtPublishEnd() {
            // 準備：公開終了日時が今の非公開
            var publication = unpublished(PublicationPeriod.of(null, NOW), null);

            // 操作：今公開する
            var result = publication.publish(NOW);

            // 確認：初回表示日時が空
            assertThat(result.firstShownAt()).isNull();
        }

        @Test
        @DisplayName("公開した時刻が公開終了日時の 1 秒前なら、初回表示日時は公開した時刻になる")
        void recordsFirstShownAtJustBeforePublishEnd() {
            // 準備：公開終了日時が 1 秒後の非公開
            var publication = unpublished(PublicationPeriod.of(null, NOW.plusSeconds(1)), null);

            // 操作：今公開する
            var result = publication.publish(NOW);

            // 確認：初回表示日時が公開した時刻
            assertThat(result.firstShownAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("公開終了日時を過ぎてから公開すると、表示されないので初回表示日時は空のまま")
        void leavesFirstShownAtEmptyAfterPublishEnd() {
            // 準備：明日から 10 日後までの期間の非公開
            var publication = unpublished(PublicationPeriod.of(TOMORROW, TEN_DAYS_LATER), null);

            // 操作：終了日時を過ぎてから公開する
            var result = publication.publish(TEN_DAYS_LATER.plusSeconds(1));

            // 確認：初回表示日時が空
            assertThat(result.firstShownAt()).isNull();
        }

        @Test
        @DisplayName("公開中で初回表示日時がない値を公開にしても、同じ値を返す")
        void returnsSameValueWhenPublishedWithoutFirstShownAt() {
            // 準備：公開の時点で公開終了を過ぎていた公開中
            var publication = publishedWithoutFirstShownAt();

            // 操作：公開する
            var result = publication.publish(NOW);

            // 確認：同じ値
            assertThat(result).isSameAs(publication);
        }
    }

    @Nested
    @DisplayName("非公開")
    class Unpublish {

        @Test
        @DisplayName("公開中を非公開にすると、公開状態が非公開になる")
        void changesStatusToUnpublished() {
            // 準備：公開中
            var publication = published(PublicationPeriod.UNLIMITED, NOW);

            // 操作：翌日に非公開にする
            var result = publication.unpublish(TOMORROW);

            // 確認：非公開
            assertThat(result.status()).isEqualTo(PublicationStatus.UNPUBLISHED);
        }

        @Test
        @DisplayName("表示されて確定した初回表示日時は、非公開にしても変わらない")
        void keepsConfirmedFirstShownAt() {
            // 準備：今日表示された公開中
            var publication = published(PublicationPeriod.UNLIMITED, NOW);

            // 操作：翌日に非公開にする
            var result = publication.unpublish(TOMORROW);

            // 確認：初回表示日時のまま
            assertThat(result.firstShownAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("表示前（初回表示日時が未来）に非公開にすると、初回表示日時は空に戻る")
        void clearsScheduledFirstShownAt() {
            // 準備：公開開始日時が明日で、今日公開した状態
            var publication = published(PublicationPeriod.of(TOMORROW, null), TOMORROW);

            // 操作：今日のうちに非公開にする
            var result = publication.unpublish(NOW);

            // 確認：初回表示日時が空
            assertThat(result.firstShownAt()).isNull();
        }

        @Test
        @DisplayName("初回表示日時ちょうどの時刻には表示済みなので、非公開にしても初回表示日時は変わらない")
        void keepsFirstShownAtAtExactTime() {
            // 準備：初回表示日時が明日の公開中
            var publication = published(PublicationPeriod.of(TOMORROW, null), TOMORROW);

            // 操作：初回表示日時ちょうどに非公開にする
            var result = publication.unpublish(TOMORROW);

            // 確認：初回表示日時のまま
            assertThat(result.firstShownAt()).isEqualTo(TOMORROW);
        }

        @Test
        @DisplayName("非公開を非公開にしても、同じ値を返す")
        void returnsSameValueWhenAlreadyUnpublished() {
            // 準備：非公開
            var publication = unpublished(PublicationPeriod.UNLIMITED, null);

            // 操作：非公開にする
            var result = publication.unpublish(NOW);

            // 確認：同じ値
            assertThat(result).isSameAs(publication);
        }

        @Test
        @DisplayName("販売終了を非公開にすると、PRODUCT_DISCONTINUED で拒否される")
        void rejectsUnpublishingDiscontinued() {
            // 準備：販売終了
            var publication = discontinued(PublicationPeriod.UNLIMITED, null);

            // 操作と確認：業務のエラー PRODUCT_DISCONTINUED で拒否される
            assertBusinessError(() -> publication.unpublish(NOW), ProductErrorCode.PRODUCT_DISCONTINUED);
        }

        @Test
        @DisplayName("公開中で初回表示日時がない値を非公開にすると、非公開になり初回表示日時は空のまま")
        void unpublishesPublishedWithoutFirstShownAt() {
            // 準備：公開の時点で公開終了を過ぎていた公開中
            var publication = publishedWithoutFirstShownAt();

            // 操作：非公開にする
            var result = publication.unpublish(NOW);

            // 確認：非公開で、初回表示日時は空
            assertThat(result).isEqualTo(new Publication(PublicationStatus.UNPUBLISHED, publication.publicationPeriod(), null));
        }
    }

    @Nested
    @DisplayName("販売終了")
    class Discontinue {

        @ParameterizedTest
        @EnumSource(value = PublicationStatus.class, names = {"UNPUBLISHED", "PUBLISHED"})
        @DisplayName("非公開と公開中は、販売終了にできる")
        void changesStatusToDiscontinued(PublicationStatus status) {
            // 準備：非公開または公開中（初回表示日時は今日）
            var publication = new Publication(status, PublicationPeriod.UNLIMITED, NOW);

            // 操作：販売終了にする
            var result = publication.discontinue(NOW);

            // 確認：販売終了
            assertThat(result.status()).isEqualTo(PublicationStatus.DISCONTINUED);
        }

        @Test
        @DisplayName("表示されて確定した初回表示日時は、販売終了にしても変わらない")
        void keepsConfirmedFirstShownAt() {
            // 準備：今日表示された公開中
            var publication = published(PublicationPeriod.UNLIMITED, NOW);

            // 操作：翌日に販売終了にする
            var result = publication.discontinue(TOMORROW);

            // 確認：初回表示日時のまま
            assertThat(result.firstShownAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("表示前（初回表示日時が未来）に販売終了にすると、初回表示日時は空に戻る")
        void clearsScheduledFirstShownAt() {
            // 準備：公開開始日時が明日で、今日公開した状態
            var publication = published(PublicationPeriod.of(TOMORROW, null), TOMORROW);

            // 操作：今日のうちに販売終了にする
            var result = publication.discontinue(NOW);

            // 確認：初回表示日時が空
            assertThat(result.firstShownAt()).isNull();
        }

        @Test
        @DisplayName("販売終了を販売終了にしても、同じ値を返す")
        void returnsSameValueWhenAlreadyDiscontinued() {
            // 準備：販売終了
            var publication = discontinued(PublicationPeriod.UNLIMITED, null);

            // 操作：販売終了にする
            var result = publication.discontinue(NOW);

            // 確認：同じ値
            assertThat(result).isSameAs(publication);
        }

        @Test
        @DisplayName("公開中で初回表示日時がない値を販売終了にすると、販売終了になり初回表示日時は空のまま")
        void discontinuesPublishedWithoutFirstShownAt() {
            // 準備：公開の時点で公開終了を過ぎていた公開中
            var publication = publishedWithoutFirstShownAt();

            // 操作：販売終了にする
            var result = publication.discontinue(NOW);

            // 確認：販売終了で、初回表示日時は空
            assertThat(result).isEqualTo(new Publication(PublicationStatus.DISCONTINUED, publication.publicationPeriod(), null));
        }
    }

    @Nested
    @DisplayName("公開期間の変更")
    class ChangePublicationPeriod {

        @Test
        @DisplayName("公開期間を変えると、新しい公開期間になる")
        void replacesPeriod() {
            // 準備：非公開と、明日から 10 日後までの期間
            var publication = unpublished(PublicationPeriod.UNLIMITED, null);
            var period = PublicationPeriod.of(TOMORROW, TEN_DAYS_LATER);

            // 操作：期間を変える
            var result = publication.changePublicationPeriod(period, NOW);

            // 確認：新しい期間になる
            assertThat(result.publicationPeriod()).isEqualTo(period);
        }

        @Test
        @DisplayName("非公開の公開期間を変えても、初回表示日時は空のまま")
        void leavesFirstShownAtOfUnpublished() {
            // 準備：非公開
            var publication = unpublished(PublicationPeriod.UNLIMITED, null);

            // 操作：開始日時を明日にする
            var result = publication.changePublicationPeriod(PublicationPeriod.of(TOMORROW, null), NOW);

            // 確認：初回表示日時が空
            assertThat(result.firstShownAt()).isNull();
        }

        @Test
        @DisplayName("販売終了の公開期間も変えられる")
        void changesPeriodOfDiscontinued() {
            // 準備：販売終了
            var publication = discontinued(PublicationPeriod.UNLIMITED, null);
            var period = PublicationPeriod.of(TOMORROW, null);

            // 操作：開始日時を明日にする
            var result = publication.changePublicationPeriod(period, NOW);

            // 確認：新しい期間になり、販売終了のまま
            assertThat(result).isEqualTo(new Publication(PublicationStatus.DISCONTINUED, period, null));
        }

        @Test
        @DisplayName("公開中に開始日時を別の未来に変えると、初回表示日時は新しい開始日時になる")
        void recalculatesScheduledFirstShownAt() {
            // 準備：公開開始日時が明日で、今日公開した状態
            var publication = published(PublicationPeriod.of(TOMORROW, null), TOMORROW);
            var later = NOW.plus(ONE_DAY.multipliedBy(3));

            // 操作：開始日時を 3 日後に変える
            var result = publication.changePublicationPeriod(PublicationPeriod.of(later, null), NOW);

            // 確認：初回表示日時が 3 日後
            assertThat(result.firstShownAt()).isEqualTo(later);
        }

        @Test
        @DisplayName("公開中に開始日時を空にすると、初回表示日時は変えた時刻になる")
        void usesChangedAtWhenStartIsCleared() {
            // 準備：公開開始日時が明日で、今日公開した状態
            var publication = published(PublicationPeriod.of(TOMORROW, null), TOMORROW);
            var changedAt = NOW.plusSeconds(60);

            // 操作：1 分後に期間を空にする
            var result = publication.changePublicationPeriod(PublicationPeriod.UNLIMITED, changedAt);

            // 確認：変えた時刻
            assertThat(result.firstShownAt()).isEqualTo(changedAt);
        }

        @Test
        @DisplayName("表示されて確定した初回表示日時は、公開期間を変えても変わらない")
        void keepsConfirmedFirstShownAt() {
            // 準備：今日表示された公開中
            var publication = published(PublicationPeriod.UNLIMITED, NOW);

            // 操作：1 分後に開始日時を明日にする
            var result = publication.changePublicationPeriod(PublicationPeriod.of(TOMORROW, null), NOW.plusSeconds(60));

            // 確認：初回表示日時のまま
            assertThat(result.firstShownAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("公開中に公開終了日時を過ぎた期間にすると、表示されないので初回表示日時は空になる")
        void clearsFirstShownAtWhenPeriodHasEnded() {
            // 準備：公開開始日時が明日で、今日公開した状態
            var publication = published(PublicationPeriod.of(TOMORROW, null), TOMORROW);

            // 操作：終了日時を今にする
            var result = publication.changePublicationPeriod(PublicationPeriod.of(null, NOW), NOW);

            // 確認：初回表示日時が空
            assertThat(result.firstShownAt()).isNull();
        }

        @Test
        @DisplayName("公開中の公開期間を変えても、公開のまま")
        void keepsStatusPublished() {
            // 準備：公開中
            var publication = published(PublicationPeriod.UNLIMITED, NOW);

            // 操作：終了日時を設定する
            var result = publication.changePublicationPeriod(PublicationPeriod.of(null, TOMORROW), NOW);

            // 確認：公開のまま
            assertThat(result.status()).isEqualTo(PublicationStatus.PUBLISHED);
        }

        @Test
        @DisplayName("公開中で初回表示日時がない値の公開終了日時を未来に延ばすと、初回表示日時は変えた時刻になる")
        void recordsFirstShownAtWhenEndIsExtended() {
            // 準備：公開の時点で公開終了を過ぎていた公開中
            var publication = publishedWithoutFirstShownAt();

            // 操作：公開終了日時を明日にする
            var result = publication.changePublicationPeriod(PublicationPeriod.of(null, TOMORROW), NOW);

            // 確認：初回表示日時が変えた時刻
            assertThat(result.firstShownAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("公開中で初回表示日時がない値の公開終了日時を別の過去にしても、初回表示日時は空のまま")
        void leavesFirstShownAtEmptyWhenEndStaysPast() {
            // 準備：公開の時点で公開終了を過ぎていた公開中
            var publication = publishedWithoutFirstShownAt();

            // 操作：公開終了日時を別の過去にする
            var result = publication.changePublicationPeriod(PublicationPeriod.of(null, NOW.minusSeconds(1)), NOW);

            // 確認：初回表示日時が空のまま
            assertThat(result.firstShownAt()).isNull();
        }
    }

    @Nested
    @DisplayName("表示中か")
    class IsVisibleAt {

        @Test
        @DisplayName("公開中で期間に限りがなければ、表示中")
        void isVisibleWhenPublishedWithoutLimit() {
            // 準備：公開中
            var publication = published(PublicationPeriod.UNLIMITED, NOW);

            // 操作と確認：表示中
            assertThat(publication.isVisibleAt(NOW)).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = PublicationStatus.class, names = {"UNPUBLISHED", "DISCONTINUED"})
        @DisplayName("公開中でなければ、表示中ではない")
        void isInvisibleWhenNotPublished(PublicationStatus status) {
            // 準備：非公開または販売終了
            var publication = new Publication(status, PublicationPeriod.UNLIMITED, NOW);

            // 操作と確認：表示中ではない
            assertThat(publication.isVisibleAt(NOW)).isFalse();
        }

        @Test
        @DisplayName("公開開始日時の 1 秒前は、表示中ではない")
        void isInvisibleJustBeforeStart() {
            // 準備：明日から 10 日後までの期間の公開中
            var publication = published(PublicationPeriod.of(TOMORROW, TEN_DAYS_LATER), TOMORROW);

            // 操作と確認：開始の 1 秒前は表示中ではない
            assertThat(publication.isVisibleAt(TOMORROW.minusSeconds(1))).isFalse();
        }

        @Test
        @DisplayName("公開開始日時ちょうどから、表示中")
        void isVisibleFromStart() {
            // 準備：明日から 10 日後までの期間の公開中
            var publication = published(PublicationPeriod.of(TOMORROW, TEN_DAYS_LATER), TOMORROW);

            // 操作と確認：開始ちょうどは表示中
            assertThat(publication.isVisibleAt(TOMORROW)).isTrue();
        }

        @Test
        @DisplayName("公開終了日時の 1 秒前までは、表示中")
        void isVisibleJustBeforeEnd() {
            // 準備：明日から 10 日後までの期間の公開中
            var publication = published(PublicationPeriod.of(TOMORROW, TEN_DAYS_LATER), TOMORROW);

            // 操作と確認：終了の 1 秒前は表示中
            assertThat(publication.isVisibleAt(TEN_DAYS_LATER.minusSeconds(1))).isTrue();
        }

        @Test
        @DisplayName("公開終了日時ちょうどから、表示中ではない")
        void isInvisibleFromEnd() {
            // 準備：明日から 10 日後までの期間の公開中
            var publication = published(PublicationPeriod.of(TOMORROW, TEN_DAYS_LATER), TOMORROW);

            // 操作と確認：終了ちょうどは表示中ではない
            assertThat(publication.isVisibleAt(TEN_DAYS_LATER)).isFalse();
        }
    }

    @Nested
    @DisplayName("一度でも表示したか")
    class HasBeenShown {

        @Test
        @DisplayName("初回表示日時がなければ、表示したことはない")
        void hasNotBeenShownWithoutFirstShownAt() {
            // 準備：初回表示日時のない非公開
            var publication = unpublished(PublicationPeriod.UNLIMITED, null);

            // 操作と確認：表示したことはない
            assertThat(publication.hasBeenShown(NOW)).isFalse();
        }

        @Test
        @DisplayName("初回表示日時が未来なら、表示したことはない")
        void hasNotBeenShownWhenFirstShownAtIsFuture() {
            // 準備：初回表示日時が明日の公開中
            var publication = published(PublicationPeriod.of(TOMORROW, null), TOMORROW);

            // 操作と確認：今日はまだ表示したことはない
            assertThat(publication.hasBeenShown(NOW)).isFalse();
        }

        @Test
        @DisplayName("初回表示日時ちょうどには、表示したことになる")
        void hasBeenShownAtFirstShownAt() {
            // 準備：初回表示日時が明日の公開中
            var publication = published(PublicationPeriod.of(TOMORROW, null), TOMORROW);

            // 操作と確認：初回表示日時ちょうどには、表示したことになる
            assertThat(publication.hasBeenShown(TOMORROW)).isTrue();
        }

        @Test
        @DisplayName("初回表示日時が過去なら、表示したことがある")
        void hasBeenShownWhenFirstShownAtIsPast() {
            // 準備：初回表示日時が昨日の公開中
            var publication = published(PublicationPeriod.UNLIMITED, YESTERDAY);

            // 操作と確認：表示したことがある
            assertThat(publication.hasBeenShown(NOW)).isTrue();
        }

        @Test
        @DisplayName("公開中でも初回表示日時がなければ、表示したことはない")
        void hasNotBeenShownWhenPublishedWithoutFirstShownAt() {
            // 準備：公開の時点で公開終了を過ぎていた公開中
            var publication = publishedWithoutFirstShownAt();

            // 操作と確認：表示したことはない
            assertThat(publication.hasBeenShown(NOW)).isFalse();
        }
    }

    @Nested
    @DisplayName("不変条件")
    class Invariants {

        @ParameterizedTest
        @EnumSource(value = PublicationStatus.class, names = {"UNPUBLISHED", "DISCONTINUED"})
        @DisplayName("非公開と販売終了は、初回表示日時がなくても作れる")
        void acceptsNullForNotPublished(PublicationStatus status) {
            // 準備：初回表示日時がない
            Instant firstShownAt = null;

            // 操作と確認：作れる
            assertThatCode(() -> new Publication(status, PublicationPeriod.UNLIMITED, firstShownAt)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @EnumSource(value = PublicationStatus.class, names = {"UNPUBLISHED", "DISCONTINUED"})
        @DisplayName("非公開と販売終了は、過去の初回表示日時があっても作れる")
        void acceptsShownForNotPublished(PublicationStatus status) {
            // 準備：初回表示日時が昨日
            var firstShownAt = YESTERDAY;

            // 操作と確認：作れる
            assertThatCode(() -> new Publication(status, PublicationPeriod.UNLIMITED, firstShownAt)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("公開中で初回表示日時があれば作れる")
        void acceptsPublishedWithFirstShownAt() {
            // 準備：初回表示日時が昨日、公開終了日時がない
            var period = PublicationPeriod.UNLIMITED;

            // 操作と確認：作れる
            assertThatCode(() -> new Publication(PublicationStatus.PUBLISHED, period, YESTERDAY)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("公開中で初回表示日時がなく、公開終了日時があれば作れる（終了済みで表示されなかった）")
        void acceptsPublishedWithoutFirstShownAtWhenPeriodEnds() {
            // 準備：公開終了日時が明日
            var period = PublicationPeriod.of(null, TOMORROW);

            // 操作と確認：作れる
            assertThatCode(() -> new Publication(PublicationStatus.PUBLISHED, period, null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("公開中で初回表示日時も公開終了日時もなければ、値を含まないメッセージで拒む")
        void rejectsPublishedWithoutFirstShownAtAndEnd() {
            // 準備：公開開始日時だけがある期間
            var period = PublicationPeriod.of(YESTERDAY, null);

            // 操作と確認：値を含まないメッセージの IllegalArgumentException で拒まれる
            assertThatThrownBy(() -> new Publication(PublicationStatus.PUBLISHED, period, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .message().doesNotContain(YESTERDAY.toString());
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 非公開の値を作る。
     *
     * @param period       公開期間
     * @param firstShownAt 初回表示日時。なければ null
     * @return 非公開の値
     */
    private static Publication unpublished(PublicationPeriod period, Instant firstShownAt) {
        return new Publication(PublicationStatus.UNPUBLISHED, period, firstShownAt);
    }

    /**
     * 公開中の値を作る。
     *
     * @param period       公開期間
     * @param firstShownAt 初回表示日時
     * @return 公開中の値
     */
    private static Publication published(PublicationPeriod period, Instant firstShownAt) {
        return new Publication(PublicationStatus.PUBLISHED, period, firstShownAt);
    }

    /**
     * 販売終了の値を作る。
     *
     * @param period       公開期間
     * @param firstShownAt 初回表示日時。なければ null
     * @return 販売終了の値
     */
    private static Publication discontinued(PublicationPeriod period, Instant firstShownAt) {
        return new Publication(PublicationStatus.DISCONTINUED, period, firstShownAt);
    }

    /**
     * 公開中で初回表示日時がない値を作る（公開の時点で公開終了を過ぎていて、表示されなかった）。
     *
     * @return 公開終了日時が昨日の、公開中の値
     */
    private static Publication publishedWithoutFirstShownAt() {
        return published(PublicationPeriod.of(null, YESTERDAY), null);
    }
}
