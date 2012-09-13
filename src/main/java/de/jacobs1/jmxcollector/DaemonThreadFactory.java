package de.jacobs1.jmxcollector;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * simple thread factory to create daemon threads instead of normal non-daemon threads (daemon threads won't prevent
 * application exit).
 *
 * @author  hjacobs
 */
public class DaemonThreadFactory implements ThreadFactory {

    private final String threadNamePrefix;
    private final AtomicInteger counter = new AtomicInteger(0);

    public DaemonThreadFactory(final String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    @Override
    public Thread newThread(final Runnable r) {
        Thread t = new Thread(r, threadNamePrefix + "-" + counter.addAndGet(1));
        t.setDaemon(true);
        return t;
    }

}
