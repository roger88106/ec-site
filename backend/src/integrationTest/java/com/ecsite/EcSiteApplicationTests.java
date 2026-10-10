package com.ecsite;

import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.ecsite.jooq.common.Tables.EVENT_PUBLICATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * アプリケーションコンテキストが起動し、マイグレーションが適用されることの検証。
 */
@IntegrationTest
class EcSiteApplicationTests {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DSLContext dsl;

    @Test
    void contextLoads() {
    }

    @Test
    @DisplayName("モジュールごとの schema が作られる")
    void schemasAreCreated() {
        var schemas = jdbcTemplate.queryForList(
                "SELECT schema_name FROM information_schema.schemata", String.class);

        assertThat(schemas).contains("common", "tax", "catalog", "media", "inventory");
    }

    @Test
    @DisplayName("イベント発行記録のテーブルが common schema に作られる")
    void eventPublicationTableIsCreated() {
        var count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'common' AND table_name = 'event_publication'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("jOOQ の生成コードでテーブルにアクセスできる")
    void generatedJooqCodeCanAccessTable() {
        // 件数は、先に動いたテスト（ロールバックしないもの）が発行したイベントで変わるので見ない
        assertThatCode(() -> dsl.fetchCount(EVENT_PUBLICATION)).doesNotThrowAnyException();
    }
}
