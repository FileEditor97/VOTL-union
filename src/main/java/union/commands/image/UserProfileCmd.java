package union.commands.image;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import net.dv8tion.jda.api.utils.FileUpload;
import union.App;
import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.utils.SteamUtil;
import union.utils.database.managers.UnionPlayerManager.PlayerInfo;
import union.utils.encoding.EncodingUtil;
import union.utils.imagegen.UserBackground;
import union.utils.imagegen.UserBackgroundHandler;
import union.utils.imagegen.renders.UserProfileRender;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

public class UserProfileCmd extends CommandBase {
	public UserProfileCmd() {
		this.name = "profile";
		this.path = "bot.image.profile";
		this.category = CmdCategory.IMAGE;
		this.module = CmdModule.IMAGE;
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help")),
			new OptionData(OptionType.INTEGER, "id", lu.getText(path+".id.help"))
				.setRequiredRange(0, 100)
		);
		this.cooldown = 120;
		this.cooldownScope = CooldownScope.USER;
		this.accessLevel = CmdAccessLevel.HELPER;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();

		Member target = event.optMember("user", event.getMember());
		if (target == null || target.getUser().isBot()) {
			editError(event, path+".bad_user");
			return;
		}

		int selectedBackgroundId = event.optInteger("id", 0);

		sendBackgroundMessage(event, target, selectedBackgroundId);
	}

	private void sendBackgroundMessage(
		SlashCommandEvent event,
		Member target,
		int backgroundId
	) {
		UserBackground background = UserBackgroundHandler.getInstance().fromId(backgroundId);
		if (background == null) {
			editError(event, path+".failed", "Background not found");
			return;
		}

		// Get user account
		Long steam64 = bot.getDBUtil().verifyCache.getSteam64(target.getIdLong());
		if (steam64 == null || steam64 == 0L) {
			editError(event, path+".not_found_steam", "Not found: "+target.getAsMention());
			return;
		}
		String steamId;
		try {
			steamId = SteamUtil.convertSteam64toSteamID(steam64);
		} catch (NumberFormatException ex) {
			editError(event, "errors.error", "Incorrect SteamID provided\nInput: `%s`".formatted(steam64));
			return;
		}

		List<PlayerInfo> playerData = bot.getDBUtil().unionPlayers.getPlayerInfo(
			event.getGuild().getIdLong(), steamId
		);
		playerData = playerData.stream().filter(PlayerInfo::exists).limit(6).toList();

		UserProfileRender render = new UserProfileRender(target)
			.setLocale(event.getUserLocale())
			.setBackground(background)
			.setPlayerData(playerData)
			.setAccessLevel(bot.getCheckUtil().getAccessLevel(target));

		final String attachmentName = EncodingUtil.encodeUserBg(event.getGuild().getIdLong(), target.getIdLong());

		EmbedBuilder embed = new EmbedBuilder()
			.setImage("attachment://" + attachmentName)
			.setColor(App.getInstance().getDBUtil().getGuildSettings(event.getGuild()).getColor());

		try {
			event.getHook().editOriginalEmbeds(embed.build()).setFiles(FileUpload.fromData(
				new ByteArrayInputStream(render.renderToBytes()),
				attachmentName
			)).queue();
		} catch (IOException e) {
			App.getInstance().getAppLogger().error("Failed to generate the rank background: {}", e.getMessage(), e);
			editError(event, path+".failed", "Rendering exception");
		}
	}
}
