package be.imgn.swing.internal;

import be.imgn.swing.Resources;
import be.imgn.swing.spi.MessagePattern;
import be.imgn.swing.spi.MessageSource;
import be.imgn.swing.spi.MessagesDescriptor;
import java.util.List;
import java.util.Locale;

/** The descriptor and the implementation of {@link Greetings}, as swing's processor generates them. */
public final class GreetingsDescriptor implements MessagesDescriptor {

    /** Creates the descriptor; the service loader does. */
    public GreetingsDescriptor() {}

    @Override
    public Class<?> type() {
        return Greetings.class;
    }

    @Override
    public Object create(Resources resources, Locale locale) {
        MessageSource<MessagePattern> source = MessageSource.fixed(
                resources,
                Greetings.class,
                locale,
                (table, read) -> MessagePattern.of(table, "hello", List.of("name"), read));
        return (Greetings) name -> source.get().format(name);
    }
}
