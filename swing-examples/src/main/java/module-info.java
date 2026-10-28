/** The acceptance ports of milestone 1, which run on the module path. */
module be.imgn.swing.examples {
    requires be.imgn.swing.ui;
    requires static org.jspecify;

    opens be.imgn.swing.examples.counter;
}
