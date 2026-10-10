package com.ecsite.module.tax.presentation;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;

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
 * 税区分 API の受け入れテスト（T110、US-1）。<br />
 * 現在時刻は {@link MutableClock} で 2026-10-08 00:00（日本時間）に固定する。
 * AC-4 の日時は日本時間のオフセット付きで送る。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class TaxCategoryApiTest {

    /**
     * API のパス
     */
    private static final String BASE = "/api/admin/tax-categories";

    /**
     * 現在：2026-10-08 00:00（日本時間）
     */
    private static final Instant NOW = OffsetDateTime.parse("2026-10-08T00:00:00+09:00").toInstant();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @BeforeEach
    void setClock() {
        clock.setInstant(NOW);
    }

    @AfterEach
    void resetClock() {
        clock.setInstant(NOW);
    }

    @Nested
    @DisplayName("AC-1 税区分の登録")
    class Create {

        @Test
        @DisplayName("AC-1 名称で登録できる。Location の URL で、その税区分を取得できる")
        void createsAndGetsByLocation() throws Exception {
            var result = createCategory("標準")
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(".*" + BASE + "/[0-9a-f-]{36}")))
                    .andExpect(jsonPath("$.id").isString())
                    .andReturn();
            String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

            mockMvc.perform(get(result.getResponse().getHeader(HttpHeaders.LOCATION)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.name").value("標準"))
                    .andExpect(jsonPath("$.rates").isEmpty())
                    .andExpect(jsonPath("$.currentRatePercent").value(nullValue()))
                    .andExpect(jsonPath("$.version").value(0));
        }

        @Test
        @DisplayName("AC-1 名称が重複する税区分は 409")
        void rejectsDuplicatedName() throws Exception {
            createCategory("標準").andExpect(status().isCreated());

            createCategory("標準")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("TAX_CATEGORY_NAME_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("名称が空なら 400")
        void rejectsEmptyName() throws Exception {
            createCategory("")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("名称が空白だけなら 400")
        void rejectsBlankName() throws Exception {
            createCategory(" ")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_TAX_CATEGORY_NAME"));
        }
    }

    @Nested
    @DisplayName("取得")
    class Get {

        @Test
        @DisplayName("存在しない ID は 404")
        void returnsNotFound() throws Exception {
            mockMvc.perform(get(BASE + "/00000000-0000-0000-0000-000000000000"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("一覧で、各税区分の現時点の率が返る")
        void listsWithCurrentRate() throws Exception {
            var standard = createdId("標準");
            replaceRates(standard, 0, """
                    [{"ratePercent": 10}]""").andExpect(status().isNoContent());
            var reduced = createdId("軽減");
            replaceRates(reduced, 0, """
                    [{"ratePercent": 8}]""").andExpect(status().isNoContent());

            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[?(@.id == '%s')].currentRatePercent".formatted(standard)).value(hasItem(10)))
                    .andExpect(jsonPath("$.items[?(@.id == '%s')].currentRatePercent".formatted(reduced)).value(hasItem(8)));
        }
    }

    @Nested
    @DisplayName("税率の一覧の置き換え")
    class ReplaceRates {

        @Test
        @DisplayName("AC-4 期間限定の税率に切り替え、元に戻せる。各時点の現時点の率が正しい")
        void switchesToLimitedTimeRateAndBack() throws Exception {
            // Given：「標準」に 10%（開始・終了とも空白）がある。現在は 2026-10-08
            var id = createdId("標準");
            replaceRates(id, 0, """
                    [{"ratePercent": 10}]""").andExpect(status().isNoContent());
            var baseRateId = rateIds(id)[0];

            // When：一覧を「10%（〜2027-04-01 00:00）」「8%（2027-04-01 00:00〜2028-04-01 00:00）」「10%（2028-04-01 00:00〜）」に更新する
            replaceRates(id, 1, """
                    [
                      {"id": "%s", "ratePercent": 10, "effectiveTo": "2027-04-01T00:00:00+09:00"},
                      {"ratePercent": 8, "effectiveFrom": "2027-04-01T00:00:00+09:00", "effectiveTo": "2028-04-01T00:00:00+09:00"},
                      {"ratePercent": 10, "effectiveFrom": "2028-04-01T00:00:00+09:00"}
                    ]""".formatted(baseRateId)).andExpect(status().isNoContent());

            // 一覧は適用期間の古い順。日時は UTC で返る
            mockMvc.perform(get(BASE + "/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(2))
                    .andExpect(jsonPath("$.rates.length()").value(3))
                    .andExpect(jsonPath("$.rates[0].id").value(baseRateId))
                    .andExpect(jsonPath("$.rates[0].effectiveFrom").value(nullValue()))
                    .andExpect(jsonPath("$.rates[0].effectiveTo").value("2027-03-31T15:00:00Z"))
                    .andExpect(jsonPath("$.rates[1].ratePercent").value(8))
                    .andExpect(jsonPath("$.rates[2].effectiveTo").value(nullValue()));

            // Then：2027-03-31 23:59 は 10%、2027-04-01 00:00 から 2028-03-31 23:59 は 8%、2028-04-01 00:00 以降は 10%
            assertCurrentRateAt(id, "2027-03-31T23:59:00+09:00", 10);
            assertCurrentRateAt(id, "2027-04-01T00:00:00+09:00", 8);
            assertCurrentRateAt(id, "2028-03-31T23:59:00+09:00", 8);
            assertCurrentRateAt(id, "2028-04-01T00:00:00+09:00", 10);
        }

        @Test
        @DisplayName("AC-2 開始が終了より後の税率は 400")
        void rejectsInvalidPeriod() throws Exception {
            var id = createdId("標準");

            replaceRates(id, 0, """
                    [{"ratePercent": 10, "effectiveFrom": "2028-04-01T00:00:00+09:00", "effectiveTo": "2027-04-01T00:00:00+09:00"}]""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_EFFECTIVE_PERIOD"));
        }

        @Test
        @DisplayName("AC-3 期間が重複する一覧は 400。全体が拒否される")
        void rejectsOverlap() throws Exception {
            var id = createdId("標準");

            replaceRates(id, 0, """
                    [
                      {"ratePercent": 10, "effectiveTo": "2028-04-01T00:00:00+09:00"},
                      {"ratePercent": 8, "effectiveFrom": "2027-04-01T00:00:00+09:00"}
                    ]""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TAX_RATE_PERIODS_OVERLAP"));

            mockMvc.perform(get(BASE + "/" + id))
                    .andExpect(jsonPath("$.rates").isEmpty())
                    .andExpect(jsonPath("$.version").value(0));
        }

        @Test
        @DisplayName("AC-3 期間の間に途切れがある一覧は 400")
        void rejectsGap() throws Exception {
            var id = createdId("標準");

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
            var id = createdId("標準");

            replaceRates(id, 0, """
                    [{"ratePercent": 10, "effectiveTo": "2027-04-01T00:00:00+09:00"}]""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TAX_RATE_LAST_PERIOD_NOT_OPEN_ENDED"));
        }

        @Test
        @DisplayName("AC-5 現在より前の日時を設定する一覧は 400")
        void rejectsPastDateTime() throws Exception {
            var id = createdId("標準");

            replaceRates(id, 0, """
                    [{"ratePercent": 10, "effectiveFrom": "2026-10-07T23:59:00+09:00"}]""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TAX_RATE_PAST_DATETIME"));
        }

        @Test
        @DisplayName("AC-5 開始済みの税率の率を変える一覧は 400")
        void rejectsChangingStartedRate() throws Exception {
            var id = createdId("標準");
            replaceRates(id, 0, """
                    [{"ratePercent": 10}]""").andExpect(status().isNoContent());

            replaceRates(id, 1, """
                    [{"id": "%s", "ratePercent": 8}]""".formatted(rateIds(id)[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TAX_RATE_STARTED_UNCHANGEABLE"));
        }

        @Test
        @DisplayName("AC-5 開始済みの税率を削除する一覧は 400")
        void rejectsDeletingStartedRate() throws Exception {
            var id = createdId("標準");
            replaceRates(id, 0, """
                    [{"ratePercent": 10}]""").andExpect(status().isNoContent());

            replaceRates(id, 1, """
                    [{"ratePercent": 10}]""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TAX_RATE_STARTED_UNCHANGEABLE"));
        }

        @Test
        @DisplayName("AC-5 終了済みの税率を変える一覧は 400")
        void rejectsChangingEndedRate() throws Exception {
            var id = createdId("標準");
            replaceRates(id, 0, """
                    [
                      {"ratePercent": 8, "effectiveTo": "2026-10-09T00:00:00+09:00"},
                      {"ratePercent": 10, "effectiveFrom": "2026-10-09T00:00:00+09:00"}
                    ]""").andExpect(status().isNoContent());
            var ids = rateIds(id);
            // 1 件目が終了済みになるまで時刻を進める
            clock.setInstant(OffsetDateTime.parse("2026-10-10T00:00:00+09:00").toInstant());

            replaceRates(id, 1, """
                    [
                      {"id": "%s", "ratePercent": 5, "effectiveTo": "2026-10-09T00:00:00+09:00"},
                      {"id": "%s", "ratePercent": 10, "effectiveFrom": "2026-10-09T00:00:00+09:00"}
                    ]""".formatted(ids[0], ids[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TAX_RATE_ENDED_UNCHANGEABLE"));
        }

        @Test
        @DisplayName("AC-7 率が 100% 以上なら 400")
        void rejectsRateOf100() throws Exception {
            var id = createdId("標準");

            replaceRates(id, 0, """
                    [{"ratePercent": 100}]""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-7 率が 0% 未満なら 400")
        void rejectsNegativeRate() throws Exception {
            var id = createdId("標準");

            replaceRates(id, 0, """
                    [{"ratePercent": -1}]""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-7 率が小数第 3 位以下を持つなら 400")
        void rejectsRateWithThreeDecimals() throws Exception {
            var id = createdId("標準");

            replaceRates(id, 0, """
                    [{"ratePercent": 8.125}]""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_RATE_PERCENT"));
        }

        @Test
        @DisplayName("取得時のバージョンと一致しなければ 409")
        void rejectsVersionMismatch() throws Exception {
            var id = createdId("標準");
            replaceRates(id, 0, """
                    [{"ratePercent": 10}]""").andExpect(status().isNoContent());

            // 他の人が先に更新した（バージョンは 1 になっている）
            replaceRates(id, 0, """
                    [{"ratePercent": 8}]""")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
        }

        @Test
        @DisplayName("存在しない税区分は 404")
        void rejectsUnknownCategory() throws Exception {
            replaceRates("00000000-0000-0000-0000-000000000000", 0, """
                    [{"ratePercent": 10}]""")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }
    }

    // ============================== 補助 ==============================

    /**
     * 税区分を登録する
     */
    private ResultActions createCategory(String name) throws Exception {
        return mockMvc.perform(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s"}""".formatted(name)));
    }

    /**
     * 税区分を登録して、その ID を返す
     */
    private String createdId(String name) throws Exception {
        var body = createCategory(name).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /**
     * 税率の一覧（JSON）を置き換える
     */
    private ResultActions replaceRates(String id, long version, String rates) throws Exception {
        return mockMvc.perform(put(BASE + "/" + id + "/rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": %d, "rates": %s}""".formatted(version, rates)));
    }

    /**
     * 税区分の税率の ID を、一覧の順に返す
     */
    private String[] rateIds(String id) throws Exception {
        var body = mockMvc.perform(get(BASE + "/" + id)).andReturn().getResponse().getContentAsString();
        return JsonPath.<java.util.List<String>>read(body, "$.rates[*].id").toArray(String[]::new);
    }

    /**
     * 時計を指定した時刻にして、その時点の税率が期待どおりかを確かめる
     */
    private void assertCurrentRateAt(String id, String at, int expectedRatePercent) throws Exception {
        clock.setInstant(OffsetDateTime.parse(at).toInstant());
        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRatePercent").value(expectedRatePercent));
    }
}
