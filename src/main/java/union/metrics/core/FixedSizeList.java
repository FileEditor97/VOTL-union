package union.metrics.core;

import java.util.ArrayList;

public class FixedSizeList<E> extends ArrayList<E> {
	private final int maxSize;

	public FixedSizeList(int maxSize) {
		super();
		this.maxSize = maxSize;
	}

	@Override
	public boolean add(E e) {
		if (size() >= maxSize) {
			remove(0);
		}
		return super.add(e);
	}
}
