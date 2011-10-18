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

import java.util.EnumMap;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Wraps a Log4J <code>org.apache.log4j.Logger</code> for Persistit logging.
 * Code to enable default logging through Log4J is shown here: <code><pre>
 *    // refer to any appropriate org.apache.log4j.Logger, for example
 *    Logger logger = Logger.getLogger("com.persistit"); //(for example)
 *    Persistit.setPersistitLogger(new Log4JAdapter(logger));
 * </pre></code>
 * 
 * @version 1.1
 */
public class Log4JAdapter implements PersistitLogger {

    private final static EnumMap<PersistitLevel, Level> LEVEL_MAP = new EnumMap<PersistitLevel, Level>(
            PersistitLevel.class);

    static {
        LEVEL_MAP.put(PersistitLevel.NONE, Level.OFF);
        LEVEL_MAP.put(PersistitLevel.TRACE, Level.TRACE);
        LEVEL_MAP.put(PersistitLevel.DEBUG, Level.DEBUG);
        LEVEL_MAP.put(PersistitLevel.INFO, Level.INFO);
        LEVEL_MAP.put(PersistitLevel.WARNING, Level.WARN);
        LEVEL_MAP.put(PersistitLevel.ERROR, Level.ERROR);
    }

    private Logger _logger;

    /**
     * Constructs a wrapped JDK 1.4 Logger.
     * 
     * @param logger
     *            A <code>Logger</code> to which Persistit log messages will be
     *            directed.
     */
    public Log4JAdapter(Logger logger) {
        _logger = logger;
    }

    /**
     * Overrides <code>isLoggable</code> to allow control by the wrapped
     * <code>Logger</code>.
     * 
     * @param lt
     *            The <code>LogTemplate</code>
     */
    @Override
    public boolean isLoggable(PersistitLevel level) {
        return _logger.isEnabledFor(LEVEL_MAP.get(level));
    }

    /**
     * Writes a log message generated by Persistit to the wrapped
     * <code>Logger</code>.
     * 
     * @param level
     *            The <code>PersistitLevel</code>
     * @param message
     *            The message to write to the log.
     */
    @Override
    public void log(PersistitLevel level, String message) {
        _logger.log(LEVEL_MAP.get(level), message);
    }

    @Override
    public void open() {
        // Nothing to do - the log is created and destroyed by the embedding
        // application
    }

    @Override
    public void close() {
        // Nothing to do - the log is created and destroyed by the embedding
        // application
    }
}
