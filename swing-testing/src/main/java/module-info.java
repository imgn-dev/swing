import be.imgn.swing.ui.internal.TesterExtension;

/** The test harness of swing applications. */
module be.imgn.swing.testing {
    requires transitive be.imgn.gaia.core.testing;
    requires be.imgn.gaia.memfs;
    requires transitive be.imgn.swing.ui;
    requires static transitive be.imgn.swing.desktop;
    requires static org.jspecify;

    exports be.imgn.swing.testing;

    uses TesterExtension;
}
