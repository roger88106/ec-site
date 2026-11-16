package com.ecsite.module.tax.presentation;

import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.http.TestJson;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 税区分 API の受け入れテスト
 *
 * <p>現在時刻は {@link MutableClock} で 2026-10-08 00:00（日本時間）に固定する。
 * 適用開始の日時は日本時間のオフセット付きで送る。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class TaxCategoryApiTest {

    // ============================== 定数 ==============================

    /**
     * API のパス
     */
    private static final String BASE = "/api/admin/tax-categories";

    /**
     * 税区分の名称
     */
    private static final String NAME = "標準";

    /**
     * 名称の上限（文字）
     */
    private static final int NAME_MAX_LENGTH = 50;

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("税区分の登録")
    class Create {

        @Test
        @DisplayName("AC-1 名称で登録できる。Location の URL で、その税区分を取得できる")
        void createsAndGetsByLocation() throws Exception {
            // 操作と確認：登録すると 201 と、作った税区分の URL と ID が返る
            // CPD-OFF 登録の API の応答（201、Location、ID）の確認そのものを読めるように、各 API のテストに書く。補助に移すと確認の内容が読めなくなる
            var result = createCategory(NAME)
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(".*" + BASE + "/[0-9a-f-]{36}")))
                    .andExpect(jsonPath("$.id").isString())
                    .andReturn();
            var id = TestJson.createdId(result.getResponse().getContentAsString());

            // 操作と確認：Location の URL で、登録した名称の、税率のない税区分を取得できる
            mockMvc.perform(get(result.getResponse().getHeader(HttpHeaders.LOCATION)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()))
                    // CPD-ON
                    .andExpect(jsonPath("$.name").value(NAME))
                    .andExpect(jsonPath("$.rates").isEmpty())
                    .andExpect(jsonPath("$.currentRatePercent").value(nullValue()))
                    .andExpect(jsonPath("$.version").value(0));
        }

        @Test
        @DisplayName("AC-1 名称が重複する税区分は 409")
        void rejectsDuplicatedName() throws Exception {
            // 準備：同じ名称の税区分を登録済み
            createCategory(NAME).andExpect(status().isCreated());

            // 操作と確認：409 と、エラーコード TAX_CATEGORY_NAME_ALREADY_EXISTS が返る
            createCategory(NAME)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("TAX_CATEGORY_NAME_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("名称が空なら 400")
        void rejectsEmptyName() throws Exception {
            // 操作と確認：入力の形の誤りとして、エラーコード INVALID_REQUEST が返る
            createCategory("")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("名称が空白だけなら 400")
        void rejectsBlankName() throws Exception {
            // 操作と確認：エラーコード INVALID_TAX_CATEGORY_NAME が返る
            createCategory(" ")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_TAX_CATEGORY_NAME"));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {1, NAME_MAX_LENGTH})
        @DisplayName("名称が範囲の端の文字数なら、その値で登録できる")
        void acceptsNameAtBoundary(int length) throws Exception {
            // 操作：登録する
            var id = createdCategoryId("あ".repeat(length));

            // 確認：送った名称で保存される
            mockMvc.perform(get(BASE + "/" + id)).andExpect(jsonPath("$.name").value("あ".repeat(length)));
        }

        @Test
        @DisplayName("名称が上限を 1 文字超えれば 400")
        void rejectsNameOverMaximum() throws Exception {
            // 操作と確認：入力の形の誤りとして、エラーコード INVALID_REQUEST が返る
            createCategory("あ".repeat(NAME_MAX_LENGTH + 1))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }
    }

    @Nested
    @DisplayName("取得")
    class Get {

        @Test
        @DisplayName("存在しない ID は 404")
        void returnsNotFound() throws Exception {
            // 操作と確認：404 と、エラーコード NOT_FOUND が返る
            mockMvc.perform(get(BASE + "/00000000-0000-0000-0000-000000000000"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("一覧で、各税区分の現時点の率が返る")
        void listsWithCurrentRate() throws Exception {
            // 準備：10% の「標準」と、8% の「軽減」
            var standard = createdCategoryId(NAME);
            replaceRates(standard, 0, """
                    [{"ratePercent": 10}]""").andExpect(status().isNoContent());
            var reduced = createdCategoryId("軽減");
            replaceRates(reduced, 0, """
                    [{"ratePercent": 8}]""").andExpect(status().isNoContent());

            // 操作と確認：一覧で、登録した 2 つの税区分に、それぞれの率が返る
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[?(@.id == '%s')].currentRatePercent".formatted(standard)).value(hasItem(10)))
                    .andExpect(jsonPath("$.items[?(@.id == '%s')].currentRatePercent".formatted(reduced)).value(hasItem(8)));
        }
    }

    @Nested
    @DisplayName("期間限定の税率への切り替え")
    class LimitedTimeRate {

        @Test
        @DisplayName("AC-4 置き換えた一覧は、適用期間の古い順に、日時を UTC で返る")
        void returnsReplacedListInOrder() throws Exception {
            // 準備：10%（開始・終了とも空白）を持つ税区分
            var id = createdCategoryId(NAME);
            replaceRates(id, 0, """
                    [{"ratePercent": 10}]""").andExpect(status().isNoContent());
            var baseRateId = rateIds(id)[0];

            // 操作：期間限定の 8% に切り替え、10% に戻す一覧に置き換える
            switchToLimitedTimeRate(id, baseRateId);

            // 確認：置き換えた一覧が、古い順に、基本の税率を残して返る
            assertLimitedTimeRateList(id, baseRateId);
        }

        @Test
        @DisplayName("AC-4 期間限定の税率に切り替え、元に戻せる。各時点の現時点の率が正しい")
        void switchesToLimitedTimeRateAndBack() throws Exception {
            // 準備：10%（開始・終了とも空白）を持つ税区分
            var id = createdCategoryId(NAME);
            replaceRates(id, 0, """
                    [{"ratePercent": 10}]""").andExpect(status().isNoContent());

            // 操作：期間限定の 8% に切り替え、10% に戻す一覧に置き換える
            switchToLimitedTimeRate(id, rateIds(id)[0]);

            // 確認：切り替えの前後の各時点の、現時点の率
            assertCurrentRatesAroundLimitedTime(id);
        }
    }

    @Nested
    @DisplayName("一覧の形の検証")
    class ListShape {

        @Test
        @DisplayName("空の一覧は 400")
        void rejectsEmptyList() throws Exception {
            // 準備：税率のない税区分
            var id = createdCategoryId(NAME);

            // 操作と確認：入力の形の誤りとして、エラーコード INVALID_REQUEST が返る
            replaceRates(id, 0, "[]")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-2 開始が終了より後の税率は 400")
        void rejectsInvalidPeriod() throws Exception {
            // 準備：税率のない税区分
            var id = createdCategoryId(NAME);

            // 操作と確認：エラーコード INVALID_EFFECTIVE_PERIOD が返る
            replaceRates(id, 0, """
                    [{"ratePercent": 10, "effectiveFrom": "2028-04-01T00:00:00+09:00", "effectiveTo": "2027-04-01T00:00:00+09:00"}]""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_EFFECTIVE_PERIOD"));
        }

        @Test
        @DisplayName("AC-3 期間が重複する一覧は 400")
        void rejectsOverlap() throws Exception {
            // 準備：税率のない税区分
            var id = createdCategoryId(NAME);

            // 操作と確認：エラーコード TAX_RATE_PERIODS_OVERLAP が返る
            replaceRates(id, 0, overlappingRates())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TAX_RATE_PERIODS_OVERLAP"));
        }

        @Test
        @DisplayName("AC-3 拒否された一覧は、全体が反映されない")
        void rejectedListChangesNothing() throws Exception {
            // 準備：税率のない税区分
            var id = createdCategoryId(NAME);

            // 操作：期間が重複する一覧で置き換え、拒否される
            replaceRates(id, 0, overlappingRates()).andExpect(status().isBadRequest());

            // 確認：税率は空のままで、バージョンも 0 のまま
            mockMvc.perform(get(BASE + "/" + id))
                    .andExpect(jsonPath("$.rates").isEmpty())
                    .andExpect(jsonPath("$.version").value(0));
        }

        @Test
        @DisplayName("AC-3 期間の間に途切れがある一覧は 400")
        void rejectsGap() throws Exception {
            // 準備：税率のない税区分
            var id = createdCategoryId(NAME);

            // 操作と確認：1 日の途切れがあれば、エラーコード TAX_RATE_PERIODS_GAP が返る
            replaceRates(id, 0, """
                    [
                      {"ratePercent": 10, "effectiveTo": "2027-04-01T00:00:00+09:00"},
                      {"ratePercent": 8, "effectiveFrom": "2027-04-02T00:00:00+09:00"}
                    ]""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TAX_RATE_PERIODS_GAP"));
        }

        @Test
        @DisplayName("AC-3 最後の税率の終了が空白でない一覧は 400")
        void rejectsBoundedLast() throws Exception {
            // 準備：税率のない税区分
            var id = createdCategoryId(NAME);

            // 操作と確認：エラーコード TAX_RATE_LAST_PERIOD_NOT_OPEN_ENDED が返る
            replaceRates(id, 0, """
                    [{"ratePercent": 10, "effectiveTo": "2027-04-01T00:00:00+09:00"}]""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TAX_RATE_LAST_PERIOD_NOT_OPEN_ENDED"));
        }
    }

    @Nested
    @DisplayName("過去の根拠を変えない")
    class PastUnchangeable {

        @Test
        @DisplayName("AC-5 現在より前の日時を設定する一覧は 400")
        void rejectsPastDateTime() throws Exception {
            // 準備：税率のない税区分
            var id = createdCategoryId(NAME);

            // 操作と確認：現在の 1 分前を開始にすると、エラーコード TAX_RATE_PAST_DATETIME が返る
            replaceRates(id, 0, """
                    [{"ratePercent": 10, "effectiveFrom": "2026-10-07T23:59:00+09:00"}]""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TAX_RATE_PAST_DATETIME"));
        }

        @Test
        @DisplayName("AC-5 開始済みの税率の率を変える一覧は 400")
        void rejectsChangingStartedRate() throws Exception {
            // 準備：開始済みの 10% を持つ税区分
            var id = createdCategoryId(NAME);
            replaceRates(id, 0, """
                    [{"ratePercent": 10}]""").andExpect(status().isNoContent());

            // 操作と確認：率を 8% に変えると、エラーコード TAX_RATE_STARTED_UNCHANGEABLE が返る
            replaceRates(id, 1, """
                    [{"id": "%s", "ratePercent": 8}]""".formatted(rateIds(id)[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TAX_RATE_STARTED_UNCHANGEABLE"));
        }

        @Test
        @DisplayName("AC-5 開始済みの税率を削除する一覧は 400")
        void rejectsDeletingStartedRate() throws Exception {
            // 準備：開始済みの 10% を持つ税区分
            var id = createdCategoryId(NAME);
            replaceRates(id, 0, """
                    [{"ratePercent": 10}]""").andExpect(status().isNoContent());

            // 操作と確認：既存の税率の ID を外した一覧は、エラーコード TAX_RATE_STARTED_UNCHANGEABLE が返る
            replaceRates(id, 1, """
                    [{"ratePercent": 10}]""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TAX_RATE_STARTED_UNCHANGEABLE"));
        }

        @Test
        @DisplayName("AC-5 終了済みの税率を変える一覧は 400")
        void rejectsChangingEndedRate() throws Exception {
            // 準備：2026-10-09 に終わる 8% と、その後の 10% を持つ税区分
            var id = createdCategoryId(NAME);
            replaceRates(id, 0, """
                    [
                      {"ratePercent": 8, "effectiveTo": "2026-10-09T00:00:00+09:00"},
                      {"ratePercent": 10, "effectiveFrom": "2026-10-09T00:00:00+09:00"}
                    ]""").andExpect(status().isNoContent());
            var ids = rateIds(id);

            // 準備：1 件目が終了済みになるまで時刻を進める
            clock.setInstant(OffsetDateTime.parse("2026-10-10T00:00:00+09:00").toInstant());

            // 操作と確認：終了済みの率を 5% に変えると、エラーコード TAX_RATE_ENDED_UNCHANGEABLE が返る
            replaceRates(id, 1, """
                    [
                      {"id": "%s", "ratePercent": 5, "effectiveTo": "2026-10-09T00:00:00+09:00"},
                      {"id": "%s", "ratePercent": 10, "effectiveFrom": "2026-10-09T00:00:00+09:00"}
                    ]""".formatted(ids[0], ids[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TAX_RATE_ENDED_UNCHANGEABLE"));
        }
    }

    @Nested
    @DisplayName("率の範囲")
    class RateRange {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"0", "99.99"})
        @DisplayName("AC-7 0% 以上 100% 未満の率は登録できる")
        void acceptsRateInRange(String ratePercent) throws Exception {
            // 準備：税率のない税区分
            var id = createdCategoryId(NAME);

            // 操作と確認：204 が返る
            replaceRates(id, 0, """
                    [{"ratePercent": %s}]""".formatted(ratePercent))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("AC-7 率が 100% 以上なら 400")
        void rejectsRateOf100() throws Exception {
            // 準備：税率のない税区分
            var id = createdCategoryId(NAME);

            // 操作と確認：入力の形の誤りとして、エラーコード INVALID_REQUEST が返る
            replaceRates(id, 0, """
                    [{"ratePercent": 100}]""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-7 率が 0% 未満なら 400")
        void rejectsNegativeRate() throws Exception {
            // 準備：税率のない税区分
            var id = createdCategoryId(NAME);

            // 操作と確認：入力の形の誤りとして、エラーコード INVALID_REQUEST が返る
            replaceRates(id, 0, """
                    [{"ratePercent": -1}]""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-7 率が小数第 3 位以下を持つなら 400")
        void rejectsRateWithThreeDecimals() throws Exception {
            // 準備：税率のない税区分
            var id = createdCategoryId(NAME);

            // 操作と確認：エラーコード INVALID_RATE_PERCENT が返る
            replaceRates(id, 0, """
                    [{"ratePercent": 8.125}]""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_RATE_PERCENT"));
        }
    }

    @Nested
    @DisplayName("置き換えの対象")
    class ReplaceTarget {

        @Test
        @DisplayName("取得時のバージョンと一致しなければ 409")
        void rejectsVersionMismatch() throws Exception {
            // 準備：他の人が先に更新し、バージョンが 1 になった税区分
            var id = createdCategoryId(NAME);
            replaceRates(id, 0, """
                    [{"ratePercent": 10}]""").andExpect(status().isNoContent());

            // 操作と確認：古いバージョン 0 で置き換えると、エラーコード CONCURRENT_MODIFICATION が返る
            replaceRates(id, 0, """
                    [{"ratePercent": 8}]""")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
        }

        @Test
        @DisplayName("存在しない税区分は 404")
        void rejectsUnknownCategory() throws Exception {
            // 操作と確認：404 と、エラーコード NOT_FOUND が返る
            replaceRates(new UUID(0, 0), 0, """
                    [{"ratePercent": 10}]""")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 期間限定の税率に切り替えた税区分の、税率の一覧を確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>200 が返る</li>
     *   <li>バージョンが 2（初めの設定と切り替えの 2 回）</li>
     *   <li>税率が 3 件</li>
     *   <li>1 件目が、基本の税率（ID が変わらない）</li>
     *   <li>1 件目の開始が空白</li>
     *   <li>1 件目の終了が、2027-04-01 00:00（日本時間）を UTC で表した値</li>
     *   <li>2 件目の率が 8%</li>
     *   <li>3 件目の終了が空白</li>
     * </ul>
     *
     * @param id         税区分 ID
     * @param baseRateId 基本の税率の ID（登録のときに振られるので、定数にできない）
     */
    private void assertLimitedTimeRateList(UUID id, String baseRateId) throws Exception {
        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.rates.length()").value(3))
                .andExpect(jsonPath("$.rates[0].id").value(baseRateId))
                .andExpect(jsonPath("$.rates[0].effectiveFrom").value(nullValue()))
                .andExpect(jsonPath("$.rates[0].effectiveTo").value("2027-03-31T15:00:00Z"))
                .andExpect(jsonPath("$.rates[1].ratePercent").value(8))
                .andExpect(jsonPath("$.rates[2].effectiveTo").value(nullValue()));
    }

    /**
     * 期間限定の税率に切り替えた税区分の、各時点の現時点の率を確認する。
     *
     * <p>確認すること（時刻は日本時間）：
     * <ul>
     *   <li>2027-03-31 23:59 は 10%</li>
     *   <li>2027-04-01 00:00 は 8%</li>
     *   <li>2028-03-31 23:59 は 8%</li>
     *   <li>2028-04-01 00:00 は 10%</li>
     * </ul>
     *
     * @param id 税区分 ID
     */
    private void assertCurrentRatesAroundLimitedTime(UUID id) throws Exception {
        assertCurrentRateAt(id, "2027-03-31T23:59:00+09:00", 10);
        assertCurrentRateAt(id, "2027-04-01T00:00:00+09:00", 8);
        assertCurrentRateAt(id, "2028-03-31T23:59:00+09:00", 8);
        assertCurrentRateAt(id, "2028-04-01T00:00:00+09:00", 10);
    }

    /**
     * 時計を指定した時刻にして、その時点の税率が期待どおりかを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>200 が返る</li>
     *   <li>現時点の率</li>
     * </ul>
     *
     * @param id                  税区分 ID
     * @param at                  時刻（オフセット付き）
     * @param expectedRatePercent 期待する率（%）
     */
    private void assertCurrentRateAt(UUID id, String at, int expectedRatePercent) throws Exception {
        clock.setInstant(OffsetDateTime.parse(at).toInstant());
        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRatePercent").value(expectedRatePercent));
    }

    // ============================== 準備の補助 ==============================

    /**
     * 税区分を登録する。
     *
     * @param name 名称
     * @return 応答
     */
    private ResultActions createCategory(String name) throws Exception {
        return mockMvc.perform(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s"}""".formatted(name)));
    }

    /**
     * 税区分を登録して、その ID を返す。
     *
     * @param name 名称
     * @return 税区分 ID
     */
    private UUID createdCategoryId(String name) throws Exception {
        var body = createCategory(name).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return TestJson.createdId(body);
    }

    /**
     * 税率の一覧（JSON）を置き換える。
     *
     * @param id      税区分 ID
     * @param version 取得時のバージョン
     * @param rates   税率の一覧（JSON の配列）
     * @return 応答
     */
    private ResultActions replaceRates(UUID id, long version, String rates) throws Exception {
        return mockMvc.perform(put(BASE + "/" + id + "/rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": %d, "rates": %s}""".formatted(version, rates)));
    }

    /**
     * 10%（開始・終了とも空白）を持つ税区分を、期間限定の税率に切り替え、元に戻す一覧に置き換える。
     *
     * <p>置き換え後は「10%（〜2027-04-01 00:00）」「8%（2027-04-01 00:00〜2028-04-01 00:00）」「10%（2028-04-01 00:00〜）」。
     *
     * @param id         税区分 ID（バージョンは 1）
     * @param baseRateId 基本の税率の ID
     */
    private void switchToLimitedTimeRate(UUID id, String baseRateId) throws Exception {
        replaceRates(id, 1, """
                [
                  {"id": "%s", "ratePercent": 10, "effectiveTo": "2027-04-01T00:00:00+09:00"},
                  {"ratePercent": 8, "effectiveFrom": "2027-04-01T00:00:00+09:00", "effectiveTo": "2028-04-01T00:00:00+09:00"},
                  {"ratePercent": 10, "effectiveFrom": "2028-04-01T00:00:00+09:00"}
                ]""".formatted(baseRateId)).andExpect(status().isNoContent());
    }

    /**
     * 期間が重複する税率の一覧を返す。
     *
     * @return 10%（〜2028-04-01）と 8%（2027-04-01〜）の一覧（JSON の配列）
     */
    private static String overlappingRates() {
        return """
                [
                  {"ratePercent": 10, "effectiveTo": "2028-04-01T00:00:00+09:00"},
                  {"ratePercent": 8, "effectiveFrom": "2027-04-01T00:00:00+09:00"}
                ]""";
    }

    /**
     * 税区分の税率の ID を、一覧の順に返す。
     *
     * @param id 税区分 ID
     * @return 税率の ID の配列
     */
    private String[] rateIds(UUID id) throws Exception {
        var body = mockMvc.perform(get(BASE + "/" + id)).andReturn().getResponse().getContentAsString();
        return JsonPath.<List<String>>read(body, "$.rates[*].id").toArray(String[]::new);
    }
}
