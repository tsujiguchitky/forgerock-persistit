/*
 * Copyright (c) 2004 Persistit Corporation. All Rights Reserved.
 *
 * The Java source code is the confidential and proprietary information
 * of Persistit Corporation ("Confidential Information"). You shall
 * not disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Persistit Corporation.
 *
 * PERSISTIT CORPORATION MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE
 * SUITABILITY OF THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR
 * A PARTICULAR PURPOSE, OR NON-INFRINGEMENT. PERSISTIT CORPORATION SHALL
 * NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING,
 * MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 * 
 * Created on Apr 6, 2004
 */
package com.persistit.unit;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.TreeMap;

import com.persistit.Util;
import com.persistit.Value;

public class ValueTest1 extends PersistitUnitTestCase {

    private static class S implements Serializable {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;
    }

    public void test1() {
        System.out.print("test1 ");
        final Value value = new Value(_persistit);

        value.put(1);

        assertEquals(value.getType(), int.class);
        assertEquals(value.getInt(), 1);
        assertEquals(value.get(), new Integer(1));
        value.put("foobar");

        assertEquals(value.getType(), String.class);
        assertEquals(value.getString(), "foobar");
        assertEquals(value.get(), "foobar");
        final StringBuffer sb = new StringBuffer();
        value.getString(sb);
        assertEquals(sb.toString(), "foobar");

        value.put(new byte[] { 1, 2, 3, 4, 5 });
        assertEquals(value.getType(), byte[].class);
        assertTrue(equals((byte[]) value.get(), new byte[] { 1, 2, 3, 4, 5 }));
        assertTrue(equals((byte[]) value.getByteArray(), new byte[] { 1, 2, 3,
                4, 5 }));
        final byte[] target = new byte[2];
        value.getByteArray(target, 2, 0, 3);
        assertTrue(equals(target, new byte[] { 3, 4 }));

        System.out.println("- done");
    }

    public void test2() {
        System.out.print("test2 ");
        final Value value = new Value(_persistit);
        value.setStreamMode(true);
        value.putString("abc");
        value.putString("abcdefghijklmnopqrstuvxwyz");
        value.putString("abcdefghijklmnopqrstuvxwyz0123456789");
        value.put(new byte[1000]);
        value.put(1.234F);
        value.put(1.23456D);

        value.setStreamMode(true);
        final String a = value.getString();
        final String b = (String) value.get();
        final String c = value.getString();
        final byte[] d = (byte[]) value.get();
        final Float e = (Float) value.get();
        final Double f = (Double) value.get();

        assertEquals(a, "abc");
        assertEquals(b, "abcdefghijklmnopqrstuvxwyz");
        assertEquals(c, "abcdefghijklmnopqrstuvxwyz0123456789");
        assertTrue(equals(d, new byte[1000]));
        assertEquals(e, new Float(1.234F));
        assertEquals(f, new Double(1.23456D));
        System.out.println("- done");
    }

    public void test3() {
        System.out.print("test3 ");
        final Value value = new Value(_persistit);
        value.setStreamMode(true);
        final byte[] b1 = { -3, -2, -1, 0, 1, 2, 3 };
        final short[] s1 = { 1, 2, 3, -1, -2, -3 };
        final char[] c1 = { 1, 2, 3, 65533, 65534, 65535 };
        final int[] i1 = { Integer.MIN_VALUE, -2, -1, 0, 1, 2,
                Integer.MAX_VALUE };
        final boolean[] bo1 = new boolean[] { true, true, false, true, false,
                false };
        final float[] f1 = { 1.1F, 1.2F, 1.3F, -1.1F, -1.2F, -1.3F };
        final double[] d1 = { 1.1, 1.2, 1.3, -1.1, -1.2, -1.3 };

        value.put(bo1);
        value.put(b1);
        value.put(s1);
        value.put(c1);
        value.put(i1);
        value.put(f1);
        value.put(d1);

        value.setStreamMode(true);
        final boolean[] bo2 = (boolean[]) value.get();
        final byte[] b2 = (byte[]) value.get();
        final short[] s2 = (short[]) value.get();
        final char[] c2 = (char[]) value.get();
        final int[] i2 = (int[]) value.get();
        final float[] f2 = (float[]) value.get();
        final double[] d2 = (double[]) value.get();

        assertTrue(equals(bo1, bo2));
        assertTrue(equals(b1, b2));
        assertTrue(equals(s1, s2));
        assertTrue(equals(c1, c2));
        assertTrue(equals(i1, i2));
        assertTrue(equals(f1, f2));
        assertTrue(equals(d1, d2));

        System.out.println("- done");
    }

    public void test4() {
        System.out.print("test4 ");
        final Value value = new Value(_persistit);

        final TreeMap map1 = new TreeMap();
        map1.put("a", "A");
        map1.put("b", "B");
        map1.put("c", "C");

        for (int index = 0; index < 1000; index++) {
            map1.put("x", new Integer(index));
            value.put(map1);
            final TreeMap map2 = (TreeMap) value.get();
            assertEquals(map1, map2);
        }
        System.out.println("- done");
    }

    public void test5() {
        System.out.print("test5 ");
        final Value value = new Value(_persistit);
        final String[] sa1 = { "a", "bb", "ccc", "dddd", "eeeee" };
        value.put(sa1);

        assertEquals(sa1.getClass(), value.getType());
        // repeat to test array class cache.
        assertEquals(sa1.getClass(), value.getType());
        assertEquals(sa1.getClass(), value.getType());

        final String[] sa2 = (String[]) value.get();
        final String[] sa2a = (String[]) value.getArray();

        assertTrue(equals(sa2, sa1));
        assertTrue(equals(sa2a, sa1));

        System.out.println("- done");
    }

    public void test6() {
        System.out.print("test6 ");
        final Value value = new Value(_persistit);
        final byte[][] ba1 = { { 1, 2, 3 }, { -4, -5, -6 }, null,
                { 7, 8, 9, 10, 11, 12 } };
        value.put(ba1);
        final byte[][] ba2 = (byte[][]) value.get();

        assertEquals(ba1.getClass(), value.getType());
        assertTrue(equals(ba1, ba2));

        final byte[][][] bb1 = new byte[][][] { ba1, ba1 };

        value.put(bb1);
        final byte[][][] bb2 = (byte[][][]) value.getArray();
        assertTrue(equals(bb1, bb2));

        System.out.println("- done");
    }

    public void test7() {
        System.out.print("test7 ");
        final Value value = new Value(_persistit);
        final String[][] sa1 = { { "a", "bb", "ccc", "dddd", "eeeee" },
                { "a", "bb", "ccc", "dddd", "eeeee" },
                { "A", "BB", "CCC", "DDDD", "EEEEE", "FFFFFF" },
                { "a", "bb", "ccc", "dddd", "eeeee" },
                { "A", "BB", "CCC", "DDDD", "EEEEE", "GGGGGG" },
                { "a", "bb", "ccc", "dddd", "eeeee" },
                { "A", "BB", "CCC", "DDDD", "EEEEE", "HHHHHH" },
                { "a", "bb", "ccc", "dddd", "eeeee" },
                { "A", "BB", "CCC", "DDDD", "EEEEE", "IIIIII" },
                { "a", "bb", "ccc", "dddd", "eeeee" },
                { "A", "BB", "CCC", "DDDD", "EEEEE", "JJJJJJ" }, {} };
        value.put(sa1);

        assertEquals(sa1.getClass(), value.getType());

        final String[][] sa2 = (String[][]) value.get();
        assertTrue(equals(sa2, sa1));

        final String[][][] sb1 = new String[][][] { sa1, sa1 };
        value.put(sb1);

        final String[][][] sb2 = (String[][][]) value.get();
        assertTrue(equals(sb1, sb2));
        assertEquals(sb1.getClass(), value.getType());
        System.out.println("- done");
    }

    public void test8() {
        System.out.print("test8 ");
        final Value value = new Value(_persistit);
        final Date date = new Date();
        value.put(date);
        final Date date2 = value.getDate();
        assertEquals(date2, date);

        final BigInteger bi = new BigInteger("123456");
        value.put(bi);
        final BigInteger bi2 = value.getBigInteger();
        assertEquals(bi2, bi);

        final BigDecimal bd = new BigDecimal("123456.654321");
        value.put(bd);
        final BigDecimal bd2 = value.getBigDecimal();
        assertEquals(bd, bd2);

        final Date[] dates = new Date[] { new Date(), new Date() };
        value.put(dates);
        final Date[] dates2 = (Date[]) value.get();
        assertTrue(equals(dates2, dates));

        final BigInteger[] bis = new BigInteger[] { new BigInteger("1"),
                new BigInteger("2"), };
        value.put(bis);
        final BigInteger[] bis2 = (BigInteger[]) value.get();
        assertTrue(equals(bis2, bis));

        final Object[] objects = new Object[] { dates, bis };
        value.put(objects);
        final Object[] objects2 = (Object[]) value.get();
        assertTrue(equals(objects, objects2));

        System.out.println("- done");
    }

    public void test9() {
        System.out.print("test9 ");
        final Object[] objects = new Object[8];
        objects[0] = new Date();
        objects[1] = new TreeMap();
        objects[2] = new BigInteger("39852357023498572034958723495872349582305");
        objects[3] = new BigInteger(
                "-39852357023498572034958723495872349582305");
        objects[4] = new BigDecimal("-991231.2123123123123123122349872e123");
        objects[5] = new BigInteger[] {
                new BigInteger("-39852357023498572034958723495872349582305"),
                new BigInteger("3498572034958723495872349582305"),
                new BigInteger(
                        "1239123912392339852357023498572034958723495872349582305"),
                new BigInteger("44"), };
        objects[6] = new BigDecimal[] {
                new BigDecimal("3.14159265"),
                new BigDecimal("0"),
                new BigDecimal("-9999999999999999999999999999999999.999999e999") };

        objects[7] = "The End";

        final Value value = new Value(_persistit);
        value.put(objects);
        final Object objects2 = value.get();
        assertTrue(equals(objects2, objects));
        System.out.println("- done");
    }

    public void test10() {
        System.out.print("test10 ");
        final ArrayList list = new ArrayList();
        list.add(new Boolean(false));
        list.add(new Boolean(true));
        list.add(null);
        list.add(new Byte((byte) 1));
        list.add(new Short((short) 2));
        list.add(new Character((char) 3));
        list.add(new Integer(4));
        list.add(new Long(5));
        list.add(new Float(6.0f));
        list.add(new Double(7.0d));
        list.add(new Byte((byte) -1));
        list.add(new Short((short) -2));
        list.add(new Character((char) -3));
        list.add(new Integer(-4));
        list.add(new Long(-5));
        list.add(new Float(-6.0f));
        list.add(new Double(-7.0d));
        list.add(new String("Eight"));
        list.add(new Date());
        list.add(BigInteger.valueOf(9));
        list.add(BigDecimal.valueOf(10));
        list.add(new Object[] { new S(), new S(), new S() });
        list.add(new Object[][] { new Object[] { new S(), new S(), new S() } });
        final Value value = new Value(_persistit);
        final Object[] objects1 = list.toArray();
        value.put(objects1);
        final Object[] objects2 = (Object[]) value.get();
        assertTrue(equals(objects2, objects2));

        System.out.println("- done");
    }

    public boolean equals(final Object a, final Object b) {
        if ((a == null) || (b == null)) {
            return a == b;
        }
        if (a.getClass().isArray()) {
            if (!b.getClass().isArray()) {
                return false;
            }
            if (a.getClass().getComponentType() != b.getClass()
                    .getComponentType()) {
                return false;
            }
            if (Array.getLength(a) != Array.getLength(b)) {
                return false;
            }
            for (int index = Array.getLength(a); --index >= 0;) {
                if (!equals(Array.get(a, index), Array.get(b, index))) {
                    return false;
                }
            }
            return true;
        } else if (a.getClass().isPrimitive()) {
            return a == b;
        } else {
            return a.equals(b);
        }
    }

    public static void main(final String[] args) throws Exception {
        new ValueTest1().initAndRunTest();
    }

    public void runAllTests() throws Exception {

        test1();
        test2();
        test3();
        test4();
        test5();
        test6();
        test7();
        test8();
        test9();
        test10();
    }

    private String floatBits(final float v) {
        final int bits = Float.floatToIntBits(v);
        final StringBuffer sb = new StringBuffer();
        Util.hex(sb, bits, 8);
        return sb.toString();
    }

    private String doubleBits(final double v) {
        final long bits = Double.doubleToLongBits(v);
        final StringBuffer sb = new StringBuffer();
        Util.hex(sb, bits, 16);
        return sb.toString();
    }

    private void debug(boolean condition) {
        if (!condition) {
            return;
        }
        return; // <-- breakpoint here
    }
}
