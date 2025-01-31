package union.objects;

public enum ExpType {
	ALL(0),
	TEXT(1),
	VOICE(2);

	public final int value;

	ExpType(final int value) {
		this.value = value;
	}
}
