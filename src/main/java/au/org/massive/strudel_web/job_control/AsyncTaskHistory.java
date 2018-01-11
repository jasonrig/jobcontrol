package au.org.massive.strudel_web.job_control;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.FutureTask;

public class AsyncTaskHistory<T> extends LinkedHashMap<String, FutureTask<T>> {
    private final int maxSize;

    public AsyncTaskHistory(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<String, FutureTask<T>> eldest) {
        return size() > maxSize;
    }

    public Map.Entry<String, FutureTask<T>> put(FutureTask<T> v) {
        String identifier = UUID.randomUUID().toString();
        put(identifier, v);
        return new AbstractMap.SimpleEntry<>(identifier, v);
    }
}
