package be.imgn.swing.internal;

import be.imgn.toml.TomlBinder;
import be.imgn.toml.TomlBindings;
import be.imgn.toml.TomlTable;
import be.imgn.toml.UnknownKeys;

/**
 * The binder and writer of {@link SampleSettings}, written by hand, as toml's processor would generate it, and
 * registered in the tests' {@code META-INF/services}, which the service loader reads on the class path.
 */
public final class SampleSettingsBinder implements TomlBinder<SampleSettings> {

    /** Creates the binder, as the service loader does. */
    public SampleSettingsBinder() {}

    @Override
    public Class<SampleSettings> type() {
        return SampleSettings.class;
    }

    @Override
    public SampleSettings bind(TomlTable table, UnknownKeys unknownKeys) {
        return new SampleSettings(
                TomlBindings.required(table, new TomlBindings.Context("", unknownKeys), "size", TomlBindings::asInt));
    }

    @Override
    public TomlTable toTable(SampleSettings value) {
        if (value.size() == SampleSettings.UNWRITABLE) {
            throw new IllegalStateException("The size " + SampleSettings.UNWRITABLE + " can't be written");
        }
        return TomlTable.builder().put("size", value.size()).build();
    }
}
