package be.imgn.swing.ui;

import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * A column of a table bound to an observable list with {@link Ui#bind(javax.swing.JTable, be.imgn.swing.ObservableList,
 * java.util.List)}: its header, the type of its values and how a row gives its value.
 *
 * <pre>{@code
 * Column.of(messages.lastName(), String.class, Contact::lastName)
 *         .editable((contact, lastName) -> contact.withLastName(lastName))
 * }</pre>
 *
 * <p>The type is what the table model's {@code getColumnClass} returns, so the table's default renderers apply: check
 * boxes for {@code Boolean}, right-aligned numbers for {@code Number}. A {@code null} value shows an empty cell.
 *
 * @param <E> the type of the rows
 * @param <V> the type of the values
 */
public final class Column<E, V> {

    // The types the table's default editors edit.
    private static final Set<Class<?>> EDITABLE =
            Set.of(String.class, Boolean.class, Integer.class, Long.class, Double.class);

    // Read when the table is bound and at every refresh, so that a header from messages follows the locale.
    private final Supplier<String> header;
    private final Class<V> type;
    private final Function<? super E, ? extends @Nullable V> value;
    private final @Nullable BiFunction<? super E, ? super V, ? extends E> with;

    private Column(
            Supplier<String> header,
            Class<V> type,
            Function<? super E, ? extends @Nullable V> value,
            @Nullable BiFunction<? super E, ? super V, ? extends E> with) {
        this.header = header;
        this.type = type;
        this.value = value;
        this.with = with;
    }

    /**
     * Returns a column whose cells can't be edited.
     *
     * @param header the header text, from typed messages like any text that code holds
     * @param type the type of the values, which the model's {@code getColumnClass} returns
     * @param value gives the value of a row, {@code null} for an empty cell
     * @param <E> the type of the rows
     * @param <V> the type of the values
     * @return the column
     * @throws NullPointerException if an argument is {@code null}
     */
    public static <E, V> Column<E, V> of(
            String header, Class<V> type, Function<? super E, ? extends @Nullable V> value) {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
        return new Column<>(() -> header, type, value, null);
    }

    /**
     * Returns a column whose cells can't be edited, whose header is read when the table is bound and again at every
     * refresh, so that a header from typed messages follows the display locale:
     * {@code Column.of(messages::firstNameHeader, String.class, Contact::firstName)}.
     *
     * @param header gives the header text, on the EDT
     * @param type the type of the values, which the model's {@code getColumnClass} returns
     * @param value gives the value of a row, {@code null} for an empty cell
     * @param <E> the type of the rows
     * @param <V> the type of the values
     * @return the column
     * @throws NullPointerException if an argument is {@code null}
     */
    public static <E, V> Column<E, V> of(
            Supplier<String> header, Class<V> type, Function<? super E, ? extends @Nullable V> value) {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
        return new Column<>(header, type, value, null);
    }

    /**
     * Returns a new column whose cells can be edited, with the table's default editors. When an edit is committed, on
     * the EDT, the binding replaces the row with {@code rows.set(index, with.apply(row, value))}; the edit is dropped
     * if the list no longer holds the edited row at that index.
     *
     * @param with returns a copy of a row with a new value
     * @return the new column
     * @throws NullPointerException if {@code with} is {@code null}
     * @throws IllegalArgumentException if the type is not {@code String}, {@code Boolean}, {@code Integer},
     *     {@code Long} or {@code Double}, which the table's default editors edit
     */
    public Column<E, V> editable(BiFunction<? super E, ? super V, ? extends E> with) {
        Objects.requireNonNull(with, "with");
        if (!EDITABLE.contains(type)) {
            throw new IllegalArgumentException("The column " + header.get() + " of type " + type.getName()
                    + " can't be editable: the table's default editors edit String, Boolean, Integer, Long and Double");
        }
        return new Column<>(header, type, value, with);
    }

    String header() {
        return header.get();
    }

    Class<V> type() {
        return type;
    }

    @Nullable
    V value(E row) {
        return value.apply(row);
    }

    boolean isEditable() {
        return with != null;
    }

    // The row with a new value, of the column's type, as the column's function returns it, null included: the list
    // rejects a null row. Only an editable column has one.
    E with(E row, V newValue) {
        BiFunction<? super E, ? super V, ? extends E> function = with;
        if (function == null) {
            throw new IllegalStateException("The column " + header.get() + " is not editable");
        }
        return function.apply(row, newValue);
    }
}
