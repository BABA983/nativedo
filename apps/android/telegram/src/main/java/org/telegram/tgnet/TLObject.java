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
}
