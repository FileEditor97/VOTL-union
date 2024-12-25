package union.utils;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @param <K> key type
 * @param <V> cache item type
 */
public class FixedCache<K, V> {
	private final ConcurrentMap<K, V> map;
	private final K[] keys;
	private final AtomicInteger currIndex = new AtomicInteger(0);

	@SuppressWarnings("unchecked")
	public FixedCache(int size) {
		this.map = new ConcurrentHashMap<>();
		if (size < 1)
			throw new IllegalArgumentException("Cache size must be at least 1!");
		this.keys = (K[]) new Object[size];
	}

	public V put(K key, V value) {
		if (map.containsKey(key)) {
			return map.put(key, value);
		}
		if (keys[currIndex.get()] != null) {
			map.remove(keys[currIndex.get()]);
		}
		keys[currIndex.getAndUpdate(i -> (i + 1) % keys.length)] = key;
		return map.put(key, value);
	}

	public V pull(K key) {
		return map.remove(key);
	}

	public void purge() {
		map.clear();
	}

	public V get(K key) {
		return map.get(key);
	}

	public boolean contains(K key) {
		return map.containsKey(key);
	}

	public Collection<V> getValues() {
		return map.values();
	}

}
