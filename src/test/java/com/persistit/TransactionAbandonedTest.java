/**
 * Copyright © 2005-2013 Akiban Technologies, Inc.  All rights reserved.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * This program may also be available under different license terms.
 * For more information, see www.akiban.com or contact licensing@akiban.com.
 *
 * Contributors:
 * Akiban Technologies, Inc.
 */

package com.persistit;

import com.persistit.exception.PersistitException;
import com.persistit.unit.ConcurrentUtil;
import com.persistit.unit.UnitTestProperties;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * <p>
 * Inspired by bug1126297: Assertion failure in
 * TransactionIndexBucket#allocateTransactionStatus
 * </p>
 * <p>
 * The symptom was the bug (a locked TransactionStatus on the free list) but the
 * cause was mishandling of abandoned transactions from the
 * {@link Persistit#cleanup()} method.
 * </p>
 * <p>
 * When attempting to rollback the abandoned transaction, the status was
 * notified and then unlocked. Since the lock was held by a now dead thread, an
 * IllegalMonitorStateException occurred. It was then put on the free list
 * during the next cleanup of that bucket since it had been notified.
 * </p>
 */
public class TransactionAbandonedTest extends PersistitUnitTestCase {
    private static final String TREE = TransactionAbandonedTest.class.getSimpleName();
    private static final int KEY_START = 1;
    private static final int KEY_RANGE = 10;
    private static final long MAX_TIMEOUT_MS = 10 * 1000;

    private static class TxnAbandoner extends ConcurrentUtil.ThrowingRunnable {
        private final Persistit persistit;
        private final boolean doRead;
        private final boolean doWrite;

        public TxnAbandoner(Persistit persistit, boolean doRead, boolean doWrite) {
            this.persistit = persistit;
            this.doRead = doRead;
            this.doWrite = doWrite;
        }

        @Override
        public void run() throws PersistitException {
            Transaction txn = persistit.getTransaction();
            txn.begin();
            if (doRead) {
                assertEquals("Traverse count", KEY_RANGE, scanAndCount(getExchange(persistit)));
            }
            if (doWrite) {
                loadData(persistit, KEY_START + KEY_RANGE, KEY_RANGE);
            }
        }
    }

    private static Exchange getExchange(Persistit persistit) throws PersistitException {
        return persistit.getExchange(UnitTestProperties.VOLUME_NAME, TREE, true);
    }

    private static void loadData(Persistit persistit, int keyOffset, int count) throws PersistitException {
        Exchange ex = getExchange(persistit);
        for (int i = 0; i < count; ++i) {
            ex.clear().append(keyOffset + i).store();
        }
    }

    private static int scanAndCount(Exchange ex) throws PersistitException {
        ex.clear().append(Key.BEFORE);
        int saw = 0;
        while (ex.next()) {
            ++saw;
        }
        return saw;
    }

    @Before
    public void disableAndLoad() throws PersistitException {
        disableBackgroundCleanup();
        loadData(_persistit, KEY_START, KEY_RANGE);
    }

    private void runAndCleanup(String name, boolean doRead, boolean doWrite) {
        Thread t = ConcurrentUtil.createThread(name, new TxnAbandoner(_persistit, false, false));
        ConcurrentUtil.startAndJoinAssertSuccess(MAX_TIMEOUT_MS, t);
        // Threw exception before fix
        _persistit.cleanup();
    }

    @Test
    public void noReadsOrWrites() {
        runAndCleanup("NoReadNoWrite", false, false);
    }

    @Test
    public void readOnly() throws PersistitException {
        runAndCleanup("ReadOnly", true, false);
        assertEquals("Traversed after abandoned", KEY_RANGE, scanAndCount(getExchange(_persistit)));
    }

    @Test
    public void readAndWrite() throws Exception {
        runAndCleanup("ReadAndWrite", true, true);
        assertEquals("Traversed after abandoned", KEY_RANGE, scanAndCount(getExchange(_persistit)));
        // Check that the abandoned was pruned
        CleanupManager cm = _persistit.getCleanupManager();
        for (int i = 0; i < 5 && cm.getEnqueuedCount() > 0; ++i) {
            cm.runTask();
        }
        Exchange rawEx = getExchange(_persistit);
        rawEx.ignoreMVCCFetch(true);
        assertEquals("Raw traversed after abandoned", KEY_RANGE, scanAndCount(rawEx));
    }
}
