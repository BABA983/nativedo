package io.github.baba983.nativedo.telegram.messenger;

/**
 * General-purpose callback interfaces and utility methods matching the public
 * API surface of {@code org/telegram/messenger/Utilities}.
 *
 * <p>Callback interfaces are added on demand as ported code needs them.
 * Utility methods delegate to standard Android/JDK implementations.
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

    public interface Callback4<T1, T2, T3, T4> {
        void run(T1 arg1, T2 arg2, T3 arg3, T4 arg4);
    }

    public interface Callback5<T1, T2, T3, T4, T5> {
        void run(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5);
    }

    public interface CallbackReturn<T, R> {
        R run(T arg);
    }

    public interface CallbackReturn2<T1, T2, R> {
        R run(T1 arg1, T2 arg2);
    }
}
