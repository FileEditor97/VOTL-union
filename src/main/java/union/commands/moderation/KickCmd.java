package union.commands.moderation;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CaseType;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.utils.CaseProofUtil;
import union.utils.database.managers.CaseManager.CaseData;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.ErrorResponse;
import union.utils.exception.AttachmentParseException;

public class KickCmd extends CommandBase {

	public KickCmd () {
		this.name = "kick";
		this.path = "bot.moderation.kick";
		this.options = List.of(
			new OptionData(OptionType.USER, "member", lu.getText(path+".member.help"), true),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help")).setMaxLength(400),
			new OptionData(OptionType.ATTACHMENT, "proof", lu.getText(path+".proof.help")),
			new OptionData(OptionType.BOOLEAN, "dm", lu.getText(path+".dm.help"))
		);
		this.botPermissions = new Permission[]{Permission.KICK_MEMBERS};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		this.cooldown = 10;
		this.cooldownScope = CooldownScope.GUILD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();
		Guild guild = Objects.requireNonNull(event.getGuild());

		Member tm = event.optMember("member");
		if (tm == null) {
			editError(event, path+".not_found");
			return;
		}
		if (event.getMember().equals(tm) || guild.getSelfMember().equals(tm)) {
			editError(event, path+".not_self");
			return;
		}

		Member mod = event.getMember();
		if (!guild.getSelfMember().canInteract(tm)) {
			editError(event, path+".kick_abort", "Bot can't interact with target member.");
			return;
		}
		if (bot.getCheckUtil().hasHigherAccess(tm, mod)) {
			editError(event, path+".higher_access");
			return;
		}
		if (!mod.canInteract(tm)) {
			editError(event, path+".kick_abort", "You can't interact with target member.");
			return;
		}

		// Get proof
		final CaseProofUtil.ProofData proofData;
		try {
			proofData = CaseProofUtil.getData(event);
		} catch (AttachmentParseException e) {
			editError(event, e.getPath(), e.getMessage());
			return;
		}

		String reason = event.optString("reason", lu.getLocalized(event.getGuildLocale(), path+".no_reason"));
		if (event.optBoolean("dm", true)) {
			tm.getUser().openPrivateChannel().queue(pm -> {
				final String text = bot.getModerationUtil().getDmText(CaseType.KICK, guild, reason, null, mod.getUser(), false);
				if (text == null) return;
				pm.sendMessage(text).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
			});
		}

		tm.kick().reason(reason).queueAfter(2, TimeUnit.SECONDS, done -> {
			// add info to db
			CaseData kickData;
			try {
				kickData = bot.getDBUtil().cases.add(CaseType.KICK, tm.getIdLong(), tm.getUser().getName(), mod.getIdLong(), mod.getUser().getName(),
					guild.getIdLong(), reason, Instant.now(), null);
			} catch (SQLException e) {
				editErrorDatabase(event, e, "Failed to create new case.");
				return;
			}
			// log kick
			bot.getLogger().mod.onNewCase(guild, tm.getUser(), kickData, proofData).thenAccept(logUrl -> {
				// Add log url to db
				bot.getDBUtil().cases.setLogUrl(kickData.getRowId(), logUrl);
				// reply and ask for kick sync
				event.getHook().editOriginalEmbeds(
					bot.getModerationUtil().actionEmbed(guild.getLocale(), kickData.getLocalIdInt(),
						path+".success", tm.getUser(), mod.getUser(), reason, logUrl)
				).setActionRow(
					Button.primary("sync_kick:"+tm.getId(), "Sync kick").withEmoji(Emoji.fromUnicode("🆑"))
				).queue();
			});
		},
		failure -> editErrorOther(event, failure.getMessage()));
	}
}
