package be.imgn.swing.examples.counter;

/**
 * A count announced on the application's bus, which the counter's subscribers receive.
 *
 * @param count the count
 */
record Counted(int count) {}
