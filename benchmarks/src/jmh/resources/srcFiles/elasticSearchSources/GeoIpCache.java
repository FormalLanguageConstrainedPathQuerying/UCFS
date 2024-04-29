/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.ingest.geoip;

import com.maxmind.db.NodeCache;
import com.maxmind.geoip2.model.AbstractResponse;

import org.elasticsearch.common.cache.Cache;
import org.elasticsearch.common.cache.CacheBuilder;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.ingest.geoip.stats.CacheStats;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * The in-memory cache for the geoip data. There should only be 1 instance of this class.
 * This cache differs from the maxmind's {@link NodeCache} such that this cache stores the deserialized Json objects to avoid the
 * cost of deserialization for each lookup (cached or not). This comes at slight expense of higher memory usage, but significant
 * reduction of CPU usage.
 */
final class GeoIpCache {

    /**
     * Internal-only sentinel object for recording that a result from the geoip database was null (i.e. there was no result). By caching
     * this no-result we can distinguish between something not being in the cache because we haven't searched for that data yet, versus
     * something not being in the cache because the data doesn't exist in the database.
     */
    static final AbstractResponse NO_RESULT = new AbstractResponse() {
        @Override
        public String toString() {
            return "AbstractResponse[NO_RESULT]";
        }
    };

    private final LongSupplier relativeNanoTimeProvider;
    private final Cache<CacheKey, AbstractResponse> cache;
    private final AtomicLong hitsTimeInNanos = new AtomicLong(0);
    private final AtomicLong missesTimeInNanos = new AtomicLong(0);

    GeoIpCache(long maxSize, LongSupplier relativeNanoTimeProvider) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("geoip max cache size must be 0 or greater");
        }
        this.relativeNanoTimeProvider = relativeNanoTimeProvider;
        this.cache = CacheBuilder.<CacheKey, AbstractResponse>builder().setMaximumWeight(maxSize).build();
    }

    GeoIpCache(long maxSize) {
        this(maxSize, System::nanoTime);
    }

    @SuppressWarnings("unchecked")
    <T extends AbstractResponse> T putIfAbsent(
        InetAddress ip,
        String databasePath,
        Function<InetAddress, AbstractResponse> retrieveFunction
    ) {
        CacheKey cacheKey = new CacheKey(ip, databasePath);
        long cacheStart = relativeNanoTimeProvider.getAsLong();
        AbstractResponse response = cache.get(cacheKey);
        long cacheRequestTime = relativeNanoTimeProvider.getAsLong() - cacheStart;

        if (response == null) {
            long retrieveStart = relativeNanoTimeProvider.getAsLong();
            response = retrieveFunction.apply(ip);
            if (response == null) {
                response = NO_RESULT;
            }
            cache.put(cacheKey, response);
            long databaseRequestAndCachePutTime = relativeNanoTimeProvider.getAsLong() - retrieveStart;
            missesTimeInNanos.addAndGet(cacheRequestTime + databaseRequestAndCachePutTime);
        } else {
            hitsTimeInNanos.addAndGet(cacheRequestTime);
        }

        if (response == NO_RESULT) {
            return null; 
        } else {
            return (T) response;
        }
    }

    AbstractResponse get(InetAddress ip, String databasePath) {
        CacheKey cacheKey = new CacheKey(ip, databasePath);
        return cache.get(cacheKey);
    }

    public int purgeCacheEntriesForDatabase(Path databaseFile) {
        String databasePath = databaseFile.toString();
        int counter = 0;
        for (CacheKey key : cache.keys()) {
            if (key.databasePath.equals(databasePath)) {
                cache.invalidate(key);
                counter++;
            }
        }
        return counter;
    }

    public int count() {
        return cache.count();
    }

    /**
     * Returns stats about this cache as of this moment. There is no guarantee that the counts reconcile (for example hits + misses = count)
     * because no locking is performed when requesting these stats.
     * @return Current stats about this cache
     */
    public CacheStats getCacheStats() {
        Cache.CacheStats stats = cache.stats();
        return new CacheStats(
            cache.count(),
            stats.getHits(),
            stats.getMisses(),
            stats.getEvictions(),
            TimeValue.nsecToMSec(hitsTimeInNanos.get()),
            TimeValue.nsecToMSec(missesTimeInNanos.get())
        );
    }

    /**
     * The key to use for the cache. Since this cache can span multiple geoip processors that all use different databases, the database
     * path is needed to be included in the cache key. For example, if we only used the IP address as the key the City and ASN the same
     * IP may be in both with different values and we need to cache both.
     */
    private record CacheKey(InetAddress ip, String databasePath) {}
}
