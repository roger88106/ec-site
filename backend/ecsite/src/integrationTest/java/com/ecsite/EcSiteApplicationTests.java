package com.ecsite;

import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

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

    @Autowired
    ApplicationContext context;

    @Test
    @DisplayName("Spring のコンテキストが起動する")
    void contextLoads() {
    }

    @Test
    @DisplayName("定期ジョブの切り替え（ecsite.scheduling.enabled）が false なら、定期ジョブを自動では動かさない")
    void schedulingIsDisabled() {
        // @EnableScheduling が 1 つでも有効だと、@Scheduled のジョブがテストと並んで動き、テストが呼ぶジョブを分散ロックで飛ばす
        assertThat(context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
    }

    @Test
    @DisplayName("モジュールごとの schema が作られる")
    void schemasAreCreated() {
        var schemas = jdbcTemplate.queryForList(
                "SELECT schema_name FROM information_schema.schemata", String.class);

        assertThat(schemas).contains("common", "tax", "catalog", "media", "inventory", "cart", "ordering");
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

    @Test
    @DisplayName("すべてのテーブルに論理名のコメントがある")
    void everyTableHasLogicalName() {
        // PostgreSQL のシステムの schema と、Flyway の履歴のテーブルは除く
        var tables = jdbcTemplate.queryForList("""
                SELECT n.nspname || '.' || c.relname
                  FROM pg_class c
                  JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE c.relkind IN ('r', 'p')
                   AND n.nspname NOT IN ('pg_catalog', 'information_schema')
                   AND n.nspname NOT LIKE 'pg\\_%'
                   AND c.relname <> 'flyway_schema_history'
                   AND obj_description(c.oid, 'pg_class') IS NULL
                """, String.class);

        assertThat(tables).isEmpty();
    }

    @Test
    @DisplayName("すべてのカラムに論理名のコメントがある")
    void everyColumnHasLogicalName() {
        // テーブルの検査と同じ範囲の、削除済みでないカラム
        var columns = jdbcTemplate.queryForList("""
                SELECT n.nspname || '.' || c.relname || '.' || a.attname
                  FROM pg_attribute a
                  JOIN pg_class c ON c.oid = a.attrelid
                  JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE c.relkind IN ('r', 'p')
                   AND a.attnum > 0
                   AND NOT a.attisdropped
                   AND n.nspname NOT IN ('pg_catalog', 'information_schema')
                   AND n.nspname NOT LIKE 'pg\\_%'
                   AND c.relname <> 'flyway_schema_history'
                   AND col_description(c.oid, a.attnum) IS NULL
                """, String.class);

        assertThat(columns).isEmpty();
    }
}
