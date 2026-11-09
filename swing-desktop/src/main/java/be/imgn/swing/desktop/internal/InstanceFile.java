package be.imgn.swing.desktop.internal;

import be.imgn.toml.TomlConfig;

/**
 * The content of {@code instance.toml}: where the first instance listens, and the token a request must give.
 *
 * @param port the loopback port
 * @param token the token, 32 random bytes in Base64
 */
@TomlConfig
public record InstanceFile(int port, String token) {}
