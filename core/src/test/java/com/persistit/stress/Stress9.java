/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.persistit.stress;

import java.util.ArrayList;

import com.persistit.Exchange;
import com.persistit.Key;
import com.persistit.Value;
import com.persistit.test.TestResult;
import com.persistit.util.ArgParser;

public class Stress9 extends StressBase {

    private final static String SHORT_DESCRIPTION = "Random key and value size write/read/delete/traverse loops"
            + " with serialized objects";

    private final static String LONG_DESCRIPTION = "   Simple stress test that repeats the following steps <repeat> times: \r\n"
            + "    - insert <count> random keys with random value length \r\n"
            + "    - read and verify <count> key/value pairs \r\n"
            + "    - traverse and count all keys using next() \r\n" + "    - delete <count> random keys\r\n";

    private final static String[] ARGS_TEMPLATE = { "op|String:wrtd|Operations to perform",
            "repeat|int:1:0:1000000000|Repetitions", "count|int:1000:0:1000000000|Number of nodes to populate",
            "size|int:4000:1:2000000|Approximate size of each data value", "seed|int:1:1:20000|Random seed",
            "splay|int:1:1:1000|Splay", };

    int _size;
    int _splay;
    int _seed;
    String _opflags;

    ArrayList _testValue = new ArrayList();

    @Override
    public String shortDescription() {
        return SHORT_DESCRIPTION;
    }

    @Override
    public String longDescription() {
        return LONG_DESCRIPTION;
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        _ap = new ArgParser("com.persistit.Stress9", _args, ARGS_TEMPLATE);
        _splay = _ap.getIntValue("splay");
        _opflags = _ap.getStringValue("op");
        _size = _ap.getIntValue("size");
        _seed = _ap.getIntValue("seed");
        _repeatTotal = _ap.getIntValue("repeat");
        _total = _ap.getIntValue("count");
        _dotGranularity = 10000;

        try {
            // Exchange with Thread-private Tree
            _ex = getPersistit().getExchange("persistit", _rootName + _threadIndex, true);
            _exs = getPersistit().getExchange("persistit", "shared", true);
        } catch (final Exception ex) {
            handleThrowable(ex);
        }
    }

    @Override
    public void executeTest() {
        final Value value1 = _exs.getValue();
        final Value value2 = new Value(getPersistit());

        setPhase("@");
        try {
            _ex.clear().remove(Key.GTEQ);
            _exs.clear().append("Stress9").append(Key.BEFORE);
            while (_exs.next()) {
                _exs.append(_threadIndex);
                _exs.remove(Key.GTEQ);
                _exs.cut();
            }
        } catch (final Exception e) {
            handleThrowable(e);
        }
        verboseln();

        for (_repeat = 0; (_repeat < _repeatTotal) && !isStopped(); _repeat++) {
            verboseln();
            verboseln("Starting cycle " + (_repeat + 1) + " of " + _repeatTotal);

            if (_opflags.indexOf('w') >= 0) {
                setPhase("w");
                _random.setSeed(_seed);
                for (_count = 0; (_count < _total) && !isStopped(); _count++) {
                    dot();
                    final int keyInteger = keyInteger(_count);

                    _exs.clear().append("Stress9").append(keyInteger).append(_threadIndex);
                    setupTestValue(_exs, keyInteger, random(2000, _size));

                    _ex.clear().append(keyInteger);
                    _ex.getValue().put(_exs.getValue().getEncodedSize());

                    try {
                        _exs.fetchAndStore();
                        _ex.store();
                        if (_exs.getValue().isDefined()) {
                            final Object obj = _exs.getValue().get();
                            if ((obj != null) && !(obj instanceof ArrayList)) {
                                throw new RuntimeException("Object should be ArrayList");
                            }
                        }
                    } catch (final Exception e) {
                        handleThrowable(e);

                        break;
                    }
                }
            }

            if (_opflags.indexOf('r') >= 0) {
                setPhase("r");
                _random.setSeed(_seed);
                for (_count = 0; (_count < _total) && !isStopped(); _count++) {
                    dot();
                    final int keyInteger = keyInteger(_count);
                    _exs.clear().append("Stress9").append(keyInteger).append(_threadIndex);
                    setupTestValue(_exs, keyInteger, random(20, _size));
                    _ex.clear().append(keyInteger);
                    try {
                        _ex.fetch();
                        int size1 = 0;
                        if (_ex.getValue().isDefined() && !_ex.getValue().isNull()) {
                            size1 = _ex.getValue().getInt();
                        }
                        _exs.fetch(value2);
                        final int size2 = value2.getEncodedSize();
                        if (size2 != size1) {
                            _result = new TestResult(false, "Value is size " + size2 + ", should be " + size1 + " key="
                                    + _ex.getKey());
                            println(_result);
                            forceStop();
                        }
                    } catch (final Exception e) {
                        handleThrowable(e);
                    }
                }
            }

            if (_opflags.indexOf('t') >= 0) {
                setPhase("t");

                _exs.clear().append("Stress9").append(Key.BEFORE);
                int count1 = 0;
                int count2 = 0;
                for (_count = 0; (_count < (_total * 10)) && !isStopped(); _count++) {
                    dot();
                    try {
                        if (!_exs.next()) {
                            break;
                        }
                        if (_exs.append(_threadIndex).fetch().getValue().isDefined()) {
                            count1++;
                        }
                        _exs.cut();
                    } catch (final Exception e) {
                        handleThrowable(e);
                    }
                }

                setPhase("T");
                _ex.clear().append(Key.BEFORE);
                for (_count = 0; (_count < (_total * 10)) && !isStopped(); _count++) {
                    dot();
                    try {
                        if (!_ex.next()) {
                            break;
                        }
                        count2++;
                    } catch (final Exception e) {
                        handleThrowable(e);
                    }
                }
                if (count1 != count2) {
                    _result = new TestResult(false, "Traverse count is " + count1 + " but should be " + count2
                            + " on repetition=" + _repeat + " in thread=" + _threadIndex);

                    break;
                }
            }

            if (_opflags.indexOf('d') >= 0) {
                setPhase("d");
                _random.setSeed(_seed);

                for (_count = 0; (_count < _total) && !isStopped(); _count++) {
                    dot();
                    final int keyInteger = keyInteger(_count);
                    _exs.clear().append("Stress9").append(keyInteger).append(_threadIndex);
                    _ex.clear().append(keyInteger);
                    try {
                        _exs.fetchAndRemove();
                        _ex.remove();
                        if (_exs.getValue().isDefined()) {
                            final Object obj = _exs.getValue().get();
                            if ((obj != null) && !(obj instanceof ArrayList)) {
                                throw new RuntimeException("expected an ArrayList");
                            }
                        }
                    } catch (final Exception e) {
                        handleThrowable(e);
                    }
                }
            }

            if ((_opflags.indexOf('h') > 0) && !isStopped()) {
                setPhase("h");
                try {
                    Thread.sleep(random(1000, 5000));
                } catch (final Exception e) {
                }
            }
        }
        verboseln();
        verbose("done");

    }

    int keyInteger(final int counter) {
        final int keyInteger = random(0, _total);
        return keyInteger;
    }

    @Override
    protected void setupTestValue(final Exchange ex, final int counter, final int length) {
        final int elements = length / 8; // ? size of a Integer?
        _testValue.clear();
        final Integer anInteger = new Integer(counter);
        for (int i = 0; i < elements; i++) {
            _testValue.add(anInteger);
        }
        ex.getValue().put(_testValue);
    }

    public static void main(final String[] args) {
        final Stress9 test = new Stress9();
        test.runStandalone(args);
    }
}
