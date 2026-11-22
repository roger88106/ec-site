package com.ecsite.module.catalog.domain.product;

import com.ecsite.common.model.id.ProductId;
import com.ecsite.common.model.id.TaxCategoryId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.module.catalog.domain.ProductErrorCode;
import com.ecsite.module.catalog.domain.product.vo.PriceExclTax;
import com.ecsite.module.catalog.domain.product.vo.ProductCode;
import com.ecsite.module.catalog.domain.product.vo.PublicationPeriod;
import com.ecsite.module.catalog.domain.product.vo.PublicationStatus;
import com.ecsite.module.catalog.domain.product.vo.VariantCode;
import com.ecsite.module.catalog.event.ProductDeleted;
import com.ecsite.module.catalog.event.ProductDiscontinued;
import com.ecsite.module.catalog.event.ProductPublished;
import com.ecsite.module.catalog.event.ProductUnpublished;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 商品（集約）の公開の単体テスト
 *
 * <p>公開の状態の切り替え、初回表示日時、公開期間、商品の削除を確認する。現在時刻は引数で固定する。
 *
 * <p>準備のデータと確認の期待値は、同じ定数から作る（準備した物から期待値を取り出さない）。
 */
class ProductPublicationTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 1 日
     */
    private static final Duration ONE_DAY = Duration.ofDays(1);

    /**
     * 明日の同じ時刻
     *
     * <p>公開を予約するときの公開開始日時に使う。
     */
    private static final Instant TOMORROW = NOW.plus(ONE_DAY);

    /**
     * 税区分 ID
     *
     * <p>税モジュールの ID なので、存在しなくてよい。
     */
    private static final TaxCategoryId TAX_CATEGORY_ID = new TaxCategoryId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    /**
     * 商品の生成
     */
    private final ProductFactory factory = new ProductFactory();

    // ============================== テスト ==============================

    @Nested
    @DisplayName("公開")
    class Publish {

        @Test
        @DisplayName("AC-29 公開にできる")
        void publishes() {
            // 準備：バリアントを持つ非公開の商品
            var product = productWithVariant();

            // 操作：公開する
            product.publish(NOW);

            // 確認：公開
            assertThat(product.status()).isEqualTo(PublicationStatus.PUBLISHED);
        }

        @Test
        @DisplayName("AC-29 公開すると、C 側に表示される")
        void becomesVisible() {
            // 準備：バリアントを持つ非公開の商品
            var product = productWithVariant();

            // 操作：公開する
            product.publish(NOW);

            // 確認：公開した時刻に表示される
            assertThat(product.isVisibleAt(NOW)).isTrue();
        }

        @Test
        @DisplayName("AC-54 公開すると、公開した時刻が初回表示日時になる")
        void recordsFirstShownAt() {
            // 準備：バリアントを持つ非公開の商品
            var product = productWithVariant();

            // 操作：公開する
            product.publish(NOW);

            // 確認：初回表示日時が公開した時刻
            assertThat(product.firstShownAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("公開すると、公開のイベントが登録される")
        void registersPublishedEvent() {
            // 準備：バリアントを持つ非公開の商品
            var product = productWithVariant();

            // 操作：公開する
            product.publish(NOW);

            // 確認：公開のイベントだけが登録されている
            assertThat(product.pullEvents()).singleElement().isInstanceOf(ProductPublished.class);
        }

        @Test
        @DisplayName("AC-31 バリアントのない商品は公開できない")
        void rejectsPublishingWithoutVariants() {
            // 準備：バリアントのない商品
            var product = productWithoutVariant();

            // 操作と確認：業務のエラー PRODUCT_HAS_NO_VARIANTS で拒否される
            assertBusinessError(() -> product.publish(NOW), ProductErrorCode.PRODUCT_HAS_NO_VARIANTS);
        }

        @Test
        @DisplayName("AC-31 公開を拒否された商品は、非公開のまま")
        void staysUnpublishedWhenRejected() {
            // 準備：バリアントのない商品
            var product = productWithoutVariant();

            // 操作と確認（前提）：公開が拒否される
            assertBusinessError(() -> product.publish(NOW), ProductErrorCode.PRODUCT_HAS_NO_VARIANTS);

            // 確認：非公開のまま
            assertThat(product.status()).isEqualTo(PublicationStatus.UNPUBLISHED);
        }

        @Test
        @DisplayName("公開中の商品を公開しても、初回表示日時は変わらない")
        void keepsFirstShownAtWhenAlreadyPublished() {
            // 準備：公開中の商品
            var product = productWithVariant();
            product.publish(NOW);

            // 操作：翌日にもう一度公開する
            product.publish(NOW.plus(ONE_DAY));

            // 確認：最初に公開した時刻のまま
            assertThat(product.firstShownAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("公開中の商品を公開しても、イベントは登録されない")
        void registersNoEventWhenAlreadyPublished() {
            // 準備：公開中の商品（公開のイベントは捨てる）
            var product = productWithVariant();
            product.publish(NOW);
            product.pullEvents();

            // 操作：翌日にもう一度公開する
            product.publish(NOW.plus(ONE_DAY));

            // 確認：イベントがない
            assertThat(product.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("非公開の商品を公開にすると、変わったので true を返す")
        void returnsTrueWhenChanged() {
            // 準備：バリアントを持つ非公開の商品
            var product = productWithVariant();

            // 操作：公開する
            var result = product.publish(NOW);

            // 確認：変わった
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("公開中の商品を公開にしても、変わらないので false を返す")
        void returnsFalseWhenAlreadyPublished() {
            // 準備：公開中の商品
            var product = publishedProduct();

            // 操作：翌日にもう一度公開する
            var result = product.publish(NOW.plus(ONE_DAY));

            // 確認：変わらない
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("バリアントのない販売終了の商品を公開にすると、PRODUCT_HAS_NO_VARIANTS でなく PRODUCT_DISCONTINUED で拒否される")
        void rejectsPublishingDiscontinuedWithoutVariants() {
            // 準備：バリアントのない、販売終了の商品
            var product = productWithoutVariant();
            product.discontinue(NOW);

            // 操作と確認：業務のエラー PRODUCT_DISCONTINUED で拒否される
            assertBusinessError(() -> product.publish(NOW), ProductErrorCode.PRODUCT_DISCONTINUED);
        }
    }

    @Nested
    @DisplayName("非公開")
    class Unpublish {

        @Test
        @DisplayName("AC-30 非公開にできる")
        void unpublishes() {
            // 準備：公開中の商品
            var product = publishedProduct();

            // 操作：翌日に非公開にする
            product.unpublish(NOW.plus(ONE_DAY));

            // 確認：非公開
            assertThat(product.status()).isEqualTo(PublicationStatus.UNPUBLISHED);
        }

        @Test
        @DisplayName("AC-30 非公開にすると、C 側に表示されない")
        void becomesInvisible() {
            // 準備：公開中の商品
            var product = publishedProduct();

            // 操作：翌日に非公開にする
            product.unpublish(NOW.plus(ONE_DAY));

            // 確認：非公開にした時刻に表示されない
            assertThat(product.isVisibleAt(NOW.plus(ONE_DAY))).isFalse();
        }

        @Test
        @DisplayName("非公開にすると、非公開のイベントが登録される")
        void registersUnpublishedEvent() {
            // 準備：公開中の商品（公開のイベントは捨てる）
            var product = publishedProduct();

            // 操作：翌日に非公開にする
            product.unpublish(NOW.plus(ONE_DAY));

            // 確認：非公開のイベントだけが登録されている
            assertThat(product.pullEvents()).singleElement().isInstanceOf(ProductUnpublished.class);
        }

        @Test
        @DisplayName("非公開の商品を非公開にしても、イベントは登録されない")
        void registersNoEventWhenAlreadyUnpublished() {
            // 準備：非公開の商品
            var product = productWithVariant();

            // 操作：非公開にする
            product.unpublish(NOW);

            // 確認：イベントがない
            assertThat(product.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("公開中の商品を非公開にすると、変わったので true を返す")
        void returnsTrueWhenChanged() {
            // 準備：公開中の商品
            var product = publishedProduct();

            // 操作：翌日に非公開にする
            var result = product.unpublish(NOW.plus(ONE_DAY));

            // 確認：変わった
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("非公開の商品を非公開にしても、変わらないので false を返す")
        void returnsFalseWhenAlreadyUnpublished() {
            // 準備：非公開の商品
            var product = productWithVariant();

            // 操作：非公開にする
            var result = product.unpublish(NOW);

            // 確認：変わらない
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("販売終了")
    class Discontinue {

        @Test
        @DisplayName("AC-32 公開中の商品を販売終了にできる")
        void discontinues() {
            // 準備：公開中の商品
            var product = publishedProduct();

            // 操作：販売終了にする
            product.discontinue(NOW);

            // 確認：販売終了
            assertThat(product.status()).isEqualTo(PublicationStatus.DISCONTINUED);
        }

        @Test
        @DisplayName("AC-32 販売終了の商品は、C 側に表示されない")
        void becomesInvisible() {
            // 準備：公開中の商品
            var product = publishedProduct();

            // 操作：販売終了にする
            product.discontinue(NOW);

            // 確認：表示されない
            assertThat(product.isVisibleAt(NOW)).isFalse();
        }

        @Test
        @DisplayName("販売終了にすると、販売終了のイベントが登録される")
        void registersDiscontinuedEvent() {
            // 準備：公開中の商品（公開のイベントは捨てる）
            var product = publishedProduct();

            // 操作：販売終了にする
            product.discontinue(NOW);

            // 確認：販売終了のイベントだけが登録されている
            assertThat(product.pullEvents()).singleElement().isInstanceOf(ProductDiscontinued.class);
        }

        @Test
        @DisplayName("AC-32 販売終了の商品は、公開に戻せない")
        void rejectsPublishing() {
            // 準備：販売終了にした商品
            var product = publishedProduct();
            product.discontinue(NOW);

            // 操作と確認：業務のエラー PRODUCT_DISCONTINUED で拒否される
            assertBusinessError(() -> product.publish(NOW), ProductErrorCode.PRODUCT_DISCONTINUED);
        }

        @Test
        @DisplayName("AC-32 販売終了の商品は、非公開に戻せない")
        void rejectsUnpublishing() {
            // 準備：販売終了にした商品
            var product = publishedProduct();
            product.discontinue(NOW);

            // 操作と確認：業務のエラー PRODUCT_DISCONTINUED で拒否される
            assertBusinessError(() -> product.unpublish(NOW), ProductErrorCode.PRODUCT_DISCONTINUED);
        }

        @Test
        @DisplayName("非公開の商品も販売終了にできる")
        void discontinuesUnpublished() {
            // 準備：バリアントのない非公開の商品
            var product = productWithoutVariant();

            // 操作：販売終了にする
            product.discontinue(NOW);

            // 確認：販売終了
            assertThat(product.status()).isEqualTo(PublicationStatus.DISCONTINUED);
        }

        @Test
        @DisplayName("販売終了の商品を販売終了にしても、イベントは登録されない")
        void registersNoEventWhenAlreadyDiscontinued() {
            // 準備：販売終了にした商品（販売終了のイベントは捨てる）
            var product = productWithVariant();
            product.discontinue(NOW);
            product.pullEvents();

            // 操作：もう一度販売終了にする
            product.discontinue(NOW);

            // 確認：イベントがない
            assertThat(product.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("公開中の商品を販売終了にすると、変わったので true を返す")
        void returnsTrueWhenPublishedChanged() {
            // 準備：公開中の商品
            var product = publishedProduct();

            // 操作：販売終了にする
            var result = product.discontinue(NOW);

            // 確認：変わった
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("非公開の商品を販売終了にすると、変わったので true を返す")
        void returnsTrueWhenUnpublishedChanged() {
            // 準備：非公開の商品
            var product = productWithVariant();

            // 操作：販売終了にする
            var result = product.discontinue(NOW);

            // 確認：変わった
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("販売終了の商品を販売終了にしても、変わらないので false を返す")
        void returnsFalseWhenAlreadyDiscontinued() {
            // 準備：販売終了の商品
            var product = productWithVariant();
            product.discontinue(NOW);

            // 操作：もう一度販売終了にする
            var result = product.discontinue(NOW);

            // 確認：変わらない
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("初回表示日時")
    class FirstShownAt {

        @Test
        @DisplayName("AC-54 過去になった初回表示日時は、再公開しても変わらない")
        void keepsFirstShownAtAfterRepublishing() {
            // 準備：公開して表示され、翌日に非公開にした商品
            var product = productWithVariant();
            product.publish(NOW);
            product.unpublish(NOW.plus(ONE_DAY));

            // 操作：2 日後に再公開する
            product.publish(NOW.plus(ONE_DAY.multipliedBy(2)));

            // 確認：最初に公開した時刻のまま
            assertThat(product.firstShownAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("AC-54 公開開始日時が未来なら、初回表示日時は公開開始日時になる")
        void usesFuturePublishStart() {
            // 準備：公開開始日時が明日の商品
            var product = productWithPeriod(PublicationPeriod.of(TOMORROW, null));

            // 操作：今日公開する
            product.publish(NOW);

            // 確認：初回表示日時が公開開始日時
            assertThat(product.firstShownAt()).isEqualTo(TOMORROW);
        }

        @Test
        @DisplayName("公開開始日時が未来なら、公開しても開始日時までは表示されない")
        void isInvisibleUntilPublishStart() {
            // 準備：公開開始日時が明日の商品
            var product = productWithPeriod(PublicationPeriod.of(TOMORROW, null));

            // 操作：今日公開する
            product.publish(NOW);

            // 確認：今日は表示されない
            assertThat(product.isVisibleAt(NOW)).isFalse();
        }

        @Test
        @DisplayName("公開開始日時が未来の商品を公開すると、公開開始日時から表示される")
        void isVisibleFromPublishStart() {
            // 準備：公開開始日時が明日の商品
            var product = productWithPeriod(PublicationPeriod.of(TOMORROW, null));

            // 操作：今日公開する
            product.publish(NOW);

            // 確認：公開開始日時には表示される
            assertThat(product.isVisibleAt(TOMORROW)).isTrue();
        }

        @Test
        @DisplayName("表示前に非公開にすると、初回表示日時は空に戻る")
        void clearsFutureFirstShownAtWhenUnpublished() {
            // 準備：公開開始日時が明日で、今日公開した商品
            var product = productWithPeriod(PublicationPeriod.of(TOMORROW, null));
            product.publish(NOW);

            // 操作：今日のうちに非公開にする
            product.unpublish(NOW);

            // 確認：初回表示日時が空
            assertThat(product.firstShownAt()).isNull();
        }

        @Test
        @DisplayName("表示前に販売終了にすると、初回表示日時は空に戻る")
        void clearsFutureFirstShownAtWhenDiscontinued() {
            // 準備：公開開始日時が明日で、今日公開した商品
            var product = productWithPeriod(PublicationPeriod.of(TOMORROW, null));
            product.publish(NOW);

            // 操作：今日のうちに販売終了にする
            product.discontinue(NOW);

            // 確認：初回表示日時が空
            assertThat(product.firstShownAt()).isNull();
        }

        @Test
        @DisplayName("公開終了日時を過ぎていれば表示されないので、公開しても初回表示日時は空のまま")
        void leavesFirstShownAtEmptyAfterPublishEnd() {
            // 準備：公開終了日時が今の商品
            var product = productWithPeriod(PublicationPeriod.of(null, NOW));

            // 操作：公開する
            product.publish(NOW);

            // 確認：初回表示日時が空
            assertThat(product.firstShownAt()).isNull();
        }
    }

    @Nested
    @DisplayName("公開期間")
    class ChangePublicationPeriod {

        @Test
        @DisplayName("AC-34 公開開始日時と公開終了日時を設定できる")
        void setsPeriod() {
            // 準備：非公開の商品と、明日から 10 日後までの期間
            var product = productWithVariant();
            var period = PublicationPeriod.of(TOMORROW, NOW.plus(ONE_DAY.multipliedBy(10)));

            // 操作：期間を設定する
            product.changePublicationPeriod(period, NOW);

            // 確認：設定した期間になる
            assertThat(product.publicationPeriod()).isEqualTo(period);
        }

        @Test
        @DisplayName("AC-34 公開開始日時だけを設定できる")
        void setsStartOnly() {
            // 準備：非公開の商品
            var product = productWithVariant();

            // 操作：開始日時だけの期間を設定する
            product.changePublicationPeriod(PublicationPeriod.of(TOMORROW, null), NOW);

            // 確認：開始日時だけを持つ
            assertThat(product.publicationPeriod()).isEqualTo(new PublicationPeriod(TOMORROW, null));
        }

        @Test
        @DisplayName("AC-34 公開終了日時だけを設定できる")
        void setsEndOnly() {
            // 準備：非公開の商品
            var product = productWithVariant();

            // 操作：終了日時だけの期間を設定する
            product.changePublicationPeriod(PublicationPeriod.of(null, TOMORROW), NOW);

            // 確認：終了日時だけを持つ
            assertThat(product.publicationPeriod()).isEqualTo(new PublicationPeriod(null, TOMORROW));
        }

        @Test
        @DisplayName("AC-34 公開開始日時と公開終了日時を、どちらも空に戻せる")
        void clearsPeriod() {
            // 準備：期間を設定した商品
            var product = productWithPeriod(PublicationPeriod.of(TOMORROW, NOW.plus(ONE_DAY.multipliedBy(10))));

            // 操作：期間を空にする
            product.changePublicationPeriod(PublicationPeriod.UNLIMITED, NOW);

            // 確認：期間がない
            assertThat(product.publicationPeriod()).isEqualTo(PublicationPeriod.UNLIMITED);
        }

        @Test
        @DisplayName("AC-34 公開中の商品にも公開期間を設定できる")
        void setsPeriodOfPublishedProduct() {
            // 準備：公開中の商品
            var product = publishedProduct();

            // 操作：終了日時を設定する
            product.changePublicationPeriod(PublicationPeriod.of(null, TOMORROW), NOW);

            // 確認：設定した期間になる
            assertThat(product.publicationPeriod()).isEqualTo(new PublicationPeriod(null, TOMORROW));
        }

        @Test
        @DisplayName("AC-34 販売終了の商品にも公開期間を設定できる")
        void setsPeriodOfDiscontinuedProduct() {
            // 準備：販売終了にした商品
            var product = productWithVariant();
            product.discontinue(NOW);

            // 操作：開始日時を設定する
            product.changePublicationPeriod(PublicationPeriod.of(TOMORROW, null), NOW);

            // 確認：設定した期間になる
            assertThat(product.publicationPeriod()).isEqualTo(new PublicationPeriod(TOMORROW, null));
        }

        @Test
        @DisplayName("AC-36 終了日時が開始日時と同じなら拒否される")
        void rejectsEndEqualToStart() {
            // 操作と確認：業務のエラー INVALID_PUBLICATION_PERIOD で拒否される
            assertBusinessError(() -> PublicationPeriod.of(NOW, NOW), ProductErrorCode.INVALID_PUBLICATION_PERIOD);
        }

        @Test
        @DisplayName("AC-36 終了日時が開始日時より前なら拒否される")
        void rejectsEndBeforeStart() {
            // 操作と確認：業務のエラー INVALID_PUBLICATION_PERIOD で拒否される
            assertBusinessError(() -> PublicationPeriod.of(NOW, NOW.minus(ONE_DAY)), ProductErrorCode.INVALID_PUBLICATION_PERIOD);
        }

        @Test
        @DisplayName("AC-36 終了日時が開始日時より後なら受け付ける")
        void acceptsEndAfterStart() {
            // 操作と確認：1 秒後の終了日時を受け付ける
            assertThatCode(() -> PublicationPeriod.of(NOW, NOW.plusSeconds(1))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AC-35 公開中でも、開始日時より前は C 側に表示されない")
        void isInvisibleBeforeStart() {
            // 準備：明日から 2 日後までの期間で公開した商品
            var product = publishedWithin(TOMORROW, NOW.plus(ONE_DAY.multipliedBy(2)));

            // 操作と確認：開始日時の 1 秒前は表示されない
            assertThat(product.isVisibleAt(TOMORROW.minusSeconds(1))).isFalse();
        }

        @Test
        @DisplayName("AC-35 開始日時ちょうどから C 側に表示される")
        void isVisibleFromStart() {
            // 準備：明日から 2 日後までの期間で公開した商品
            var product = publishedWithin(TOMORROW, NOW.plus(ONE_DAY.multipliedBy(2)));

            // 操作と確認：開始日時に表示される
            assertThat(product.isVisibleAt(TOMORROW)).isTrue();
        }

        @Test
        @DisplayName("AC-35 終了日時の直前まで C 側に表示される")
        void isVisibleUntilJustBeforeEnd() {
            // 準備：明日から 2 日後までの期間で公開した商品
            var end = NOW.plus(ONE_DAY.multipliedBy(2));
            var product = publishedWithin(TOMORROW, end);

            // 操作と確認：終了日時の 1 秒前は表示される
            assertThat(product.isVisibleAt(end.minusSeconds(1))).isTrue();
        }

        @Test
        @DisplayName("AC-35 公開中でも、終了日時ちょうどから C 側に表示されない")
        void isInvisibleFromEnd() {
            // 準備：明日から 2 日後までの期間で公開した商品
            var end = NOW.plus(ONE_DAY.multipliedBy(2));
            var product = publishedWithin(TOMORROW, end);

            // 操作と確認：終了日時に表示されない
            assertThat(product.isVisibleAt(end)).isFalse();
        }

        @Test
        @DisplayName("公開中に開始日時を別の未来に変えると、初回表示日時は新しい開始日時になる")
        void recalculatesScheduledFirstShownAt() {
            // 準備：公開開始日時が明日で、今日公開した商品
            var product = productWithPeriod(PublicationPeriod.of(TOMORROW, null));
            product.publish(NOW);
            var later = NOW.plus(ONE_DAY.multipliedBy(3));

            // 確認（前提）：初回表示日時が明日
            assertThat(product.firstShownAt()).isEqualTo(TOMORROW);

            // 操作：開始日時を 3 日後に変える
            product.changePublicationPeriod(PublicationPeriod.of(later, null), NOW);

            // 確認：初回表示日時が 3 日後
            assertThat(product.firstShownAt()).isEqualTo(later);
        }

        @Test
        @DisplayName("公開中に開始日時を空にすると、初回表示日時は変えた時刻になる")
        void usesChangedAtWhenStartIsCleared() {
            // 準備：公開開始日時が明日で、今日公開した商品
            var product = productWithPeriod(PublicationPeriod.of(TOMORROW, null));
            product.publish(NOW);
            var changedAt = NOW.plusSeconds(60);

            // 操作：1 分後に期間を空にする
            product.changePublicationPeriod(PublicationPeriod.UNLIMITED, changedAt);

            // 確認：変えた時刻から表示される
            assertThat(product.firstShownAt()).isEqualTo(changedAt);
        }

        @Test
        @DisplayName("AC-54 表示されて確定した初回表示日時は、公開期間を変えても変わらない")
        void keepsConfirmedFirstShownAt() {
            // 準備：今日公開して表示された商品
            var product = publishedProduct();

            // 操作：1 分後に開始日時を明日にする
            product.changePublicationPeriod(PublicationPeriod.of(TOMORROW, null), NOW.plusSeconds(60));

            // 確認：最初に公開した時刻のまま
            assertThat(product.firstShownAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("公開中に終了日時を過ぎた期間にすると、表示されないので初回表示日時は空になる")
        void clearsFirstShownAtWhenPeriodHasEnded() {
            // 準備：公開開始日時が明日で、今日公開した商品
            var product = productWithPeriod(PublicationPeriod.of(TOMORROW, null));
            product.publish(NOW);

            // 操作：終了日時を今にする
            product.changePublicationPeriod(PublicationPeriod.of(null, NOW), NOW);

            // 確認：初回表示日時が空
            assertThat(product.firstShownAt()).isNull();
        }

        @Test
        @DisplayName("公開中でない商品の期間を変えても、初回表示日時は空のまま")
        void leavesFirstShownAtOfUnpublished() {
            // 準備：非公開の商品
            var product = productWithVariant();

            // 操作：開始日時を明日にする
            product.changePublicationPeriod(PublicationPeriod.of(TOMORROW, null), NOW);

            // 確認：初回表示日時が空
            assertThat(product.firstShownAt()).isNull();
        }

        @Test
        @DisplayName("公開期間を変えても、イベントは登録されない")
        void registersNoEvent() {
            // 準備：非公開の商品
            var product = productWithVariant();

            // 操作：開始日時を明日にする
            product.changePublicationPeriod(PublicationPeriod.of(TOMORROW, null), NOW);

            // 確認：イベントがない
            assertThat(product.pullEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("商品の削除")
    class Delete {

        @Test
        @DisplayName("AC-33 C 側に一度も表示されていない商品を削除すると、削除のイベントが登録される")
        void deletesNeverShownProduct() {
            // 準備：非公開の商品
            var product = productWithVariant();

            // 操作：削除する
            product.delete(NOW);

            // 確認：最後のイベントが、この商品の削除のイベント
            assertThat(product.pullEvents()).last()
                    .isInstanceOfSatisfying(ProductDeleted.class, event -> assertThat(event.productId()).isEqualTo(product.id()));
        }

        @Test
        @DisplayName("AC-33 表示されたことのある商品は、販売終了にしても削除できない")
        void rejectsDeletingDiscontinuedShownProduct() {
            // 準備：今日公開して表示され、翌日に販売終了にした商品
            var product = productWithVariant();
            product.publish(NOW);
            product.discontinue(NOW.plus(ONE_DAY));

            // 操作と確認：業務のエラー PRODUCT_ALREADY_SHOWN で拒否される
            assertBusinessError(() -> product.delete(NOW.plus(ONE_DAY)), ProductErrorCode.PRODUCT_ALREADY_SHOWN);
        }

        @Test
        @DisplayName("AC-33 表示されたことのある商品は、非公開にしても削除できない")
        void rejectsDeletingUnpublishedShownProduct() {
            // 準備：今日公開して表示され、翌日に非公開にした商品
            var product = productWithVariant();
            product.publish(NOW);
            product.unpublish(NOW.plus(ONE_DAY));

            // 操作と確認：業務のエラー PRODUCT_ALREADY_SHOWN で拒否される
            assertBusinessError(() -> product.delete(NOW.plus(ONE_DAY)), ProductErrorCode.PRODUCT_ALREADY_SHOWN);
        }

        @Test
        @DisplayName("AC-33 初回表示日時が未来（表示予定）の商品は削除できる")
        void deletesScheduledProduct() {
            // 準備：公開開始日時が明日で、今日公開した商品
            var product = productWithPeriod(PublicationPeriod.of(TOMORROW, null));
            product.publish(NOW);

            // 操作と確認：今日なら削除できる
            assertThatCode(() -> product.delete(NOW)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AC-33 公開を予約し、開始日時の前に取り消した（非公開にした）商品は削除できる")
        void deletesCancelledReservation() {
            // 準備：公開開始日時が明日で、今日公開し、1 分後に非公開にした商品
            var product = productWithPeriod(PublicationPeriod.of(TOMORROW, null));
            product.publish(NOW);
            product.unpublish(NOW.plusSeconds(60));

            // 操作と確認：削除できる
            assertThatCode(() -> product.delete(NOW.plusSeconds(60))).doesNotThrowAnyException();
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 軸がなく、バリアントのない非公開の商品を作る。
     *
     * @return 非公開の商品
     */
    private Product productWithoutVariant() {
        return factory.create(new ProductId(UUID.randomUUID()), ProductCode.of("ABC-001"), "T シャツ", "", TAX_CATEGORY_ID, PriceExclTax.of(1000),
                Set.of(), List.of());
    }

    /**
     * 軸がなく、バリアントを 1 つ持つ非公開の商品を作る。
     *
     * <p>バリアント登録のイベントは捨てておく。
     *
     * @return 非公開の商品
     */
    private Product productWithVariant() {
        var product = productWithoutVariant();
        product.addVariant(new VariantId(UUID.randomUUID()), VariantCode.of("ABC-001"), List.of(), NOW);
        product.pullEvents();
        return product;
    }

    /**
     * 公開期間を持つ、バリアントが 1 つの非公開の商品を作る。
     *
     * @param period 公開期間
     * @return 非公開の商品
     */
    private Product productWithPeriod(PublicationPeriod period) {
        var product = productWithVariant();
        product.changePublicationPeriod(period, NOW);
        return product;
    }

    /**
     * 今日公開して表示された、バリアントが 1 つの商品を作る。
     *
     * <p>公開のイベントは捨てておく。
     *
     * @return 公開中の商品（初回表示日時は {@link #NOW}）
     */
    private Product publishedProduct() {
        var product = productWithVariant();
        product.publish(NOW);
        product.pullEvents();
        return product;
    }

    /**
     * 公開期間を設定してから、今日公開した商品を作る。
     *
     * @param start 公開開始日時
     * @param end   公開終了日時
     * @return 公開中の商品
     */
    private Product publishedWithin(Instant start, Instant end) {
        var product = productWithVariant();
        product.changePublicationPeriod(PublicationPeriod.of(start, end), NOW);
        product.publish(NOW);
        return product;
    }
}
