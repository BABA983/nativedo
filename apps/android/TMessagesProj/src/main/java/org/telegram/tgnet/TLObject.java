package org.telegram.tgnet;

/**
 * Minimal TLObject stub. Base class for all Telegram TL types.
 */
public class TLObject {
    public void readParams(InputSerializedData stream, boolean exception) {
    }

    public void serializeToStream(OutputSerializedData stream) {
    }

    public int getObjectSize() {
        return 0;
    }

    /** Pin helper (delegates to {@code me.vkryl.core.BitwiseUtils}, which this tree does not carry). */
    public static boolean hasFlag(int flags, int flag) {
        return (flags & flag) != 0;
    }

    /** Pin helper, inlined for the same reason as {@link #hasFlag(int, int)}. */
    public static int setFlag(int flags, int flag, boolean value) {
        return value ? (flags | flag) : (flags & ~flag);
    }
}
