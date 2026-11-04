package be.imgn.swing.examples.password;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.Check;
import be.imgn.swing.Check.Status;
import be.imgn.swing.Validation;
import be.imgn.swing.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The rules of a new password, tested without a form, as a command-line tool or a service would. */
class PasswordRulesTest {

    private static final PasswordMessages MESSAGES = new PasswordMessages() {
        @Override
        public String currentRequired() {
            return "current required";
        }

        @Override
        public String currentMatches() {
            return "current matches";
        }

        @Override
        public String minLength(int length) {
            return "length " + length;
        }

        @Override
        public String digit() {
            return "digit";
        }

        @Override
        public String confirmationMatches() {
            return "confirmation matches";
        }

        @Override
        public String changed() {
            return "changed";
        }
    };

    private static final Validator<String> RULES = PasswordRules.newPassword(MESSAGES);

    @Test
    void checksALengthOfEightCharactersThenADigit() {
        assertThat(RULES.validate("abc"))
                .isEqualTo(
                        new Validation(List.of(new Check("length 8", Status.UNMET), new Check("digit", Status.UNMET))));
    }

    @Test
    void aTextWithADigitMeetsTheDigitRuleWhateverItsLength() {
        assertThat(RULES.validate("abc1").checks()).extracting(Check::status).containsExactly(Status.UNMET, Status.MET);
    }

    @Test
    void eightCharactersWithADigitAreValid() {
        assertThat(RULES.validate("abcdefg1").valid()).isTrue();
        assertThat(RULES.validate("abcdefgh").error()).contains("digit");
    }
}
