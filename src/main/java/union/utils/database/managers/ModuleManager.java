package union.utils.database.managers;

import java.util.Collections;
import java.util.List;

import union.objects.CmdModule;
import union.utils.database.LiteDBBase;
import union.utils.database.ConnectionUtil;

public class ModuleManager extends LiteDBBase {
	
	public ModuleManager(ConnectionUtil cu) {
		super(cu, "moduleOff");
	}

	public void add(String guildId, CmdModule module) {
		execute("INSERT INTO %s(guildId, module) VALUES (%s, %s)".formatted(table, guildId, module.toString()));
	}

	public void remove(String guildId, CmdModule module) {
		execute("DELETE FROM %s WHERE (guildId=%s AND module=%s)".formatted(table, guildId, quote(module.toString())));
	}

	public void removeAll(String guildId) {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public List<CmdModule> getDisabled(String guildId) {
		List<String> data = select("SELECT module FROM %s WHERE (guildId=%s)".formatted(table, guildId), "module", String.class);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(v -> CmdModule.valueOf(v)).toList();
	}

	public boolean isDisabled(String guildId, CmdModule module) {
		return selectOne("SELECT module FROM %s WHERE (guildId=%s AND module=%s)".formatted(table, guildId, quote(module.toString())), "module", String.class) != null;
	}

}
