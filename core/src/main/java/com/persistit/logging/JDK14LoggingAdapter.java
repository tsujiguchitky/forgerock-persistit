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

package com.persistit.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wraps a JDK 1.4 <code>java.util.logging.Logger</code> for Persistit logging.
 * Code to enable default logging through the JDK 1.4 Loggin API is shown here:
 * <code><pre>
 *    // refer to any appropriate java.util.logging.Logger, for example
 *    Logger logger = Logger.getLogger("com.persistit");    //(for example)
 *    Persistit.setPersistitLogger(new JDK14LoggingAdapter(logger));
 * </pre></code>
 * 
 * @version 1.1
 */
public class JDK14LoggingAdapter extends AbstractPersistitLogger {
    /**
     * Mapping from int log levels used inside Persistit to JDK14 Level
     * constants.
     */
    public final static Level[] LEVEL_ARRAY = new Level[] { Level.FINEST, // 0
            Level.FINEST, // 1
            Level.FINER, // 2
            Level.FINE, // 3
            Level.INFO, // 4
            Level.WARNING, // 5
            Level.SEVERE, // 6
            Level.ALL, // 7
            Level.ALL, // 8
            Level.ALL, // 9
    };

    private Logger _logger;

    /**
     * Constructs a wrapped JDK 1.4 Logger.
     * 
     * @param logger
     *            A <code>Logger</code> to which Persistit log messages will be
     *            directed.
     */
    public JDK14LoggingAdapter(Logger logger) {
        _logger = logger;
    }

    /**
     * Translates the level specified by a Persisit <code>LogTemplate</code> to
     * a <code>java.util.logging.Level</code>.
     * 
     * @param lt
     *            The <code>LogTemplate</code>
     * @return The <code>Level</code>
     */
    public Level getLevel(LogTemplate lt) {
        return LEVEL_ARRAY[lt.getLevel()];
    }

    /**
     * Overrides <code>isLoggable</code> it allow control by the wrapped
     * <code>Logger</code>.
     * 
     * @param lt
     *            The <code>LogTemplate</code>
     */
    public boolean isLoggable(LogTemplate lt) {
        return _logger.isLoggable(getLevel(lt));
    }

    /**
     * Writes a log message generated by Persistit to the wrapped
     * <code>Logger</code>.
     * 
     * @param lt
     *            The <code>LogTemplate</code>
     * @param message
     *            The message to write to the log.
     */
    public void log(LogTemplate lt, String message) {
        _logger.log(getLevel(lt), message);
    }

}
