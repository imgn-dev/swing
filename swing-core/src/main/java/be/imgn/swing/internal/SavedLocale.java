package be.imgn.swing.internal;

import be.imgn.toml.TomlConfig;

/**
 * The content of {@code locale.toml}: the display locale the user chose.
 *
 * @param locale the locale's BCP 47 language tag, such as {@code fr-BE}
 */
@TomlConfig
public record SavedLocale(String locale) {}
