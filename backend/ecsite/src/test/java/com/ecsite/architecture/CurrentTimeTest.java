package com.ecsite.architecture;

import com.ecsite.ProductionClasses;
import com.ecsite.architecture.sample.currenttime.CallsCalendarGetInstance;
import com.ecsite.architecture.sample.currenttime.CallsCurrentTimeMillis;
import com.ecsite.architecture.sample.currenttime.CallsNowWithClock;
import com.ecsite.architecture.sample.currenttime.CallsNowWithZone;
import com.ecsite.architecture.sample.currenttime.CallsSystemNow;
import com.ecsite.architecture.sample.currenttime.CallsYearNow;
import com.ecsite.architecture.sample.currenttime.CreatesDate;
import com.ecsite.architecture.sample.currenttime.CreatesDefaultZoneClock;
import com.ecsite.architecture.sample.currenttime.CreatesSystemClock;
import com.ecsite.architecture.sample.currenttime.CreatesSystemUtcClock;
import com.ecsite.architecture.sample.currenttime.ExampleClockConfig;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 現在時刻の取り方の検査
 *
 * <p>根拠はバックエンドの書き方の「現在時刻は Clock から」。
 * 本番のクラスは、注入した {@link Clock} から現在時刻を取る。次の 2 つを確認する。
 * <ul>
 *   <li>システムの時計から現在時刻を取る呼び出しをしない。対象は、日時の型の {@code now()} と {@code now(ZoneId)}、
 *       {@code System.currentTimeMillis()}、引数なしの {@code new Date()}、{@code Calendar.getInstance()}</li>
 *   <li>システムの時計（{@code Clock.systemUTC()} など）を作るのは、時計の Bean を作る {@code ClockConfig} だけ</li>
 * </ul>
 * {@code now(clock)} のように時計を渡す呼び出しは当たらない。
 */
class CurrentTimeTest {

    // ============================== 定数 ==============================

    /**
     * {@code now} で現在時刻を取れる日時の型
     */
    private static final Set<Class<?>> DATE_TIME_TYPES = Set.of(
            Instant.class, LocalDate.class, LocalDateTime.class, LocalTime.class, OffsetDateTime.class, OffsetTime.class,
            ZonedDateTime.class, Year.class, YearMonth.class, MonthDay.class);

    /**
     * 日時の型の、時計を渡さない {@code now}（引数なし、{@code now(ZoneId)}）の呼び出し
     */
    private static final DescribedPredicate<JavaMethodCall> NOW_WITHOUT_CLOCK = DescribedPredicate.describe(
            "now() or now(ZoneId) of a date-time type",
            call -> call.getName().equals("now")
                    && DATE_TIME_TYPES.stream().anyMatch(type -> call.getTargetOwner().isEquivalentTo(type))
                    && !takesOnlyClock(call));

    /**
     * {@code Calendar.getInstance}（引数の有無を問わない）の呼び出し
     */
    private static final DescribedPredicate<JavaMethodCall> CALENDAR_GET_INSTANCE = DescribedPredicate.describe(
            "Calendar.getInstance",
            call -> call.getName().equals("getInstance") && call.getTargetOwner().isEquivalentTo(Calendar.class));

    /**
     * 時計を渡さずに、システムの時計から現在時刻を取らない規則
     */
    private static final ArchRule NO_SYSTEM_TIME = noClasses()
            .should().callMethodWhere(NOW_WITHOUT_CLOCK)
            .orShould().callMethod(System.class, "currentTimeMillis")
            .orShould().callConstructor(Date.class)
            .orShould().callConstructor(GregorianCalendar.class)
            .orShould().callMethodWhere(CALENDAR_GET_INSTANCE)
            .because("現在時刻は注入した Clock から取る（バックエンドの書き方の「現在時刻は Clock から」）");

    /**
     * システムの時計を作ってよい、時計の Bean の設定のクラス
     *
     * <p>package-private のクラスなので、名前で指定する。
     */
    private static final String CLOCK_CONFIG = "com.ecsite.config.ClockConfig";

    // ============================== テスト ==============================

    @Nested
    @DisplayName("本番のクラス")
    class ProductionCode {

        @Test
        @DisplayName("本番のクラスは、時計を渡さずに現在時刻を取らない")
        void productionClassesTakeCurrentTimeFromClock() {
            // 操作と確認：本番の手書きのクラスに規則を当てる
            NO_SYSTEM_TIME.check(ProductionClasses.handwritten());
        }

        @Test
        @DisplayName("本番のクラスは、時計の Bean の設定の外で、システムの時計を作らない")
        void productionClassesDoNotCreateSystemClock() {
            // 操作と確認：本番の手書きのクラスに規則を当てる
            systemClockRule(CLOCK_CONFIG).check(ProductionClasses.handwritten());
        }

        @Test
        @DisplayName("システムの時計を作ってよいクラスは、本番のクラスでシステムの時計を作っている")
        void clockConfigCreatesSystemClock() {
            // 準備：例外のクラスを除かない規則
            ArchRule withoutException = systemClockRule("");

            // 操作：本番の手書きのクラスに規則を当てる
            List<String> details = withoutException.evaluate(ProductionClasses.handwritten()).getFailureReport().getDetails();

            // 確認：例外のクラスが違反になる（例外の一覧が使われている）
            assertThat(details).anyMatch(detail -> detail.contains(CLOCK_CONFIG));
        }
    }

    @Nested
    @DisplayName("現在時刻の規則")
    class SystemTimeRule {

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.architecture.CurrentTimeTest#systemTimeExamples")
        @DisplayName("規則は、時計を渡さずに現在時刻を取る例に当たる")
        void ruleMatchesSystemTime(Class<?> example) {
            // 準備：時計を渡さずに現在時刻を取る例
            JavaClasses examples = new ClassFileImporter().importClasses(example);

            // 操作と確認：違反になる
            assertThat(NO_SYSTEM_TIME.evaluate(examples).hasViolation()).isTrue();
        }

        @Test
        @DisplayName("規則は、時計を渡して now を呼ぶ例に当たらない")
        void ruleDoesNotMatchNowWithClock() {
            // 準備：注入した時計を渡して now を呼ぶ例
            JavaClasses examples = new ClassFileImporter().importClasses(CallsNowWithClock.class);

            // 操作と確認：違反にならない
            assertThat(NO_SYSTEM_TIME.evaluate(examples).hasViolation()).isFalse();
        }
    }

    @Nested
    @DisplayName("システムの時計の規則")
    class SystemClockRule {

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.architecture.CurrentTimeTest#systemClockExamples")
        @DisplayName("規則は、システムの時計を作る例に当たる")
        void ruleMatchesSystemClock(Class<?> example) {
            // 準備：システムの時計を作る例
            JavaClasses examples = new ClassFileImporter().importClasses(example);

            // 操作と確認：例外の一覧にないので違反になる
            assertThat(systemClockRule(CLOCK_CONFIG).evaluate(examples).hasViolation()).isTrue();
        }

        @Test
        @DisplayName("規則は、例外の一覧のクラスには当たらない")
        void ruleDoesNotMatchListedClass() {
            // 準備：時計の Bean の設定に見立てた例（規則の対象を 0 件にしないため、規則に合う例も加える）
            JavaClasses examples = new ClassFileImporter().importClasses(ExampleClockConfig.class, CallsNowWithClock.class);

            // 操作と確認：例を例外の一覧に入れた規則では、違反にならない
            assertThat(systemClockRule(ExampleClockConfig.class.getName()).evaluate(examples).hasViolation()).isFalse();
        }

        @Test
        @DisplayName("規則は、時計を受け取るクラスには当たらない")
        void ruleDoesNotMatchInjectedClock() {
            // 準備：注入した時計を渡して now を呼ぶ例
            JavaClasses examples = new ClassFileImporter().importClasses(CallsNowWithClock.class);

            // 操作と確認：違反にならない
            assertThat(systemClockRule(CLOCK_CONFIG).evaluate(examples).hasViolation()).isFalse();
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 時計を渡さずに現在時刻を取る例を返す。
     *
     * @return 例のクラス（取り方の名前を付ける）
     */
    static Stream<Named<Class<?>>> systemTimeExamples() {
        return Stream.of(
                Named.of("Instant.now()", CallsSystemNow.class),
                Named.of("LocalDate.now(ZoneId)", CallsNowWithZone.class),
                Named.of("Year.now()", CallsYearNow.class),
                Named.of("System.currentTimeMillis()", CallsCurrentTimeMillis.class),
                Named.of("new Date()", CreatesDate.class),
                Named.of("Calendar.getInstance()", CallsCalendarGetInstance.class));
    }

    /**
     * システムの時計を作る例を返す。
     *
     * @return 例のクラス（作り方の名前を付ける）
     */
    static Stream<Named<Class<?>>> systemClockExamples() {
        return Stream.of(
                Named.of("Clock.systemUTC()", CreatesSystemUtcClock.class),
                Named.of("Clock.systemDefaultZone()", CreatesDefaultZoneClock.class),
                Named.of("Clock.system(ZoneId)", CreatesSystemClock.class));
    }

    // ============================== 規則の補助 ==============================

    /**
     * 呼び出しの引数が、時計 1 つだけかを返す。
     *
     * @param call メソッドの呼び出し
     * @return 引数が {@link Clock} 1 つだけなら true
     */
    private static boolean takesOnlyClock(JavaMethodCall call) {
        var parameters = call.getTarget().getRawParameterTypes();
        return parameters.size() == 1 && parameters.getFirst().isEquivalentTo(Clock.class);
    }

    /**
     * 例外の一覧のクラスの外で、システムの時計を作らない規則を作る。
     *
     * @param clockConfig システムの時計を作ってよいクラスの名前（空なら、どのクラスも除かない）
     * @return 規則
     */
    private static ArchRule systemClockRule(String clockConfig) {
        return noClasses().that().doNotHaveFullyQualifiedName(clockConfig)
                .should().callMethod(Clock.class, "systemUTC")
                .orShould().callMethod(Clock.class, "systemDefaultZone")
                .orShould().callMethod(Clock.class, "system", ZoneId.class)
                .because("システムの時計は、時計の Bean の設定だけが作る（バックエンドの書き方の「現在時刻は Clock から」）");
    }
}
