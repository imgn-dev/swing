package be.imgn.swing.docking;

import be.imgn.swing.ui.View;

/**
 * A view shown in its frame's {@link Dock}: a content, commands, a scope and resources, with no menu bar or toolbar of
 * its own. It is an added view of its frame, opened and closed while the frame is open. Its class's {@code title}
 * resource, else its simple name, titles its tab and header; its {@code icon} resource, a path relative to its package,
 * is their icon. The user may close it through its header's close button, which closes it as {@link Dock#close} does.
 */
public interface DockView extends View {}
