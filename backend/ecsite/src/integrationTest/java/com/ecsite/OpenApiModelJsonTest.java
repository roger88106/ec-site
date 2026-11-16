package com.ecsite;

import com.ecsite.api.admin.model.TaxRateDraft;
import com.ecsite.support.config.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAPI の生成モデルの JSON 変換の検証
 *
 * <p>nullable の項目は {@code JsonNullable} で生成されるため、そのモジュールが登録されている必要がある。
 */
@IntegrationTest
class OpenApiModelJsonTest {

    // ============================== 定数 ==============================

    /**
     * 開始日時に null を送り、終了日時を送らない税率の本文
     */
    private static final String NULL_AND_ABSENT = """
            {"ratePercent": 10, "effectiveFrom": null}
            """;

    // ============================== Bean ==============================

    /**
     * アプリの JSON の変換
     */
    @Autowired
    JsonMapper jsonMapper;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("nullable の項目の読み込み")
    class Reading {

        @Test
        @DisplayName("null を送った項目は、値ありで中身が null になる")
        void readsNullAsPresent() {
            // 操作：開始日時に null を送った本文を読む
            var draft = jsonMapper.readValue(NULL_AND_ABSENT, TaxRateDraft.class);

            // 確認：開始日時は値ありで、中身が null
            assertThat(draft.getEffectiveFrom().isPresent()).isTrue();
            assertThat(draft.getEffectiveFrom().get()).isNull();
        }

        @Test
        @DisplayName("送らなかった項目は、値なしになる（null を送った場合と区別できる）")
        void readsAbsentAsUndefined() {
            // 操作：終了日時を送らない本文を読む
            var draft = jsonMapper.readValue(NULL_AND_ABSENT, TaxRateDraft.class);

            // 確認：終了日時は値なし
            assertThat(draft.getEffectiveTo().isPresent()).isFalse();
        }
    }

    @Test
    @DisplayName("nullable の項目は、値そのものとして JSON に出力される。日時は UTC で出力される")
    void serializesNullableAsPlainValue() {
        // 準備：日本時間の開始日時を送った本文を読む
        var draft = jsonMapper.readValue("""
                {"ratePercent": 8, "effectiveFrom": "2027-04-01T00:00:00+09:00"}
                """, TaxRateDraft.class);

        // 操作：JSON に書く
        var json = jsonMapper.writeValueAsString(draft);

        // 確認：包まない値で、同じ時点の UTC（日本時間での表示は画面で行う）
        assertThat(json).contains("\"effectiveFrom\":\"2027-03-31T15:00:00Z\"");
    }
}
