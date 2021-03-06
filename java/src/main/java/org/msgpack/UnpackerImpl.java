//
// MessagePack for Java
//
// Copyright (C) 2009-2010 FURUHASHI Sadayuki
//
//    Licensed under the Apache License, Version 2.0 (the "License");
//    you may not use this file except in compliance with the License.
//    You may obtain a copy of the License at
//
//        http://www.apache.org/licenses/LICENSE-2.0
//
//    Unless required by applicable law or agreed to in writing, software
//    distributed under the License is distributed on an "AS IS" BASIS,
//    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//    See the License for the specific language governing permissions and
//    limitations under the License.
//
package org.msgpack;

import java.nio.ByteBuffer;
import java.math.BigInteger;
import org.msgpack.object.*;

public class UnpackerImpl {
	static final int CS_HEADER      = 0x00;
	static final int CS_FLOAT       = 0x0a;
	static final int CS_DOUBLE      = 0x0b;
	static final int CS_UINT_8      = 0x0c;
	static final int CS_UINT_16     = 0x0d;
	static final int CS_UINT_32     = 0x0e;
	static final int CS_UINT_64     = 0x0f;
	static final int CS_INT_8       = 0x10;
	static final int CS_INT_16      = 0x11;
	static final int CS_INT_32      = 0x12;
	static final int CS_INT_64      = 0x13;
	static final int CS_RAW_16      = 0x1a;
	static final int CS_RAW_32      = 0x1b;
	static final int CS_ARRAY_16    = 0x1c;
	static final int CS_ARRAY_32    = 0x1d;
	static final int CS_MAP_16      = 0x1e;
	static final int CS_MAP_32      = 0x1f;
	static final int ACS_RAW_VALUE  = 0x20;
    static final int CS_VO_HEADER   = 0x21;
    static final int CS_VO_FIELDS   = 0x22;
	static final int CT_ARRAY_ITEM  = 0x00;
	static final int CT_MAP_KEY     = 0x01;
	static final int CT_MAP_VALUE   = 0x02;
    static final int CT_VO_VALUES   = 0x03;

	static final int MAX_STACK_SIZE = 32;

	private int cs;
	private int trail;
	private int top;
	private int[]    stack_ct       = new int[MAX_STACK_SIZE];
	private int[]    stack_count    = new int[MAX_STACK_SIZE];
	private Object[] stack_obj      = new Object[MAX_STACK_SIZE];
	private int top_ct;
	private int top_count;
	private Object top_obj;
	private ByteBuffer castBuffer   = ByteBuffer.allocate(8);
	private boolean finished = false;
	private MessagePackObject data = null;
    private VOHelper voHelper = null;

    public interface VOHelper {
        VOInstance newObject();
    }

    static public abstract class VOInstance
    {
        int mixins = 0;
        final boolean mixinDataRemains() {
            return mixins != 0;
        }
        final void nextMixin() {
            --mixins;
        }
        final void incrementMixinCount(int mixins) {
            this.mixins += mixins;
        }

        // Custom value-type API
        abstract protected int     processValueType(int typeID);
        abstract protected void    putValue(byte[] bytes, int start);

        // ValueObject builder API
        abstract protected void    prepareValueObject(int typeID);
        abstract protected void    prepareForNext8Fields(byte flags);
        abstract protected void    putValue(Object value);
        abstract protected boolean fieldgroupRequiresMoreValues();

        // Resulting object
        abstract public MessagePackObject getData();
    }

	public UnpackerImpl()
	{
		reset();
	}

    public final void setVOHelper(VOHelper voHelper)
    {
        this.voHelper = voHelper;
    }

	public final MessagePackObject getData()
	{
		return data;
	}

	public final boolean isFinished()
	{
		return finished;
	}

	public final void resetState() {
		cs = CS_HEADER;
		top = -1;
		top_ct = 0;
		top_count = 0;
		top_obj = null;
	}

	public final void reset()
	{
		resetState();
		finished = false;
		data = null;
	}

	@SuppressWarnings("unchecked")
	public final int execute(byte[] src, int off, int length) throws UnpackException
	{
		if(off >= length) { return off; }

		int limit = length;
		int i = off;
		int count;

		Object obj = null;

		_out: do {
		_header_again_without_cs_reset: { _header_again: {
			//System.out.println("while i:"+i+" limit:"+limit);

			int b = src[i];

			_push: {
				_fixed_trail_again:
				if(cs == CS_HEADER) {
	
					if((b & 0x80) == 0) {  // Positive Fixnum
						//System.out.println("positive fixnum "+b);
						obj = IntegerType.create((byte)b);
						break _push;
					}
	
					if((b & 0xe0) == 0xe0) {  // Negative Fixnum
						//System.out.println("negative fixnum "+b);
						obj = IntegerType.create((byte)b);
						break _push;
					}
	
					if((b & 0xe0) == 0xa0) {  // FixRaw
						trail = b & 0x1f;
						if(trail == 0) {
							obj = RawType.create(new byte[0]);
							break _push;
						}
						cs = ACS_RAW_VALUE;
						break _fixed_trail_again;
					}
	
					if((b & 0xf0) == 0x90) {  // FixArray
						if(top >= MAX_STACK_SIZE) {
							throw new UnpackException("parse error");
						}
						count = b & 0x0f;
						//System.out.println("fixarray count:"+count);
						obj = new MessagePackObject[count];
						if(count == 0) {
							obj = ArrayType.create((MessagePackObject[])obj);
							break _push;
						}
						++top;
						stack_obj[top]    = top_obj;
						stack_ct[top]     = top_ct;
						stack_count[top]  = top_count;
						top_obj    = obj;
						top_ct     = CT_ARRAY_ITEM;
						top_count  = count;
						break _header_again;
					}
	
					if((b & 0xf0) == 0x80) {  // FixMap
						if(top >= MAX_STACK_SIZE) {
							throw new UnpackException("parse error");
						}
						count = b & 0x0f;
						obj = new MessagePackObject[count*2];
						if(count == 0) {
							obj = MapType.create((MessagePackObject[])obj);
							break _push;
						}
						//System.out.println("fixmap count:"+count);
						++top;
						stack_obj[top]    = top_obj;
						stack_ct[top]     = top_ct;
						stack_count[top]  = top_count;
						top_obj    = obj;
						top_ct     = CT_MAP_KEY;
						top_count  = count;
						break _header_again;
					}
	
					switch(b & 0xff) {    // FIXME
					case 0xc0:  // nil
						obj = NilType.create();
						break _push;
					case 0xc2:  // false
						obj = BooleanType.create(false);
						break _push;
					case 0xc3:  // true
						obj = BooleanType.create(true);
						break _push;
					case 0xca:  // float
					case 0xcb:  // double
					case 0xcc:  // unsigned int  8
					case 0xcd:  // unsigned int 16
					case 0xce:  // unsigned int 32
					case 0xcf:  // unsigned int 64
					case 0xd0:  // signed int  8
					case 0xd1:  // signed int 16
					case 0xd2:  // signed int 32
					case 0xd3:  // signed int 64
						trail = 1 << (b & 0x03);
						cs = b & 0x1f;
						//System.out.println("a trail "+trail+"  cs:"+cs);
						break _fixed_trail_again;
					case 0xda:  // raw 16
					case 0xdb:  // raw 32
					case 0xdc:  // array 16
					case 0xdd:  // array 32
					case 0xde:  // map 16
					case 0xdf:  // map 32
						trail = 2 << (b & 0x01);
						cs = b & 0x1f;
						//System.out.println("b trail "+trail+"  cs:"+cs);
						break _fixed_trail_again;
                    case 0xd7:  // ValueObject
                        //System.out.println(top + " valueobject:start     | (push top)");
                        ++top;
                        stack_obj[top]    = top_obj;
                        stack_ct[top]     = top_ct;
                        stack_count[top]  = top_count;
                        top_obj    = voHelper.newObject();
                        top_ct     = -1;
                        top_count  = -1;

                        trail = 3; // of 2? header + typeID (max 2 bytes)
                        cs = CS_VO_HEADER;
                        break _fixed_trail_again;
					default:
						//System.out.println("unknown b "+(b&0xff));
						throw new UnpackException("parse error");
					}
	
				}  // _fixed_trail_again

				do {
				_fixed_trail_again: {
	
					if(limit - i <= trail) { break _out; }
					int n = i + 1;
					i += trail;

					switch(cs) {
					case CS_FLOAT:
						castBuffer.rewind();
						castBuffer.put(src, n, 4);
						obj = FloatType.create( castBuffer.getFloat(0) );
						//System.out.println("float "+obj);
						break _push;
					case CS_DOUBLE:
						castBuffer.rewind();
						castBuffer.put(src, n, 8);
						obj = FloatType.create( castBuffer.getDouble(0) );
						//System.out.println("double "+obj);
						break _push;
					case CS_UINT_8:
						//System.out.println(n);
						//System.out.println(src[n]);
						//System.out.println(src[n+1]);
						//System.out.println(src[n-1]);
						obj = IntegerType.create( (short)((src[n]) & 0xff) );
						//System.out.println("uint8 "+obj);
						break _push;
					case CS_UINT_16:
						//System.out.println(src[n]);
						//System.out.println(src[n+1]);
						castBuffer.rewind();
						castBuffer.put(src, n, 2);
						obj = IntegerType.create( ((int)castBuffer.getShort(0)) & 0xffff );
						//System.out.println("uint 16 "+obj);
						break _push;
					case CS_UINT_32:
						castBuffer.rewind();
						castBuffer.put(src, n, 4);
						obj = IntegerType.create( ((long)castBuffer.getInt(0)) & 0xffffffffL );
						//System.out.println("uint 32 "+obj);
						break _push;
					case CS_UINT_64:
						castBuffer.rewind();
						castBuffer.put(src, n, 8);
						{
							long o = castBuffer.getLong(0);
							if(o < 0) {
								obj = IntegerType.create(new BigInteger(1, castBuffer.array()));
							} else {
								obj = IntegerType.create(o);
							}
						}
						break _push;
					case CS_INT_8:
						obj = IntegerType.create( src[n] );
						break _push;
					case CS_INT_16:
						castBuffer.rewind();
						castBuffer.put(src, n, 2);
						obj = IntegerType.create( castBuffer.getShort(0) );
						break _push;
					case CS_INT_32:
						castBuffer.rewind();
						castBuffer.put(src, n, 4);
						obj = IntegerType.create( castBuffer.getInt(0) );
						break _push;
					case CS_INT_64:
						castBuffer.rewind();
						castBuffer.put(src, n, 8);
						obj = IntegerType.create( castBuffer.getLong(0) );
						break _push;
					case CS_RAW_16:
						castBuffer.rewind();
						castBuffer.put(src, n, 2);
						trail = ((int)castBuffer.getShort(0)) & 0xffff;
						if(trail == 0) {
							obj = RawType.create(new byte[0]);
							break _push;
						}
						cs = ACS_RAW_VALUE;
						break _fixed_trail_again;
					case CS_RAW_32:
						castBuffer.rewind();
						castBuffer.put(src, n, 4);
						// FIXME overflow check
						trail = castBuffer.getInt(0) & 0x7fffffff;
						if(trail == 0) {
							obj = RawType.create(new byte[0]);
							break _push;
						}
						cs = ACS_RAW_VALUE;
						break _fixed_trail_again;
					case ACS_RAW_VALUE: {
							// TODO zero-copy buffer
							byte[] raw = new byte[trail];
							System.arraycopy(src, n, raw, 0, trail);
							obj = RawType.create(raw);
						}
						break _push;
					case CS_ARRAY_16:
						if(top >= MAX_STACK_SIZE) {
							throw new UnpackException("parse error");
						}
						castBuffer.rewind();
						castBuffer.put(src, n, 2);
						count = ((int)castBuffer.getShort(0)) & 0xffff;
						obj = new MessagePackObject[count];
						if(count == 0) {
							obj = ArrayType.create((MessagePackObject[])obj);
							break _push;
						}
						++top;
						stack_obj[top]    = top_obj;
						stack_ct[top]     = top_ct;
						stack_count[top]  = top_count;
						top_obj    = obj;
						top_ct     = CT_ARRAY_ITEM;
						top_count  = count;
						break _header_again;
					case CS_ARRAY_32:
						if(top >= MAX_STACK_SIZE) {
							throw new UnpackException("parse error");
						}
						castBuffer.rewind();
						castBuffer.put(src, n, 4);
						// FIXME overflow check
						count = castBuffer.getInt(0) & 0x7fffffff;
						obj = new MessagePackObject[count];
						if(count == 0) {
							obj = ArrayType.create((MessagePackObject[])obj);
							break _push;
						}
						++top;
						stack_obj[top]    = top_obj;
						stack_ct[top]     = top_ct;
						stack_count[top]  = top_count;
						top_obj    = obj;
						top_ct     = CT_ARRAY_ITEM;
						top_count  = count;
						break _header_again;
					case CS_MAP_16:
						if(top >= MAX_STACK_SIZE) {
							throw new UnpackException("parse error");
						}
						castBuffer.rewind();
						castBuffer.put(src, n, 2);
						count = ((int)castBuffer.getShort(0)) & 0xffff;
						obj = new MessagePackObject[count*2];
						if(count == 0) {
							obj = MapType.create((MessagePackObject[])obj);
							break _push;
						}
						//System.out.println("fixmap count:"+count);
						++top;
						stack_obj[top]    = top_obj;
						stack_ct[top]     = top_ct;
						stack_count[top]  = top_count;
						top_obj    = obj;
						top_ct     = CT_MAP_KEY;
						top_count  = count;
						break _header_again;
					case CS_MAP_32:
						if(top >= MAX_STACK_SIZE) {
							throw new UnpackException("parse error");
						}
						castBuffer.rewind();
						castBuffer.put(src, n, 4);
						// FIXME overflow check
						count = castBuffer.getInt(0) & 0x7fffffff;
						obj = new MessagePackObject[count*2];
						if(count == 0) {
							obj = MapType.create((MessagePackObject[])obj);
							break _push;
						}
						//System.out.println("fixmap count:"+count);
						++top;
						stack_obj[top]    = top_obj;
						stack_ct[top]     = top_ct;
						stack_count[top]  = top_count;
						top_obj    = obj;
						top_ct     = CT_MAP_KEY;
						top_count  = count;
						break _header_again;

                    case CS_VO_HEADER: {
                        VOInstance vo = (VOInstance) top_obj;
                        int header = src[n];

                        if (header >= 0) // first (sign) bit not set
                        {
                            // Custom value type (not a value-object)
                            count = vo.processValueType(header);
					        if(limit - n <= count) {
                                trail = count + 1;
                                i = n - 1;
                                break _out; // try again later when sufficient data is available
                            }
                            i = n + count;
                            vo.putValue(src, n);
                            //System.out.println(top + "   \\_ -> pop stack     | custom value done");
                            top_obj    = stack_obj[top];
                            top_ct     = stack_ct[top];
                            top_count  = stack_count[top];
                            stack_obj[top] = null;
                            --top;
                            obj = vo.getData();
                            break _push;
                        }

                        count = header & 0x07; // property byte-flags count

                        int typeID;
                        if (0 == (header & 0x40)) { // single byte typeID
                            --i; // go back one, as trail was guessed as 3
                            typeID = ((src[n+1]) & 0xff);
                        } else {
                            castBuffer.rewind();
                            castBuffer.put(src, n+1, 2);
                            typeID = ((int)castBuffer.getShort(0)) & 0xffff;
                        }
                        /*System.out.println(top + " valueobject:header    | firstbyte = "+ header +
                          ", mixins = "+ mixinCount +
                          ", property-sets = "+ count +
                          ", typeID = "+ typeID);
                        */
                        vo.prepareValueObject(typeID);
                        vo.incrementMixinCount((header & 0x38 /* 0b_0011_1000 */) >>> 3); // kan dus groter dan 7 worden (!) - dataRemains is true zolang mixinCount > 0

                        if ((top_count = count) > 0) {
                            trail = 1;
                            cs = CS_VO_FIELDS;
                            //System.out.println(top + "   \\_ CS_VO_FIELDS     | count = " + count);
                            break _fixed_trail_again;
                        }
                        // else if
                        if (vo.mixinDataRemains()) {
                            vo.nextMixin();
                            //System.out.println(top + "   \\_ -> mixin         | count = " + count + " trail = "+trail);
                            trail = 3; //bytes: 1 header, 2 type id (of 1 type id + iets aan data)
                            break _fixed_trail_again;
                        }
                        // else, object complete
                        //System.out.println(top + "   \\_ -> pop stack     | vo done");
                        top_obj    = stack_obj[top];
                        top_ct     = stack_ct[top];
                        top_count  = stack_count[top];
                        stack_obj[top] = null;
                        --top;
                        obj = vo.getData();
                        break _push;
                    }
                    case CS_VO_FIELDS: {
                        //System.out.println(top + " valueobject:fields");
                        // read 1 byte (field-group of max 8 values)
                        VOInstance vo = (VOInstance) top_obj;
                        vo.prepareForNext8Fields(src[i]);
                        if (src[i] != 0) {// && vo.fieldgroupRequiresMoreValues()) {
                            top_ct = CT_VO_VALUES;
                            break _header_again;
                        }
                        else if (--top_count == 0) // if 0: No more property groups for this type
                        {
                            //System.out.println(top + "   \\_ -> no more field groups");
                            if (!vo.mixinDataRemains()) {
                                // else, object complete
                                //System.out.println(top + "   \\_ -> pop stack     | vo done");
                                top_obj    = stack_obj[top];
                                top_ct     = stack_ct[top];
                                top_count  = stack_count[top];
                                stack_obj[top] = null;
                                --top;
                                obj = vo.getData();
                                break _push;
                            }
                            // else

                            vo.nextMixin();
                            trail = 3;
                            cs = CS_VO_HEADER; // process next mixin
                        }
                        break _fixed_trail_again;
                    }

					default:
						throw new UnpackException("parse error");
					}

				}  // _fixed_trail_again
				}  while(true);
			}  // _push

			do {
			_push: {
				//System.out.println("push top:"+top);
				if(top == -1) {
					++i;
					data = (MessagePackObject)obj;
					finished = true;
					break _out;
				}

				switch(top_ct) {
				case CT_ARRAY_ITEM: {
						//System.out.println("array item "+obj);
						Object[] ar = (Object[])top_obj;
						ar[ar.length - top_count] = obj;
						if(--top_count == 0) {
							top_obj    = stack_obj[top];
							top_ct     = stack_ct[top];
							top_count  = stack_count[top];
							obj = ArrayType.create((MessagePackObject[])ar);
							stack_obj[top] = null;
							--top;
							break _push;
						}
						break _header_again;
					}
				case CT_MAP_KEY: {
						//System.out.println("map key:"+top+" "+obj);
						Object[] mp = (Object[])top_obj;
						mp[mp.length - top_count*2] = obj;
						top_ct = CT_MAP_VALUE;
						break _header_again;
					}
				case CT_MAP_VALUE: {
						//System.out.println("map value:"+top+" "+obj);
						Object[] mp = (Object[])top_obj;
						mp[mp.length - top_count*2 + 1] = obj;
						if(--top_count == 0) {
							top_obj    = stack_obj[top];
							top_ct     = stack_ct[top];
							top_count  = stack_count[top];
							obj = MapType.create((MessagePackObject[])mp);
							stack_obj[top] = null;
							--top;
							break _push;
						}
						top_ct = CT_MAP_KEY;
						break _header_again;
					}
                case CT_VO_VALUES: {
                        //System.out.println(top + " valueobject:values");
                        VOInstance vo = (VOInstance) top_obj;
                        vo.putValue(obj);

                        if (vo.fieldgroupRequiresMoreValues()) {
                            //System.out.println(top + "   \\_ -> CT_VO_VALUES -> requires more values");
                            break _header_again;
                        }
                        // else
                        if (--top_count == 0)
                        {
                            //System.out.println(top + "   \\_ -> no more field groups");
                            if (vo.mixinDataRemains()) {
                                vo.nextMixin();
                                trail = 3;
                                cs = CS_VO_HEADER; // process next mixin
                                break _header_again_without_cs_reset;
                            }
                            // else, value-object complete, POP stack
                            //System.out.println(top + "   \\_ -> pop stack     | vo done");
                            top_obj    = stack_obj[top];
                            top_ct     = stack_ct[top];
                            top_count  = stack_count[top];
                            stack_obj[top] = null;
                            --top;
                            obj = vo.getData();
                            break _push;
                        }
                        // else next set of properties
                        //System.out.println(top + "   \\_ -> CT_VO_VALUES -> next set of properties");
                        trail = 1;
                        cs = CS_VO_FIELDS;
                        break _header_again_without_cs_reset;
                    }
				default:
					throw new UnpackException("parse error");
				}
			}  // _push
			} while(true);

        }  // _header_again
        cs  = CS_HEADER;
        ++i;
        } // _header_again_without_cs_reset
		} while(i < limit);  // _out

		return i;
	}
}

