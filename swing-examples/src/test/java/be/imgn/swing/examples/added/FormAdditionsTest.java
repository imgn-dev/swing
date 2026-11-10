package be.imgn.swing.examples.added;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.testing.AppTester;
import be.imgn.swing.ui.Form;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The form's validation of a part of it, and the code that reveals a component before the form focuses it. */
class FormAdditionsTest {

    @Test
    void validatesTheBindingsWithinAComponentOnly() {
        try (AppTester<CardsApp> app = AppTester.start(CardsApp.class)) {
            CardsApp cards = app.application();
            Form form = cards.form();

            assertThat(app.call(() -> form.validate(cards.card(0)))).isFalse();

            assertThat(app.call(() -> Optional.ofNullable(cards.firstField().getToolTipText())))
                    .contains("A first value is required");
            assertThat(app.call(() -> Optional.ofNullable(cards.secondField().getToolTipText())))
                    .isEmpty();
            assertThat(app.focused()).containsSame(cards.firstField());
        }
    }

    @Test
    void reportsTheBindingsWithinAComponentValidOnceTheyAre() {
        try (AppTester<CardsApp> app = AppTester.start(CardsApp.class)) {
            CardsApp cards = app.application();
            app.run(() -> cards.firstField().setText("one"));

            assertThat(app.call(() -> cards.form().validate(cards.card(0)))).isTrue();
            assertThat(app.call(() -> cards.form().valid().get())).isFalse();
        }
    }

    @Test
    void reportsAComponentWithNoBindingValid() {
        try (AppTester<CardsApp> app = AppTester.start(CardsApp.class)) {
            CardsApp cards = app.application();

            assertThat(app.call(() -> cards.form().validate(cards.card(2)))).isTrue();
            assertThat(app.call(() -> Optional.ofNullable(cards.firstField().getToolTipText())))
                    .isEmpty();
        }
    }

    @Test
    void focusesAComponentOnAHiddenCardOnceTheRevealCodeShowsIt() {
        try (AppTester<CardsApp> app = AppTester.start(CardsApp.class)) {
            CardsApp cards = app.application();
            List<Component> revealed = new ArrayList<>();
            app.run(() -> {
                cards.firstField().setText("one");
                cards.form().revealWith(revealed::add);
                cards.form().revealWith(component -> cards.show("second"));
            });

            assertThat(app.call(() -> cards.form().validate())).isFalse();

            assertThat(revealed).containsExactly(cards.secondField());
            assertThat(app.focused()).containsSame(cards.secondField());
        }
    }

    @Test
    void leavesAComponentOnAHiddenCardUnfocusedWithoutRevealCode() {
        try (AppTester<CardsApp> app = AppTester.start(CardsApp.class)) {
            CardsApp cards = app.application();
            app.run(() -> cards.firstField().setText("one"));

            assertThat(app.call(() -> cards.form().validate(cards.card(1)))).isFalse();

            assertThat(app.focused()).isEmpty();
        }
    }
}
