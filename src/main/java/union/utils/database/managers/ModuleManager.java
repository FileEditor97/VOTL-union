package union.utils.database.managers;

import java.util.Collections;
import java.util.List;

import union.objects.CmdModule;
import union.utils.database.LiteDBBase;
import union.utils.database.DBUtil;

public class ModuleManager extends LiteDBBase {

	private final String TABLE = "moduleOff";
	
	public ModuleManager(DBUtil util) {
		super(util);
	}

	public void add(String guildId, CmdModule module) {
		insert(TABLE, List.of("guildId", "module"), List.of(guildId, module.toString()));
	}

	public void remove(String guildId, CmdModule module) {
		delete(TABLE, List.of("guildId", "module"), List.of(guildId, module.toString()));
	}

	public void removeAll(String guildId) {
		delete(TABLE, "guildId", guildId);
	}

	public List<CmdModule> getDisabled(String guildId) {
		List<Object> data = select(TABLE, "module", "guildId", guildId);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(obj -> CmdModule.valueOf((String) obj)).toList();
	}

	public boolean isDisabled(String guildId, CmdModule module) {
		if (selectOne(TABLE, "guildId", List.of("guildId", "module"), List.of(guildId, module.toString())) == null) return false;
		return true;
	}

}
