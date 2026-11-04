package be.imgn.swing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import be.imgn.swing.Check.Status;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ValidationTest {

    private static final Rule<String> MIN_LENGTH = Rule.of("At least 8 characters", s -> s.length() >= 8);
    private static final Rule<CharSequence> DIGIT =
            Rule.of("A digit", s -> s.chars().anyMatch(Character::isDigit));

    @Test
    void makesARuleOfARequirementAndATest() {
        assertThat(MIN_LENGTH.requirement()).isEqualTo("At least 8 characters");
        assertThat(MIN_LENGTH.test("abcdefgh")).isTrue();
        assertThat(MIN_LENGTH.test("abc")).isFalse();
        assertThatNullPointerException().isThrownBy(() -> Rule.of(nothing(), s -> true));
        assertThatNullPointerException().isThrownBy(() -> Rule.of("Required", noTest()));
    }

    @Test
    void rejectsANullRule() {
        assertThatNullPointerException().isThrownBy(() -> Validator.of(MIN_LENGTH, noRule()));
    }

    @Test
    void requiresATextThatIsNotBlank() {
        var required = Rule.required("Required");

        assertThat(required.requirement()).isEqualTo("Required");
        assertThat(required.test("a")).isTrue();
        assertThat(required.test(" a ")).isTrue();
        assertThat(required.test("")).isFalse();
        assertThat(required.test(" \t\n")).isFalse();
    }

    @Test
    void givesOneCheckPerRuleInOrder() {
        Validator<String> validator = Validator.of(MIN_LENGTH, DIGIT);

        assertThat(validator.validate("abc").checks())
                .containsExactly(new Check("At least 8 characters", Status.UNMET), new Check("A digit", Status.UNMET));
        assertThat(validator.validate("abc1").checks())
                .containsExactly(new Check("At least 8 characters", Status.UNMET), new Check("A digit", Status.MET));
        assertThat(validator.validate("abcdefg1").checks())
                .containsExactly(new Check("At least 8 characters", Status.MET), new Check("A digit", Status.MET));
    }

    @Test
    void testsEveryRuleWhetherOrNotAnEarlierOneFailed() {
        var tested = new ArrayList<String>();
        Validator<String> validator = Validator.of(failing("first", tested), failing("second", tested));

        validator.validate("value");

        assertThat(tested).containsExactly("first", "second");
    }

    @Test
    void copiesTheRulesOfAValidator() {
        var rules = new ArrayList<Rule<? super String>>(List.of(MIN_LENGTH));
        var validator = new Validator<>(rules);

        rules.add(DIGIT);

        assertThat(validator.rules()).containsExactly(MIN_LENGTH);
        assertThat(validator.validate("abc").checks()).hasSize(1);
    }

    @Test
    void copiesTheChecksOfAValidation() {
        var checks = new ArrayList<>(List.of(new Check("a", Status.MET)));
        var validation = new Validation(checks);

        checks.add(new Check("b", Status.UNMET));

        assertThat(validation.checks()).containsExactly(new Check("a", Status.MET));
        assertThat(validation.valid()).isTrue();
    }

    @Test
    void isValidWhenEveryCheckIsMet() {
        assertThat(new Validation(List.of()).valid()).isTrue();
        assertThat(new Validation(List.of(new Check("a", Status.MET), new Check("b", Status.MET))).valid())
                .isTrue();
        assertThat(new Validation(List.of(new Check("a", Status.MET), new Check("b", Status.UNMET))).valid())
                .isFalse();
        assertThat(new Validation(List.of(new Check("a", Status.UNKNOWN))).valid())
                .isFalse();
    }

    @Test
    void givesTheRequirementOfTheFirstCheckNotMetAsItsError() {
        var validation = new Validation(
                List.of(new Check("a", Status.MET), new Check("b", Status.UNKNOWN), new Check("c", Status.UNMET)));

        assertThat(validation.error()).contains("b");
        assertThat(new Validation(List.of(new Check("a", Status.MET))).error()).isEqualTo(Optional.empty());
    }

    private static Rule<String> failing(String requirement, List<String> tested) {
        return Rule.of(requirement, s -> {
            tested.add(requirement);
            return false;
        });
    }

    // Through lists, which the compiler and NullAway don't check, to reach the rules' and validators' own checks.
    private static String nothing() {
        return Arrays.asList((String) null).get(0);
    }

    private static Predicate<String> noTest() {
        return Collections.<Predicate<String>>singletonList(null).get(0);
    }

    private static Rule<String> noRule() {
        return Collections.<Rule<String>>singletonList(null).get(0);
    }
}
