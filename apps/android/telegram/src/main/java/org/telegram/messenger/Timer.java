package org.telegram.messenger;

public class Timer {
    public static class Task {
    }

    public static Timer create(String name) {
        return new Timer();
    }

    public static Task start(Timer timer, String name) {
        return new Task();
    }

    public static void done(Task task) {
    }

    public static void log(Timer timer, String message) {
        FileLog.d(message);
    }

    public static void finish(Timer timer) {
    }
}
