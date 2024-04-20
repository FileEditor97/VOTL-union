package union.utils;

import java.util.function.Function;

public class CastUtil {
	
	public static Long castLong(Object o) {
		if (o == null) return null;
		return Long.valueOf(o.toString());
	}

	@SuppressWarnings("unchecked")
	public static <T> T getOrDefault(Object obj, T defaultObj) {
		if (obj == null) return defaultObj;
		if (obj instanceof Long || defaultObj instanceof Long) {
			return (T) castLong(obj);
		}
		return (T) obj;
	}

	@SuppressWarnings("unchecked")
	public static <T> T requireNonNull(Object obj) {
		if (obj == null) throw new NullPointerException("Object is null");
		if (obj instanceof Long) {
			return (T) castLong(obj);
		}
		return (T) obj;
	}

	public static <T> T resolveOrDefault(Object obj, Function<Object, T> resolver, T defaultObj) {
		if (obj == null) return defaultObj;
		return resolver.apply(obj);
	}
	
}
