package org.msgpack.valueobjects;

import junit.framework.TestCase;
import org.junit.Test;
import org.msgpack.UnpackException;
import org.msgpack.UnpackerImpl;

import java.io.ByteArrayOutputStream;

public class TestValueObjects extends TestCase
{
    @Test
    public void testValueObjectImpl_1() throws UnpackException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        TestVOHelper mockVOHelper = new TestVOHelper();
        //              | VO        |HEADER     |typeID 253 |field (1)  |string[2]  |'v'        |'o'        |field (15) |bool false
        byte[] bytes = {(byte)0xD7, (byte)0x82, (byte)0xFD, (byte)0x01, (byte)0xA2, (byte)0x76, (byte)0x6F, (byte)0x80, (byte)0xC2};
        //              -41         -126        -3          1           -94         118         111         -128        -62
        //              [0]         [1]         [2]         [3]         [4]         [5]         [6]         [7]         [8]

        UnpackerImpl unpacker = new UnpackerImpl();
        unpacker.setVOHelper(mockVOHelper);
        unpacker.execute(bytes, 0, bytes.length);

        assertEquals("vo",  mockVOHelper.vo);
        assertEquals(false, mockVOHelper.bool);
    }

    @Test
    public void testValueObjectImpl_mixins_1() throws UnpackException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        TestVOHelper mockVOHelper = new TestVOHelper();
        //              | VO        | HEADER    |typeID 254 |HEADER     |typeID 253 |field (1)  |string[2]  |'v'        |'o'        |field (15) |bool false |HEADER     |type Sized |field (1)  | fixed int
        byte[] bytes = {(byte)0xD7, (byte)0x90, (byte)0xFE, (byte)0x82, (byte)0xFD, (byte)0x01, (byte)0xA2, (byte)0x76, (byte)0x6F, (byte)0x80, (byte)0xC2, (byte)0x81, (byte)0x01, (byte)0x01, (byte)0x2A};
        //              -41         -112        -2          -126        -3          1           -94         118         111         -128        -62         -127        1           1           42

        UnpackerImpl unpacker = new UnpackerImpl();
        unpacker.setVOHelper(mockVOHelper);
        unpacker.execute(bytes, 0, bytes.length);

        assertEquals("vo",  mockVOHelper.vo);
        assertEquals(false, mockVOHelper.bool);
        assertEquals(42, mockVOHelper.fourty2);
    }

    @Test
    public void testValueObjectImpl_mixins_and_empty_fields() throws UnpackException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        TestVOHelper mockVOHelper = new TestVOHelper();
        //              | VO        | HEADER    |typeID 254 |HEADER     |typeID 253 |no-fields  |field (15) |bool false |HEADER     |type Sized |field (1)  | fixed int
        byte[] bytes = {(byte)0xD7, (byte)0x90, (byte)0xFE, (byte)0x82, (byte)0xFD, (byte)0x00, (byte)0x80, (byte)0xC2, (byte)0x81, (byte)0x01, (byte)0x01, (byte)0x2A};
        //              -41         -112        -2          -126        -3          0           -128        -62         -127        1           1           42

        UnpackerImpl unpacker = new UnpackerImpl();
        unpacker.setVOHelper(mockVOHelper);
        unpacker.execute(bytes, 0, bytes.length);

        assertEquals(null,  mockVOHelper.vo);
        assertEquals(false, mockVOHelper.bool);
        assertEquals(42, mockVOHelper.fourty2);
    }
}
