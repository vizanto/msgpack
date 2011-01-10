package org.msgpack.valueobjects;

import org.msgpack.MessagePackObject;
import org.msgpack.UnpackerImpl;
import org.msgpack.object.BooleanType;
import org.msgpack.object.IntegerType;
import org.msgpack.object.RawType;

/**
 * Created by IntelliJ IDEA.
 * User: blue
 * Date: 08-01-11
 * Time: 02:02
 * To change this template use File | Settings | File Templates.
 */
class TestVOHelper implements UnpackerImpl.VOHelper
{
    public int valuesPut = 0;
    public String vo;
    public boolean bool = true;
    public int fourty2 = 0;

    public UnpackerImpl.VOInstance newObject() {
        return new UnpackerImpl.VOInstance() {
            int fields = 0;

            // Custom value-type API
            protected int processValueType(int typeID) {
                return 0;
            }
            protected void putValue(byte[] bytes, int start) {
            }

            protected void prepareValueObject(int typeID) {
                return;
            }

            protected void prepareForNext8Fields(byte flags) {
                fields = 0;
                for (int i = 0; i < 8; ++i) {
                    int bit = (flags & (1 << i));
                    if ((bit & 0xFF) != 0) ++fields;
                }
                return;
            }

            protected void putValue(Object value)
            {
                ++valuesPut;
                if (value instanceof RawType) {
                    vo = ((RawType) value).asString();
                }
                else if (value instanceof BooleanType) {
                    bool = ((BooleanType) value).asBoolean();
                }
                else if (value instanceof IntegerType) {
                    fourty2 = ((IntegerType) value).asInt();
                }
                --fields;
            }



            protected boolean fieldgroupRequiresMoreValues() {
                return fields != 0;
            }

            public MessagePackObject getData() {
                return null;
            }
        };
    }
}
