package com.ecsite;

import com.ecsite.api.admin.model.TaxRateDraft;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAPI の生成モデルが、アプリの JSON 変換で正しく扱えることの検証。<br />
 * nullable の項目は {@code JsonNullable} で生成されるため、そのモジュールが登録されている必要がある。
 */
@IntegrationTest
class OpenApiModelJsonTest {

    @Autowired
    JsonMapper jsonMapper;

    @Test
    @DisplayName("nullable の項目で、null を送った場合と送らなかった場合を区別できる")
    void distinguishesNullFromAbsent() {
        var draft = jsonMapper.readValue("""
                {"ratePercent": 10, "effectiveFrom": null}
                """, TaxRateDraft.class);

        // null を送った項目：値ありで中身が null
        assertThat(draft.getEffectiveFrom().isPresent()).isTrue();
        assertThat(draft.getEffectiveFrom().get()).isNull();
        // 送らなかった項目：値なし
        assertThat(draft.getEffectiveTo().isPresent()).isFalse();
    }

    @Test
    @DisplayName("nullable の項目は、値そのものとして JSON に出力される。日時は UTC で出力される")
    void serializesNullableAsPlainValue() {
        var draft = jsonMapper.readValue("""
                {"ratePercent": 8, "effectiveFrom": "2027-04-01T00:00:00+09:00"}
                """, TaxRateDraft.class);

        var json = jsonMapper.writeValueAsString(draft);

        // 受け取った日時は UTC に揃えられる（同じ時点）。日本時間での表示は画面で行う
        assertThat(json).contains("\"effectiveFrom\":\"2027-03-31T15:00:00Z\"");
    }
}
