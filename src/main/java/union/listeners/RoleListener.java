package union.listeners;

import union.objects.annotation.NotNull;
import union.objects.logs.LogType;

import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateColorEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateIconEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateNameEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdatePermissionsEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class RoleListener extends ListenerAdapter {

	private final LogType type = LogType.ROLE;
	
	@Override
	public void onRoleCreate(@NotNull RoleCreateEvent event) {}

	@Override
	public void onRoleDelete(@NotNull RoleDeleteEvent event) {}

	@Override
	public void onRoleUpdateName(@NotNull RoleUpdateNameEvent event) {}

	@Override
	public void onRoleUpdateColor(@NotNull RoleUpdateColorEvent event) {}

	@Override
	public void onRoleUpdateIcon(@NotNull RoleUpdateIconEvent event) {}

	@Override
	public void onRoleUpdatePermissions(@NotNull RoleUpdatePermissionsEvent event) {}

}