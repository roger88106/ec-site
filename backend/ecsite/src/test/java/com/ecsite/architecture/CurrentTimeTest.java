package com.ecsite.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 現在時刻の取り方の検査（docs/rules/backend-coding.md 第 4.2 節）。<br />
 * 本番のクラスは、日時の型の引数なしの {@code now()} を呼ばず、注入した {@link Clock} から現在時刻を取る。
 * {@code now(clock)} のように時計を渡す呼び出しは当たらない。
 */
class CurrentTimeTest {

    /**
     * 本番のクラス（テストと、生成したコードを除く）
     */
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(location -> !location.contains("/com/ecsite/jooq/") && !location.contains("/com/ecsite/api/"))
            .importPackages("com.ecsite");

    /**
     * 日時の型の引数なしの now() を呼ばない規則
     */
    private static final ArchRule NO_SYSTEM_NOW = noClasses()
            .should().callMethod(Instant.class, "now")
            .orShould().callMethod(LocalDate.class, "now")
            .orShould().callMethod(LocalDateTime.class, "now")
            .orShould().callMethod(LocalTime.class, "now")
            .orShould().callMethod(OffsetDateTime.class, "now")
            .orShould().callMethod(ZonedDateTime.class, "now")
            .because("現在時刻は注入した Clock から取る（docs/rules/backend-coding.md 第 4.2 節）");

    @Test
    @DisplayName("本番のクラスは、日時の型の引数なしの now() を呼ばない")
    void productionClassesTakeCurrentTimeFromClock() {
        NO_SYSTEM_NOW.check(CLASSES);
    }

    @Test
    @DisplayName("規則は、引数なしの now() を呼ぶクラスに当たり、時計を渡すクラスには当たらない")
    void ruleMatchesOnlySystemNow() {
        assertThat(NO_SYSTEM_NOW.evaluate(new ClassFileImporter().importClasses(CallsSystemNow.class)).hasViolation())
                .isTrue();
        assertThat(NO_SYSTEM_NOW.evaluate(new ClassFileImporter().importClasses(CallsNowWithClock.class)).hasViolation())
                .isFalse();
    }
}

/**
 * 規則に当たる例：引数なしの now() を呼ぶ
 */
final class CallsSystemNow {

    /**
     * 例の関数。
     *
     * @return 今の時刻
     */
    Instant now() {
        return Instant.now();
    }
}

/**
 * 規則に合う例：時計を渡して now() を呼ぶ
 */
final class CallsNowWithClock {

    /**
     * 時計
     */
    private final Clock clock = Clock.systemUTC();

    /**
     * 例の関数。
     *
     * @return 今の時刻
     */
    Instant now() {
        return Instant.now(clock);
    }
}
