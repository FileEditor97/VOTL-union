package union.commands.games;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.TimeFormat;

import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CaseType;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.CaseProofUtil;
import union.utils.database.managers.CaseManager;
import union.utils.exception.AttachmentParseException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class GameStrikeCmd extends CommandBase {

	private final long denyPerms = Permission.getRaw(Permission.MESSAGE_SEND, Permission.MESSAGE_SEND_IN_THREADS, Permission.MESSAGE_ADD_REACTION, Permission.CREATE_PUBLIC_THREADS);

	public GameStrikeCmd() {
		this.name = "gamestrike";
		this.path = "bot.games.gamestrike";
		this.options = List.of(
			new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
				.setChannelTypes(ChannelType.TEXT),
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help"), true)
				.setMaxLength(200),
			new OptionData(OptionType.ATTACHMENT, "proof", lu.getText(path+".proof.help"))
		);
		this.category = CmdCategory.GAMES;
		this.module = CmdModule.GAMES;
		this.accessLevel = CmdAccessLevel.MOD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();

		GuildChannel channel = event.optGuildChannel("channel");
		if (bot.getDBUtil().games.getMaxStrikes(channel.getIdLong()) == null) {
			editError(event, path+".not_found", "Channel: %s".formatted(channel.getAsMention()));
			return;
		}
		Member tm = event.optMember("user");
		if (tm == null || tm.getUser().isBot() || tm.equals(event.getMember())
			|| tm.equals(event.getGuild().getSelfMember())
			|| bot.getCheckUtil().hasHigherAccess(tm, event.getMember())) {
			editError(event, path+".not_member");
			return;
		}

		long channelId = channel.getIdLong();
		int strikeCooldown = bot.getDBUtil().getGuildSettings(event.getGuild()).getStrikeCooldown();
		if (strikeCooldown > 0) {
			Instant lastUpdate = bot.getDBUtil().games.getLastUpdate(channelId, tm.getIdLong());
			if (lastUpdate != null && lastUpdate.isAfter(Instant.now().minus(strikeCooldown, ChronoUnit.MINUTES))) {
				// Cooldown between strikes
				editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_FAILURE)
					.setDescription(lu.getText(event, path+".cooldown").formatted(TimeFormat.RELATIVE.format(lastUpdate.plus(strikeCooldown, ChronoUnit.MINUTES))))
					.build()
				);
				return;
			}
		}

		// Get proof
		final CaseProofUtil.ProofData proofData;
		try {
			proofData = CaseProofUtil.getData(event);
		} catch (AttachmentParseException e) {
			editError(event, e.getPath(), e.getMessage());
			return;
		}

		String reason = event.optString("reason");
		// Add to DB
		long guildId = event.getGuild().getIdLong();
		CaseManager.CaseData strikeData = bot.getDBUtil().cases.add(CaseType.GAME_STRIKE, tm.getIdLong(), tm.getUser().getName(), event.getUser().getIdLong(), event.getUser().getName(),
			guildId, reason, Instant.now(), null);
		bot.getDBUtil().games.addStrike(guildId, channelId, tm.getIdLong());
		// Inform user
		tm.getUser().openPrivateChannel().queue(pm -> {
			MessageEmbed embed = bot.getModerationUtil().getGameStrikeEmbed(channel, event.getUser(), reason);
			if (embed == null) return;
			pm.sendMessageEmbeds(embed).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
		});
		// Log
		final int strikeCount = bot.getDBUtil().games.countStrikes(channelId, tm.getIdLong());
		final int maxStrikes = bot.getDBUtil().games.getMaxStrikes(channelId);
		bot.getLogger().mod.onNewCase(event.getGuild(), tm.getUser(), strikeData, proofData, strikeCount+"/"+maxStrikes);
		// Reply
		editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
			.setDescription(lu.getText(event, path+".done").formatted(tm.getAsMention(), channel.getAsMention()))
			.setFooter("#"+strikeData.getLocalId())
			.build());
		// Check if reached limit
		if (strikeCount >= maxStrikes) {
			try {
				channel.getPermissionContainer().upsertPermissionOverride(tm).setDenied(denyPerms).reason("Game ban").queue();
			} catch (InsufficientPermissionException ignored) {}
		}
	}
}
