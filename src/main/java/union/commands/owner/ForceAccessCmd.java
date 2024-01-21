package union.commands.owner;

import java.util.List;

import union.App;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.constants.CmdCategory;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class ForceAccessCmd extends CommandBase {
	
	public ForceAccessCmd(App bot) {
		super(bot);
		this.name = "forceaccess";
		this.path = "bot.owner.forceaccess";
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
		this.options = List.of(
			new OptionData(OptionType.STRING, "server", lu.getText(path+".server.help"), true),
			new OptionData(OptionType.INTEGER, "type", lu.getText(path+".type.help"), true)
				.addChoice("Role", 1)
				.addChoice("User", 2),
			new OptionData(OptionType.STRING, "target", lu.getText(path+".target.help"), true).setMaxLength(30),
			new OptionData(OptionType.INTEGER, "access_level", lu.getText(path+".access_level.help"), true)
				.addChoice("- Remove -", CmdAccessLevel.ALL.getLevel())
				.addChoice("Helper", CmdAccessLevel.HELPER.getLevel())
				.addChoice("Moderator", CmdAccessLevel.MOD.getLevel())
				.addChoice("Operator", CmdAccessLevel.OPERATOR.getLevel())
		);
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		Guild guild = bot.JDA.getGuildById(event.optString("server"));
		if (guild == null) {
			createError(event, path+".no_guild");
			return;
		}

		CmdAccessLevel level = CmdAccessLevel.byLevel(event.optInteger("access_level"));
		String targetId = event.optString("target");
		if (event.optInteger("type") == 1) {
			// Target is role
			if (level.equals(CmdAccessLevel.ALL)) {
				bot.getDBUtil().access.removeRole(targetId);
			}
			bot.getDBUtil().access.addRole(guild.getId(), targetId, level);
			createReply(event, lu.getText(event, path+".done").replace("{level}", level.getName()).replace("{target}", "Role `"+targetId+"`"));
		} else {
			// Target is user
			if (level.equals(CmdAccessLevel.ALL)) {
				bot.getDBUtil().access.removeUser(guild.getId(), targetId);
			}
			bot.getDBUtil().access.addUser(guild.getId(), targetId, level);
			createReply(event, lu.getText(event, path+".done").replace("{level}", level.getName()).replace("{target}", "User `"+targetId+"`"));
		}
	}

}
