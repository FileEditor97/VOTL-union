package union.objects;

import net.dv8tion.jda.api.entities.Member;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReportData {
	private final Member member;
	private final int countTotal;
	private final List<String> countValues;
	private boolean skip = true;

	public ReportData(final Member member, final int countRoles, final Map<Integer, Integer> countMap) {
		this.member = member;
		this.countValues = new ArrayList<>();
		int sum = 0;
		int v = countStrikes(countMap); sum+=v;
		countValues.add(String.valueOf(v));
		v = getCount(countMap, CaseType.GAME_STRIKE); sum+=v;
		countValues.add(String.valueOf(v));
		v = getCount(countMap, CaseType.MUTE); sum+=v;
		countValues.add(String.valueOf(v));
		v = getCount(countMap, CaseType.KICK); sum+=v;
		countValues.add(String.valueOf(v));
		v = getCount(countMap, CaseType.BAN); sum+=v;
		countValues.add(String.valueOf(v));
		sum+=countRoles;
		countValues.add(String.valueOf(countRoles));
		this.countTotal = sum;
	}

	public void dontSkip() {
		skip = false;
	}

	public boolean skip() {
		return skip;
	}

	public Member getMember() {
		return member;
	}

	public String getCountTotal() {
		return String.valueOf(countTotal);
	}

	public int getCountTotalInt() {
		return countTotal;
	}

	public List<String> getCountValues() {
		return countValues;
	}

	private int countStrikes(Map<Integer, Integer> data) {
		return getCount(data, CaseType.STRIKE_1)+getCount(data, CaseType.STRIKE_2)+getCount(data, CaseType.STRIKE_3);
	}

	private int getCount(Map<Integer, Integer> data, CaseType type) {
		return data.getOrDefault(type.getType(), 0);
	}
}
