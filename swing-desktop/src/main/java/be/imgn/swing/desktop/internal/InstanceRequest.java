package be.imgn.swing.desktop.internal;

import be.imgn.toml.TomlConfig;
import java.util.List;

/**
 * The request a second instance sends to the first: the token, and the launch's arguments.
 *
 * @param token the token of {@code instance.toml}
 * @param arguments the arguments
 */
@TomlConfig
public record InstanceRequest(String token, List<String> arguments) {

    /**
     * Creates the request.
     *
     * @param token the token
     * @param arguments the arguments, copied
     */
    public InstanceRequest {
        arguments = List.copyOf(arguments);
    }
}
