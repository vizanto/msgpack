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
    public String vo;
    public boolean bool = true;
    public int fourty2 = 0;

    public UnpackerImpl.VOInstance newObject() {
        return new UnpackerImpl.VOInstance() {
            int fields = 0;
            int mixins = 0;
            public void prepareForType(int typeID) {
                return;
            }

            @Override public void incrementMixinCount(int mixins) {
                this.mixins += mixins;
            }

            @Override public void prepareForNext8Fields(byte flags) {
                fields = 0;
                for (int i = 0; i < 8; ++i) {
                    int bit = (flags & (1 << i));
                    if ((bit & 0xFF) != 0) ++fields;
                }
                return;
            }

            @Override public void putValue(Object value) {
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

            @Override public boolean fieldgroupRequiresMoreValues() {
                return fields != 0;
            }

            @Override public boolean mixinDataRemains() {
                return mixins != 0;
            }

            @Override public void nextMixin() {
                --mixins;
            }

            @Override public MessagePackObject getData() {
                return null;
            }
        };
    }
}
