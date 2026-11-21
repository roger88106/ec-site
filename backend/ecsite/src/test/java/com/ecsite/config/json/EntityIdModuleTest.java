package com.ecsite.config.json;

import com.ecsite.common.model.base.EntityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 識別子の型の JSON の単体テスト
 *
 * <p>{@link EntityId} の実装が、UUID の文字列として書かれ、その文字列から読めることを確認する。
 */
class EntityIdModuleTest {

    // ============================== 定数 ==============================

    /**
     * 識別子の値
     */
    private static final UUID VALUE = UUID.fromString("0b1f4c52-6d7e-4f0a-9a31-2c8d5e7b9a10");

    // ============================== Bean ==============================

    /**
     * 識別子のモジュールを登録した JSON の変換
     */
    private final JsonMapper mapper = JsonMapper.builder().addModule(new EntityIdModule()).build();

    // ============================== テスト ==============================

    @Test
    @DisplayName("識別子は、UUID の文字列で書く")
    void writesAsUuidString() {
        // 操作と確認：UUID の文字列になる
        assertThat(mapper.writeValueAsString(new SampleId(VALUE))).isEqualTo("\"" + VALUE + "\"");
    }

    @Test
    @DisplayName("識別子を項目に持つ record は、項目を UUID の文字列で書く")
    void writesFieldAsUuidString() {
        // 操作と確認：項目の値が UUID の文字列になる
        assertThat(mapper.writeValueAsString(new Holder(new SampleId(VALUE)))).isEqualTo("{\"sampleId\":\"" + VALUE + "\"}");
    }

    @Test
    @DisplayName("UUID の文字列から、識別子を読める")
    void readsFromUuidString() {
        // 操作と確認：識別子になる
        assertThat(mapper.readValue("\"" + VALUE + "\"", SampleId.class)).isEqualTo(new SampleId(VALUE));
    }

    @Test
    @DisplayName("識別子を項目に持つ record を、JSON から読める")
    void readsFieldFromUuidString() {
        // 操作と確認：項目が識別子になる
        assertThat(mapper.readValue("{\"sampleId\":\"" + VALUE + "\"}", Holder.class)).isEqualTo(new Holder(new SampleId(VALUE)));
    }

    // ============================== 準備の補助 ==============================

    /**
     * テストの中だけの識別子の型
     *
     * @param value UUID
     */
    record SampleId(UUID value) implements EntityId {
    }

    /**
     * 識別子を項目に持つ record
     *
     * @param sampleId 識別子
     */
    record Holder(SampleId sampleId) {
    }
}
