package com.ecsite.module.region.application;

import com.ecsite.module.region.application.model.MunicipalityView;
import com.ecsite.module.region.application.model.PrefectureMunicipalitiesView;
import com.ecsite.module.region.application.port.RegionRepository;
import com.ecsite.module.region.domain.vo.Municipality;
import com.ecsite.module.region.domain.vo.PostalAddress;
import com.ecsite.module.region.domain.vo.Prefecture;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 地域の参照のサービスの単体テスト
 *
 * <p>リポジトリは、呼び出しの回数を数える偽物にする。時間は、進められる Ticker で動かす。
 */
class RegionQueryServiceTest {

    // ============================== 定数 ==============================

    /**
     * キャッシュの期限
     */
    private static final Duration EXPIRATION = Duration.ofHours(24);

    /**
     * 東京都
     */
    private static final Prefecture TOKYO = new Prefecture("13", "東京都");

    /**
     * 大阪府
     */
    private static final Prefecture OSAKA = new Prefecture("27", "大阪府");

    /**
     * 千代田区
     */
    private static final Municipality CHIYODA = new Municipality("13101", "13", "千代田区");

    /**
     * 中央区
     */
    private static final Municipality CHUO = new Municipality("13102", "13", "中央区");

    /**
     * 大阪市北区
     */
    private static final Municipality OSAKA_KITA = new Municipality("27127", "27", "大阪市北区");

    /**
     * 千代田区の郵便番号の住所
     */
    private static final PostalAddress CHIYODA_ADDRESS = new PostalAddress("1000001", TOKYO, CHIYODA, "千代田");

    // ============================== 状態 ==============================

    /**
     * 偽のリポジトリ
     */
    private FakeRegionRepository repository;

    /**
     * 進められる時計（ナノ秒）
     */
    private AtomicLong nanos;

    /**
     * テスト対象
     */
    private RegionQueryService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRegionRepository();
        nanos = new AtomicLong();
        Ticker ticker = nanos::get;
        var cache = new RegionMasterCache(repository, new RegionCacheProperties(EXPIRATION), ticker);
        service = new RegionQueryService(cache, repository);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("DB への問い合わせ")
    class Queries {

        @Test
        @DisplayName("DC-557-5 4 つの照会を繰り返しても、DB に問い合わせるのは最初の 1 回（都道府県の一覧と全ての市区町村）だけ")
        void loadsOnceForRepeatedQueries() {
            // 操作：4 つの照会を 3 回ずつ繰り返す
            for (int i = 0; i < 3; i++) {
                service.prefectures();
                service.findPrefecture("13");
                service.municipalitiesOf("13");
                service.findMunicipality("13101");
            }

            // 確認：都道府県の一覧と全ての市区町村を 1 回ずつ読み、他に問い合わせない
            assertThat(repository.prefecturesCalls).isEqualTo(1);
            assertThat(repository.allMunicipalitiesCalls).isEqualTo(1);
            assertThat(repository.otherCalls).isZero();
        }

        @Test
        @DisplayName("DC-557-7 郵便番号の検索は、呼ぶたびに DB に問い合わせる")
        void postalSearchQueriesEveryTime() {
            // 操作：同じ郵便番号を 3 回検索する
            var first = service.findPostalAddresses("1000001");
            service.findPostalAddresses("1000001");
            service.findPostalAddresses("1000001");

            // 確認：3 回とも問い合わせ、住所を返し、マスターの読み込みはしない
            assertThat(repository.postalCalls).isEqualTo(3);
            assertThat(first).containsExactly(CHIYODA_ADDRESS);
            assertThat(repository.prefecturesCalls).isZero();
            assertThat(repository.allMunicipalitiesCalls).isZero();
        }
    }

    @Nested
    @DisplayName("返す内容")
    class Contents {

        @Test
        @DisplayName("都道府県の一覧は、コードの順で返す")
        void prefecturesInCodeOrder() {
            assertThat(service.prefectures()).containsExactly(TOKYO, OSAKA);
        }

        @Test
        @DisplayName("都道府県をコードで引ける。ないコードは空")
        void findsPrefectureByCode() {
            assertThat(service.findPrefecture("27")).contains(OSAKA);
            assertThat(service.findPrefecture("99")).isEmpty();
        }

        @Test
        @DisplayName("都道府県の市区町村は、その都道府県のものだけをコードの順で返す")
        void municipalitiesOfPrefecture() {
            // 操作：2 つの都道府県の市区町村を引く
            var tokyo = service.municipalitiesOf("13");
            var osaka = service.municipalitiesOf("27");

            // 確認：都道府県と、その市区町村だけ
            assertThat(tokyo).contains(new PrefectureMunicipalitiesView(TOKYO, List.of(CHIYODA, CHUO)));
            assertThat(osaka).contains(new PrefectureMunicipalitiesView(OSAKA, List.of(OSAKA_KITA)));
        }

        @Test
        @DisplayName("ない都道府県の市区町村は空を返す")
        void municipalitiesOfMissingPrefecture() {
            assertThat(service.municipalitiesOf("99")).isEmpty();
        }

        @Test
        @DisplayName("市区町村をコードで引くと、属する都道府県が付く。ないコードは空")
        void findsMunicipalityWithPrefecture() {
            assertThat(service.findMunicipality("27127")).contains(new MunicipalityView(OSAKA, OSAKA_KITA));
            assertThat(service.findMunicipality("99999")).isEmpty();
        }
    }

    @Nested
    @DisplayName("期限")
    class Expiration {

        @Test
        @DisplayName("DC-557-6 期限の 1 ナノ秒前までは読み直さない")
        void doesNotReloadBeforeExpiration() {
            // 準備：読み込み済みにする
            service.prefectures();

            // 操作：期限の 1 ナノ秒前まで進めて照会する
            nanos.addAndGet(EXPIRATION.toNanos() - 1);
            service.prefectures();

            // 確認：読み直さない
            assertThat(repository.prefecturesCalls).isEqualTo(1);
            assertThat(repository.allMunicipalitiesCalls).isEqualTo(1);
        }

        @Test
        @DisplayName("DC-557-6 期限になった後の照会で読み直し、新しい内容を返す")
        void reloadsAfterExpiration() {
            // 準備：読み込み済みにして、DB の内容を変える
            service.prefectures();
            repository.prefectures = List.of(TOKYO);

            // 操作：期限まで進めて照会する
            nanos.addAndGet(EXPIRATION.toNanos());
            var prefectures = service.prefectures();

            // 確認：2 回目の読み込みをして、新しい内容を返す
            assertThat(repository.prefecturesCalls).isEqualTo(2);
            assertThat(repository.allMunicipalitiesCalls).isEqualTo(2);
            assertThat(prefectures).containsExactly(TOKYO);
        }
    }

    // ============================== 偽物 ==============================

    /**
     * 呼び出しの回数を数える、メモリ上のリポジトリ
     */
    private static final class FakeRegionRepository implements RegionRepository {

        /**
         * 都道府県の一覧を読んだ回数
         */
        int prefecturesCalls;

        /**
         * 全ての市区町村を読んだ回数
         */
        int allMunicipalitiesCalls;

        /**
         * マスターの読み込みに使わない問い合わせの回数
         */
        int otherCalls;

        /**
         * 郵便番号の検索の回数
         */
        int postalCalls;

        /**
         * 持っている都道府県（コードの順）
         */
        List<Prefecture> prefectures = List.of(TOKYO, OSAKA);

        @Override
        public List<Prefecture> findPrefectures() {
            prefecturesCalls++;
            return prefectures;
        }

        @Override
        public List<Municipality> findAllMunicipalities() {
            allMunicipalitiesCalls++;
            return List.of(CHIYODA, CHUO, OSAKA_KITA);
        }

        @Override
        public Optional<Prefecture> findPrefecture(String code) {
            otherCalls++;
            return Optional.empty();
        }

        @Override
        public List<Municipality> findMunicipalities(String prefectureCode) {
            otherCalls++;
            return List.of();
        }

        @Override
        public Optional<MunicipalityView> findMunicipality(String code) {
            otherCalls++;
            return Optional.empty();
        }

        @Override
        public List<PostalAddress> findPostalAddresses(String postalCode) {
            postalCalls++;
            return List.of(CHIYODA_ADDRESS);
        }
    }
}
