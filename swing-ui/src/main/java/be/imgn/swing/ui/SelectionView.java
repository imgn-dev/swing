package be.imgn.swing.ui;

/**
 * The single selection of a component that shows a feed's copy, by index in the copy, as a {@link SelectionSync} reads
 * and changes it. The component's own listener tells the sync of the user's changes with
 * {@link SelectionSync#userChanged()}.
 */
interface SelectionView {

    /**
     * Returns the selected index.
     *
     * @return the index, -1 when nothing is selected
     */
    int selectedIndex();

    /**
     * Selects an element.
     *
     * @param index its index in the copy
     */
    void select(int index);

    /**
     * Scrolls the component so that an element is visible.
     *
     * @param index its index in the copy
     */
    void scrollTo(int index);

    /** Clears the selection. */
    void clear();
}
