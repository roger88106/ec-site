package com.ecsite.architecture;

import com.ecsite.architecture.sample.testcode.WaitsForCondition;
import com.ecsite.architecture.sample.testcode.WaitsForTime;
import com.ecsite.support.ProductionClasses;
import com.ecsite.support.TestCodeClasses;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * テストの待ち方の検査
 *
 * <p>根拠はテストの書き方の「待ち方」。
 * 時間で待つテストは、遅い環境で失敗し、速い環境で時間を無駄にするので、条件で待つ。
 * 単体テストと統合テストは、スレッドを止める呼び出し（{@code Thread.sleep}、{@link TimeUnit} の {@code sleep}）と、
 * 決まった時間だけ見る・待ってから見る Awaitility の書き方（{@code during}、{@code pollDelay}）を使わない。
 */
class TimeWaitTest {

    // ============================== テスト ==============================

    @Test
    @DisplayName("単体テストと統合テストは、時間で待たない")
    void testsDoNotWaitForTime() {
        // 準備：調べるテストのクラスが 1 件以上ある
        ProductionClasses.requireNotEmpty(TestCodeClasses.all().stream().toList(), "テストのクラス");

        // 操作と確認：時間で待つ呼び出しがない
        assertThat(violations(TestCodeClasses.all())).isEmpty();
    }

    @Test
    @DisplayName("規則は、sleep と Awaitility の during・pollDelay に当たり、条件で待つ書き方には当たらない")
    void ruleMatchesOnlyTimeWaits() {
        // 準備：時間で待つ例と、条件で待つ例
        var examples = new ClassFileImporter().importClasses(WaitsForTime.class, WaitsForCondition.class);

        // 操作と確認：時間で待つ例の 4 つの呼び出しだけが違反になる
        assertThat(violations(examples)).containsExactly(
                WaitsForTime.class.getName() + " -> during",
                WaitsForTime.class.getName() + " -> pollDelay",
                WaitsForTime.class.getName() + " -> sleep",
                WaitsForTime.class.getName() + " -> sleep");
    }

    // ============================== 補助 ==============================

    /**
     * 時間で待つ呼び出しを返す。
     *
     * @param classes 調べるクラス
     * @return 呼び出す側の最上位のクラスの名前と、呼び出しの名前（名前の順。同じ呼び出しも数える）
     */
    private static List<String> violations(JavaClasses classes) {
        return classes.stream()
                .flatMap(javaClass -> javaClass.getMethodCallsFromSelf().stream())
                .filter(TimeWaitTest::waitsForTime)
                .map(call -> ProductionClasses.outermostName(call.getOriginOwner()) + " -> " + call.getName())
                .sorted()
                .toList();
    }

    /**
     * 時間で待つ呼び出しかを返す。
     *
     * @param call 呼び出し
     * @return {@code Thread.sleep}、{@link TimeUnit} の {@code sleep}、Awaitility の {@code during}・{@code pollDelay} なら true
     */
    private static boolean waitsForTime(JavaMethodCall call) {
        var owner = call.getTargetOwner();
        return switch (call.getName()) {
            case "sleep" -> owner.isEquivalentTo(Thread.class) || owner.isAssignableTo(TimeUnit.class);
            case "during", "pollDelay" -> owner.getPackageName().startsWith("org.awaitility");
            default -> false;
        };
    }
}
