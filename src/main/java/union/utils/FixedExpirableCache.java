package union.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @param <K> key type
 * @param <V> cache item type
 */
public class FixedExpirableCache<K, V> {
	private final ConcurrentMap<K, CacheItem<V>> map;
	private final K[] keys;
	private final long itemLifetimeSeconds; // Item lifetime in milliseconds
	private final ScheduledExecutorService cleanupExecutor;
	private final AtomicInteger currIndex = new AtomicInteger(0);

	@SuppressWarnings("unchecked")
	public FixedExpirableCache(int size, long itemLifetimeSeconds) {
		this.map = new ConcurrentHashMap<>();
		if (size < 1)
			throw new IllegalArgumentException("Cache size must be at least 1!");
		if (itemLifetimeSeconds < 10)
			throw new IllegalArgumentException("Item lifetime must be at least 10 second!");

		this.keys = (K[]) new Object[size];
		this.itemLifetimeSeconds = itemLifetimeSeconds;

		// Schedule periodic cleanup
		this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
		this.cleanupExecutor.scheduleAtFixedRate(this::removeExpiredItems, itemLifetimeSeconds, itemLifetimeSeconds, TimeUnit.SECONDS);
	}

	public void put(K key, V value) {
		final long now = System.currentTimeMillis();

		if (map.containsKey(key)) {
			map.put(key, new CacheItem<>(value, now));
			return;
		}
		if (keys[currIndex.get()] != null)
			map.remove(keys[currIndex.get()]);

		keys[currIndex.getAndUpdate(i -> (i + 1) % keys.length)] = key;
		map.put(key, new CacheItem<>(value, now));
	}

	public V pull(K key) {
		CacheItem<V> removed = map.remove(key);
		return removed != null ? removed.value : null;
	}

	public void purge() {
		map.clear();
	}

	public V get(K key) {
		CacheItem<V> item = map.get(key);
		if (item == null || isExpired(item)) {
			map.remove(key); // Remove expired item immediately
			return null;
		}
		return item.value;
	}

	public boolean contains(K key) {
		CacheItem<V> item = map.get(key);
		return item != null && !isExpired(item);
	}

	public Collection<V> getValues() {
		removeExpiredItems(); // Ensure expired items are cleared before returning values
		List<V> values = new ArrayList<>();
		for (CacheItem<V> item : map.values()) {
			if (!isExpired(item)) {
				values.add(item.value);
			}
		}
		return values;
	}

	private void removeExpiredItems() {
		map.entrySet().removeIf(entry -> isExpired(entry.getValue()));
	}

	private boolean isExpired(CacheItem<V> item) {
		return System.currentTimeMillis() - item.creationTime > itemLifetimeSeconds*1000;
	}

	public void shutdown() {
		cleanupExecutor.shutdown();
	}

	/**
	 * Helper class to store value and its creation time
	 */
	private record CacheItem<V>(V value, long creationTime) {}
}
