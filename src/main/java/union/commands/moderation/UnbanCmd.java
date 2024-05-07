package union.commands.moderation;

import java.time.Instant;
import java.util.List;

import union.App;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CaseType;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.database.managers.CaseManager.CaseData;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class UnbanCmd extends CommandBase {
	
	public UnbanCmd(App bot) {
		super(bot);
		this.name = "unban";
		this.path = "bot.moderation.unban";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help")).setMaxLength(400)
		);
		this.botPermissions = new Permission[]{Permission.BAN_MEMBERS};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();

		Guild guild = event.getGuild();
		User tu = event.optUser("user");

		if (tu == null) {
			editError(event, path+".not_found");
			return;
		}
		if (event.getUser().equals(tu) || event.getJDA().getSelfUser().equals(tu)) {
			editError(event, path+".not_self");
			return;
		}

		// Remove active ban log
		CaseData banData = bot.getDBUtil().cases.getMemberActive(tu.getIdLong(), guild.getIdLong(), CaseType.BAN);
		if (banData != null) {
			bot.getDBUtil().cases.setInactive(banData.getCaseIdInt());
		}

		guild.retrieveBan(tu).queue(ban -> {
			// Check if in blacklist
			for (int groupId : bot.getDBUtil().group.getGuildGroups(guild.getIdLong())) {
				// Check every group this server is part of for if user is blacklisted
				if (bot.getDBUtil().blacklist.inGroupUser(groupId, tu.getIdLong())) {
					if (bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.OPERATOR)) {
						// User is Operator+, remove blacklist
						bot.getDBUtil().blacklist.removeUser(groupId, tu.getIdLong());
						bot.getLogger().mod.onBlacklistRemoved(event.getUser(), tu, null, groupId);
					} else {
						// User is not Operator+, reject unban
						editError(event, path+".blacklisted", "Group ID : "+groupId);
						return;
					}
				}
			}
			Member mod = event.getMember();
			final String reason = event.optString("reason", lu.getText(event, path+".no_reason"));
			// add info to db
			bot.getDBUtil().cases.add(CaseType.UNBAN, tu.getIdLong(), tu.getName(), mod.getIdLong(), mod.getUser().getName(),
				guild.getIdLong(), reason, Instant.now(), null);
			CaseData unbanData = bot.getDBUtil().cases.getMemberLast(tu.getIdLong(), guild.getIdLong());
			// perform unban
			guild.unban(tu).reason(reason).queue();
			// log unban
			bot.getLogger().mod.onNewCase(guild, tu, unbanData, banData != null ? banData.getReason() : ban.getReason());

			// reply and ask for unban sync
			event.getHook().editOriginalEmbeds(
					bot.getModerationUtil().actionEmbed(guild.getLocale(), unbanData.getCaseIdInt(),
							path+".success", tu, mod.getUser(), reason)
			).setActionRow(
				Button.primary("sync_unban:"+tu.getId(), "Sync unban").withEmoji(Emoji.fromUnicode("🆑"))
			).queue();
		},
		failure -> {
			// reply and ask for unban sync
			event.getHook().editOriginalEmbeds(
				bot.getEmbedUtil().getEmbed(Constants.COLOR_FAILURE)
					.setDescription(lu.getText(event, path+".no_ban")
						.replace("{user_tag}", tu.getName()))
					.build()
			).setActionRow(
				Button.primary("sync_unban:"+tu.getId(), "Sync unban").withEmoji(Emoji.fromUnicode("🆑"))
			).queue();
		});
	}

}
