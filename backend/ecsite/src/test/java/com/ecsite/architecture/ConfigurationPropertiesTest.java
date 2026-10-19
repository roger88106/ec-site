package com.ecsite.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * 設定の値の型を有効にする所の検査（docs/backend/coding.md 第 6.1 節）。
 */
class ConfigurationPropertiesTest {

    @Test
    @DisplayName("@EnableConfigurationProperties を付けるクラスは、設定のクラス（@Configuration）")
    void enableConfigurationPropertiesIsOnConfiguration() {
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(location -> !location.contains("/com/ecsite/jooq/") && !location.contains("/com/ecsite/api/"))
                .importPackages("com.ecsite");
        ArchRule rule = classes().that().areAnnotatedWith(EnableConfigurationProperties.class)
                .should().beMetaAnnotatedWith(Configuration.class);
        rule.check(classes);
    }
}
