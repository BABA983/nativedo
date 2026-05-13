package io.github.baba983.nativedo.core;

/**
 * General-purpose callback interfaces matching the public API surface of
 * {@code org/telegram/messenger/Utilities.Callback} family.
 *
 * <p>Skeleton — add variants ({@code Callback4}, {@code CallbackReturn}, etc.) as
 * ported code needs them.
 */
public final class Utilities {

    private Utilities() {
    }

    public interface Callback<T> {
        void run(T arg);
    }

    public interface Callback2<T1, T2> {
        void run(T1 arg1, T2 arg2);
    }

    public interface Callback3<T1, T2, T3> {
        void run(T1 arg1, T2 arg2, T3 arg3);
    }
}
