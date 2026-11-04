package be.imgn.swing.ui;

/** The single selection of a combo box, in its {@link FeedComboModel}, whose indices are those of the copy. */
final class ComboSelectionView implements SelectionView {

    private final FeedComboModel<?> model;

    /**
     * Creates the view of a combo box's model.
     *
     * @param model the model
     */
    ComboSelectionView(FeedComboModel<?> model) {
        this.model = model;
    }

    /**
     * Tells a sync of the user's changes of the selection, through a form's binding, which handles each change that was
     * the user's once the sync has set the property.
     *
     * @param sync the sync
     * @param binding the binding
     */
    void follow(SelectionSync<?> sync, Binding<?> binding) {
        model.follow(() -> binding.userChanged(sync::userChanged));
    }

    @Override
    public int selectedIndex() {
        return model.selectedIndex();
    }

    @Override
    public void select(int index) {
        model.select(index);
    }

    // A combo box shows its selected item in its display area: its popup list scrolls to it whenever it opens.
    @Override
    public void scrollTo(int index) {}

    @Override
    public void clear() {
        model.select(-1);
    }
}
