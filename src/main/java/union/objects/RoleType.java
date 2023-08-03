package union.objects;

public enum RoleType {
	ASSIGN(0, "role_type.assign"),
	TOGGLE(1, "role_type.toggle"),
	CUSTOM(2, "role_type.custom");

	private final Integer type;
	private final String path;

	RoleType(Integer type, String path) {
		this.type = type;
		this.path = path;
	}

	public Integer getType() {
		return type;
	}

	public String getPath() {
		return path;
	}

	@Override
	public String toString() {
		return this.name().toLowerCase();
	}
}
