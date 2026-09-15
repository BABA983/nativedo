package org.telegram.ui.Components;

import java.nio.ByteBuffer;

public class CurvesToolValue {
    public ByteBuffer curveBuffer;
    
    public boolean shouldBeSkipped() {
        return true;
    }
    
    public void fillBuffer() {
        curveBuffer = ByteBuffer.allocateDirect(256 * 4);
    }
    
    public void serializeToStream(org.telegram.tgnet.OutputSerializedData stream) {
    }
    
    public void readParams(org.telegram.tgnet.InputSerializedData stream, boolean exception) {
    }
}
