package union.utils;

public class CastUtil {
	
	public static Long castLong(Object o) {
		if (o == null) return null;
		return Long.valueOf(o.toString());
	}

	@SuppressWarnings("unchecked")
	public static <T> T getOrDefault(Object obj, T defaultObj) {
		if (obj == null) return defaultObj;
		if (obj instanceof Long) {
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
	
}
