package union.utils;

public class CastUtil {
	
	public static Long castLong(Object o) {
		if (o == null) return null;
		return Long.valueOf(o.toString());
	}
	
}
