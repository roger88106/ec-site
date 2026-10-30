package com.ecsite.architecture;

import com.ecsite.ProductionClasses;
import com.ecsite.architecture.cycle.domain.aggregate.OrderLike;
import com.ecsite.architecture.cycle.domain.aggregate.vo.AmountLike;
import com.ecsite.architecture.cycle.domain.oneway.OneWayOrder;
import com.ecsite.architecture.cycle.domain.oneway.vo.PlainAmount;
import com.ecsite.architecture.cycle.domain.withport.PortOwner;
import com.ecsite.architecture.cycle.domain.withport.port.OwnerPort;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * domain のパッケージの循環の検査（バックエンドの書き方の「標準の構成」）。<br />
 * domain の直下、集約のパッケージ、値のパッケージ（vo）を別々の slice とし、slice の間に循環がないことを確認する。
 */
class PackageCycleTest {

    /**
     * domain のパッケージ（domain の直下と、その下のパッケージ）の形
     */
    private static final Pattern DOMAIN_PACKAGE = Pattern.compile(".*\\.domain(\\..*)?");

    /**
     * 集約の port のパッケージの末尾。port は集約の一部（集約のクラスが port を使い、port が集約の型を返す）なので、集約と同じ slice にする
     */
    private static final Pattern PORT_SUFFIX = Pattern.compile("\\.port$");

    /**
     * domain の下のパッケージの間に循環がない規則
     */
    private static final ArchRule NO_CYCLES = SlicesRuleDefinition.slices()
            .assignedFrom(new ByDomainPackage()).should().beFreeOfCycles();

    @Test
    @DisplayName("モジュールの domain の下のパッケージの間に、循環がない")
    void domainPackagesAreFreeOfCycles() {
        ProductionClasses.requireNotEmpty(
                ProductionClasses.modules().stream()
                        .filter(javaClass -> DOMAIN_PACKAGE.matcher(javaClass.getPackageName()).matches())
                        .toList(),
                "モジュールの domain の下のクラス");

        NO_CYCLES.check(ProductionClasses.modules());
    }

    @Test
    @DisplayName("規則は、集約のパッケージと vo が互いに依存する例に当たる")
    void ruleMatchesCycleBetweenAggregateAndValuePackage() {
        JavaClasses examples = new ClassFileImporter().importClasses(OrderLike.class, AmountLike.class);

        List<String> details = NO_CYCLES.evaluate(examples).getFailureReport().getDetails();

        assertThat(details).isNotEmpty().allSatisfy(detail -> assertThat(detail).contains("Cycle detected"));
        assertThat(String.join("\n", details))
                .contains(OrderLike.class.getName())
                .contains(AmountLike.class.getName());
    }

    @Test
    @DisplayName("規則は、集約のパッケージから vo への一方向の依存には当たらない")
    void ruleDoesNotMatchOneWayDependency() {
        JavaClasses examples = new ClassFileImporter().importClasses(OneWayOrder.class, PlainAmount.class);

        assertThat(NO_CYCLES.evaluate(examples).hasViolation()).isFalse();
    }

    @Test
    @DisplayName("規則は、集約のパッケージと、その port が互いに依存する例には当たらない（port は集約と同じ slice）")
    void ruleDoesNotMatchAggregateAndItsPort() {
        JavaClasses examples = new ClassFileImporter().importClasses(PortOwner.class, OwnerPort.class);

        assertThat(NO_CYCLES.evaluate(examples).hasViolation()).isFalse();
    }

    /**
     * クラスのパッケージを slice の名前にする割り当て。domain の外のクラスは、どの slice にも入れない。<br />
     * domain の直下、集約のパッケージ（port を含む）、値のパッケージ（vo）が、それぞれ別の slice になる。
     */
    private static final class ByDomainPackage implements SliceAssignment {

        /**
         * 割り当ての説明を返す。
         *
         * @return 説明
         */
        @Override
        public String getDescription() {
            return "domain の下のパッケージ";
        }

        /**
         * クラスが入る slice を返す。
         *
         * @param javaClass クラス
         * @return domain の下のクラスはパッケージの名前の slice。それ以外は無視
         */
        @Override
        public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
            String packageName = javaClass.getPackageName();
            return DOMAIN_PACKAGE.matcher(packageName).matches()
                    ? SliceIdentifier.of(PORT_SUFFIX.matcher(packageName).replaceFirst(""))
                    : SliceIdentifier.ignore();
        }
    }
}
