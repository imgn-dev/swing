package be.imgn.swing;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an interface of typed messages. Each method returns {@code String} and is a key of the interface's TOML
 * files, named after the interface ({@code CounterMessages.toml}, {@code CounterMessages_fr.toml}), in its package. The
 * placeholders of the patterns name the method's parameters, as {@code {count}}; the processor checks them against
 * every locale file and generates the implementation, a singleton bean.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Messages {}
