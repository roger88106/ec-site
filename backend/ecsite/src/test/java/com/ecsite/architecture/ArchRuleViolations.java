package com.ecsite.architecture;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 構成の検査の、規則の違反の詳細を確認する補助
 *
 * <p>違反の例に規則を当てたときの、ArchUnit の違反の詳細（{@code getFailureReport().getDetails()}）を読む。
 */
final class ArchRuleViolations {

    /**
     * インスタンスを作らない。
     */
    private ArchRuleViolations() {
    }

    /**
     * 規則の違反の詳細に、指定した依存元から依存先への依存が 1 件あることを確認する。
     *
     * @param details 規則の違反の詳細
     * @param origin  依存元のクラス
     * @param target  依存先のクラス
     */
    static void assertViolation(List<String> details, Class<?> origin, Class<?> target) {
        assertThat(details)
                .as("%s -> %s", origin.getSimpleName(), target.getSimpleName())
                .filteredOn(detail -> detail.contains(origin.getName()) && detail.contains(target.getName()))
                .hasSize(1);
    }

    /**
     * 規則の違反の詳細に、指定したクラスを含むものがないことを確認する。
     *
     * @param details 規則の違反の詳細
     * @param example 規則に合う例のクラス
     */
    static void assertNoViolation(List<String> details, Class<?> example) {
        assertThat(details)
                .as(example.getSimpleName())
                .noneMatch(detail -> detail.contains(example.getName()));
    }
}
