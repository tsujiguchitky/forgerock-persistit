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
 * Created on July 22, 2004
 */
package com.persistit.stress;

import com.persistit.ArgParser;
import com.persistit.Exchange;
import com.persistit.Key;
import com.persistit.Transaction;
import com.persistit.TransactionRunnable;
import com.persistit.exception.PersistitException;
import com.persistit.test.PersistitTestResult;

/**
 * @version 1.0
 */
public class Stress8txn extends StressBase {
    private final static String SHORT_DESCRIPTION = "Tests transactions";

    private final static String LONG_DESCRIPTION = "   Tests transactions to ensure isolation, atomicity and\r\n"
            + "   consistency.  Each transaction performs several updates\r\n"
            + "   simulating moving cash between accounts.  To exercise\r\n"
            + "   optimistic concurrency control, several threads should run\r\n"
            + "   this test simultaneously.  At the beginning of the run, and\r\n"
            + "   periodically, this class tests whether all 'accounts' are\r\n"
            + "   consistent";

    @Override
    public String shortDescription() {
        return SHORT_DESCRIPTION;
    }

    @Override
    public String longDescription() {
        return LONG_DESCRIPTION;
    }

    private final static String[] ARGS_TEMPLATE = {
            "repeat|int:1:1:1000000000|Repetitions",
            "count|int:100:0:100000|Number of iterations per cycle",
            "size|int:1000:1:100000000|Number of 'C' accounts",
            "seed|int:1:1:20000|Random seed", };

    static boolean _consistencyCheckDone;
    int _size;
    int _seed;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        _ap = new ArgParser("com.persistit.Stress8txn", _args, ARGS_TEMPLATE);
        _total = _ap.getIntValue("count");
        _repeatTotal = _ap.getIntValue("repeat");
        _size = _ap.getIntValue("size");
        _seed = _ap.getIntValue("seed");
        seed(_seed);
        _dotGranularity = 1000;

        try {
            _exs = getPersistit().getExchange("persistit", "shared", true);
        } catch (final Exception ex) {
            handleThrowable(ex);
        }
    }

    /**
     * <p>
     * Implements tests with "accounts" to be updated transactionally There is a
     * hierarchy of accounts categories A, B and C. A accounts contain B
     * accounts which contain C accounts. At all times, the sums of C accounts
     * must match the total in their containing B account, and so on. The
     * overall sum of every account must always be 0. Operations are:
     * <ol>
     * <li>"transfer" (add/subtract) an amount from a C account to another C
     * account within the same B.</ki>
     * <li>"transfer" (add/subtract) an amount from a C account to a C account
     * in a different B account, resulting in changes to B and possibly A
     * account totals.</li>
     * <li>Consistency check - determining that the subaccounts total to the
     * containing account total.</li>
     * </ol>
     * </p>
     * <p>
     * As a wrinkle, a few "account" totals are represented by strings of a
     * length that represents the account total, rather than by an integer. This
     * is to test long record management during transactions.
     * </p>
     * <p>
     * The expected result is that each consistency check will match, no matter
     * what. This includes the result of abruptly stopping and restarting the
     * JVM. The first thread starting this test performs a consistency check
     * across the entire database to make sure that the result of any recovery
     * operation is correct.
     * </p>
     */
    @Override
    public void executeTest() {
        synchronized (Stress8txn.class) {
            if (!_consistencyCheckDone) {
                _consistencyCheckDone = true;
                try {
                    totalConsistencyCheck();
                } catch (final PersistitException pe) {
                    _result = new PersistitTestResult(false, pe);
                    forceStop();
                }
            }
        }

        final Transaction txn = _exs.getTransaction();
        final Operation[] ops = new Operation[5];
        ops[0] = new Operation0();
        ops[1] = new Operation1();
        ops[2] = new Operation2();
        ops[3] = new Operation3();
        ops[4] = new Operation4();

        for (_repeat = 0; (_repeat < _repeatTotal) && !isStopped(); _repeat++) {
            println();
            println();
            println("Starting test cycle " + _repeat + " at " + tsString());

            for (_count = 0; (_count < _total) && !isStopped(); _count++) {
                try {
                    dot();
                    final int selector = select();
                    final Operation op = ops[selector];
                    final int acct1 = random(0, _size);
                    final int acct2 = random(0, _size);
                    op.setup(acct1, acct2);
                    final int passes = txn.run(op, 100, 5, false);
                    if (passes > 10) {
                        println("pass count=" + passes);
                    }
                    if (op._result != null) {
                        _result = op._result;
                        // if (Debug.ENABLED) Debug.debug0(true);
                        forceStop();
                    }
                } catch (final Exception pe) {
                    _result = new PersistitTestResult(false, pe);
                    forceStop();
                }
            }
        }

        try {
            _exs.clear().append("stress8txn");
            while (_exs.next(true)) {
                if ((_exs.getValue().getType() == String.class)
                        && (getAccountValue(_exs) > 8000)) {
                    // System.out.println("len=" + getAccountValue(_exs) +
                    // " Key=" + _exs.getKey().toString());
                }
            }
        } catch (final PersistitException pe) {
            _result = new PersistitTestResult(false, pe);
            forceStop();
        }
    }

    private int select() {
        final int r = random(0, 1000);
        if (r < 800) {
            return 0;
        }
        if (r < 900) {
            return 1;
        }
        if (r < 940) {
            return 2;
        }
        if (r < 980) {
            return 3;
        }
        return 4;
    }

    private abstract class Operation implements TransactionRunnable {
        int _a1, _b1, _c1, _a2, _b2, _c2;

        void setup(final int acct1, final int acct2) {
            _a1 = (acct1 / 10000);
            _b1 = (acct1 / 100) % 100;
            _c1 = (acct1 % 100);

            _a2 = (acct2 / 10000);
            _b2 = (acct2 / 100) % 100;
            _c2 = (acct2 % 100);
        }

        PersistitTestResult _result = null;
    }

    private class Operation0 extends Operation {
        /**
         * Transfers from one C account to another within the same B
         */
        public void runTransaction() throws PersistitException {
            final int delta = random(-1000, 1000);
            if (_c1 != _c2) {
                _exs.clear().append("stress8txn").append(_a1).append(_b1)
                        .append(_c1).fetch();
                putAccountValue(_exs, getAccountValue(_exs) + delta, _c1 == 1);
                _exs.store();

                _exs.clear().append("stress8txn").append(_a1).append(_b1)
                        .append(_c2).fetch();
                putAccountValue(_exs, getAccountValue(_exs) - delta, _c2 == 1);
                _exs.store();
            }
        }
    }

    private class Operation1 extends Operation {
        /*
         * Transfers from one C account to another in possibly a different B
         * account
         */
        public void runTransaction() throws PersistitException {
            final int delta = random(-1000, 1000);
            if ((_c1 != _c2) || (_b1 != _b2) || (_a1 != _a2)) {
                _exs.clear().append("stress8txn").append(_a1).append(_b1)
                        .append(_c1).fetch();
                putAccountValue(_exs, getAccountValue(_exs) + delta, _c1 == 1);
                _exs.store();

                _exs.clear().append("stress8txn").append(_a2).append(_b2)
                        .append(_c2).fetch();
                putAccountValue(_exs, getAccountValue(_exs) - delta, _c2 == 1);
                _exs.store();
            }

            if ((_b1 != _b2) || (_a1 != _a2)) {
                _exs.clear().append("stress8txn").append(_a1).append(_b1)
                        .fetch();
                putAccountValue(_exs, getAccountValue(_exs) + delta, _b1 == 1);
                _exs.store();

                _exs.clear().append("stress8txn").append(_a2).append(_b2)
                        .fetch();
                putAccountValue(_exs, getAccountValue(_exs) - delta, _b1 == 1);
                _exs.store();
            }

            if (_a1 != _a2) {
                _exs.clear().append("stress8txn").append(_a1).fetch();
                putAccountValue(_exs, getAccountValue(_exs) + delta, _a1 == 1);
                _exs.store();

                _exs.clear().append("stress8txn").append(_a2).fetch();
                putAccountValue(_exs, getAccountValue(_exs) - delta, _a1 == 1);
                _exs.store();
            }
        }
    }

    private class Operation2 extends Operation {
        /**
         * Perform consistency check across a B account
         */
        public void runTransaction() throws PersistitException {
            _result = null;
            _exs.clear().append("stress8txn").append(_a1).append(_b1).fetch();
            final int valueB = getAccountValue(_exs);
            final int totalC = accountTotal(_exs);
            if (valueB != totalC) {
                _result = new PersistitTestResult(false, "totalC=" + totalC
                        + " valueB=" + valueB + " at " + _exs);
            }
        }
    }

    private class Operation3 extends Operation {
        /**
         * Perform consistency check across an A account
         */
        public void runTransaction() throws PersistitException {
            _result = null;
            _exs.clear().append("stress8txn").append(_a1).fetch();
            final int valueA = getAccountValue(_exs);
            final int totalB = accountTotal(_exs);
            if (valueA != totalB) {
                _result = new PersistitTestResult(false, "totalB=" + totalB
                        + " valueA=" + valueA + " at " + _exs);
            }
        }
    }

    private class Operation4 extends Operation {
        /**
         * Perform consistency check across all A accounts
         */
        public void runTransaction() throws PersistitException {
            _result = null;
            _exs.clear().append("stress8txn");
            final int totalA = accountTotal(_exs);
            if (totalA != 0) {
                _result = new PersistitTestResult(false, "totalA=" + totalA
                        + " at " + _exs);
            }
        }
    }

    private int accountTotal(final Exchange ex) throws PersistitException {
        int total = 0;
        ex.append(Key.BEFORE);
        while (ex.next()) {
            total += getAccountValue(ex);
        }
        ex.cut();
        return total;
    }

    private boolean totalConsistencyCheck() throws PersistitException {
        int totalA = 0;
        final Exchange exa = new Exchange(_exs);
        final Exchange exb = new Exchange(_exs);
        final Exchange exc = new Exchange(_exs);

        exa.clear().append("stress8txn").append(Key.BEFORE);
        while (exa.next()) {
            final int valueA = getAccountValue(exa);
            totalA += valueA;
            int totalB = 0;
            exa.getKey().copyTo(exb.getKey());
            exb.append(Key.BEFORE);
            while (exb.next()) {
                final int valueB = getAccountValue(exb);
                totalB += valueB;
                int totalC = 0;
                exb.getKey().copyTo(exc.getKey());
                exc.append(Key.BEFORE);
                while (exc.next()) {
                    final int valueC = getAccountValue(exc);
                    totalC += valueC;
                }
                if (totalC != valueB) {
                    _result = new PersistitTestResult(false, "totalC=" + totalC
                            + " valueB=" + valueB + " at " + exb);
                    forceStop();
                    return false;
                }
            }
            if (totalB != valueA) {
                _result = new PersistitTestResult(false, "totalB=" + totalB
                        + " valueA=" + valueA + " at " + exa);
                forceStop();
                return false;
            }
        }
        if (totalA != 0) {
            _result = new PersistitTestResult(false, "totalA=" + totalA
                    + " at " + exa);
            forceStop();
            return false;
        }
        return true;
    }

    private int getAccountValue(final Exchange ex) {
        if (!ex.getValue().isDefined()) {
            return 0;
        }
        try {
            if (ex.getValue().getType() == String.class) {
                ex.getValue().getString(_sb);
                return _sb.length();
            } else {
                return ex.getValue().getInt();
            }
        } catch (final NullPointerException npe) {
            System.out.println(ex);
            try {
                Thread.sleep(10000);
            } catch (final InterruptedException ie) {
            }
            throw npe;
        }
    }

    private void putAccountValue(final Exchange ex, final int value,
            final boolean string) {
        if ((value > 0) && (value < 100000)
                && ((random(0, 100) == 0) || string)) {
            _sb.setLength(0);
            int i = 0;
            for (i = 100; i < value; i += 100) {
                _sb.append("......... ......... ......... ......... ......... "
                        + "......... ......... ......... .........           ");
                int k = i;
                for (int j = 1; (k != 0) && (j < 10); j++) {
                    _sb.setCharAt(i - j, (char) ('0' + (k % 10)));
                    k /= 10;
                }
            }
            for (i = i - 100; i < value; i++) {
                _sb.append(".");
            }
            if (_sb.length() != value) {
                throw new RuntimeException("oops");
            }
            ex.getValue().putString(_sb);
        } else {
            ex.getValue().put(value);
        }
    }

    private void describeTest(final String m) {
        print(m);
        print(": ");
        for (int i = m.length(); i < 52; i++) {
            print(" ");
        }
    }

    public static void main(final String[] args) throws Exception {
        new Stress8txn().runStandalone(args);
    }

}
