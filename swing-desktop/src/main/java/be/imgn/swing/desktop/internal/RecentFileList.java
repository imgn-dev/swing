package be.imgn.swing.desktop.internal;

import be.imgn.toml.TomlConfig;
import java.util.List;

/**
 * The content of {@code recent-files.toml}: the recent files, most recent first.
 *
 * @param files the absolute paths of the files
 */
@TomlConfig
public record RecentFileList(List<String> files) {

    /**
     * Creates the list.
     *
     * @param files the absolute paths of the files, copied
     */
    public RecentFileList {
        files = List.copyOf(files);
    }
}
