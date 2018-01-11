package au.org.massive.strudel_web;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * A servlet listener to keep a thread pool active during the life of the application
 *
 * @author jrigby
 */
public class AsyncTasks implements ServletContextListener {

    private static ExecutorService executor;

    @Override
    public void contextDestroyed(ServletContextEvent arg0) {
        executor.shutdown();
    }

    @Override
    public void contextInitialized(ServletContextEvent arg0) {
        // Default to 20 concurrent tasks
        executor = Executors.newFixedThreadPool(20);
    }

    public static ExecutorService getExecutorService() {
        return executor;
    }

}
