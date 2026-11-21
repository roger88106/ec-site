package com.ecsite.config.json;

import com.ecsite.common.model.base.EntityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.ValueInstantiationException;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 識別子の型の JSON の単体テスト
 *
 * <p>{@link EntityId} の実装が、UUID の文字列として書かれ、その文字列から読めることと、読めない入力を拒否することを確認する。
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

    @Test
    @DisplayName("JSON の null は、null として読む")
    void readsNullAsNull() {
        // 操作と確認：識別子でなく null になる
        assertThat(mapper.readValue("null", SampleId.class)).isNull();
    }

    @Test
    @DisplayName("識別子を項目に持つ record の、JSON の null の項目は、null として読む")
    void readsNullFieldAsNull() {
        // 操作と確認：項目が null になる
        assertThat(mapper.readValue("{\"sampleId\":null}", Holder.class)).isEqualTo(new Holder(null));
    }

    @ParameterizedTest(name = "「{0}」")
    @ValueSource(strings = {"\"not-a-uuid\"", "\"\"", "123", "true"})
    @DisplayName("UUID の文字列でない値は、値の形の例外で拒否する")
    void rejectsNonUuid(String json) {
        // 操作と確認：値の形が違う例外になる（API では 500 でなく 400 の形のエラーにできる）
        assertThatThrownBy(() -> mapper.readValue(json, SampleId.class)).isInstanceOf(InvalidFormatException.class);
    }

    @Test
    @DisplayName("コンストラクターが拒否する値は、生成の例外で拒否する")
    void rejectsValueRejectedByConstructor() {
        // 操作と確認：コンストラクターの例外が、生成の例外に包まれる
        assertThatThrownBy(() -> mapper.readValue("\"" + new UUID(0, 0) + "\"", StrictId.class))
                .isInstanceOf(ValueInstantiationException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("UUID 1 つのコンストラクターがない record は、UUID の文字列で書かない")
    void doesNotWriteRecordWithoutUuidConstructorAsString() {
        // 操作と確認：この設定の対象にならず、項目を持つ JSON の object になる
        assertThat(mapper.writeValueAsString(new TwoFieldsId(VALUE, "x")))
                .isEqualTo("{\"value\":\"" + VALUE + "\",\"extra\":\"x\"}");
    }

    @Test
    @DisplayName("UUID 1 つのコンストラクターがない record は、UUID の文字列から読まない")
    void doesNotReadRecordWithoutUuidConstructorFromString() {
        // 操作と確認：この設定の対象にならないので、UUID の文字列からは作れない
        assertThatThrownBy(() -> mapper.readValue("\"" + VALUE + "\"", TwoFieldsId.class)).isNotNull();
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
     * 特定の値を拒否する、テストの中だけの識別子の型
     *
     * @param value UUID（全て 0 は拒否する）
     */
    record StrictId(UUID value) implements EntityId {

        StrictId {
            if (value.equals(new UUID(0, 0))) {
                throw new IllegalArgumentException("全て 0 は使えない");
            }
        }
    }

    /**
     * UUID 1 つのコンストラクターを持たない、テストの中だけの識別子の型
     *
     * @param value UUID
     * @param extra 余分な項目
     */
    record TwoFieldsId(UUID value, String extra) implements EntityId {
    }

    /**
     * 識別子を項目に持つ record
     *
     * @param sampleId 識別子
     */
    record Holder(SampleId sampleId) {
    }
}
