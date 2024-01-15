package union.commands.moderation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import union.App;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class BlacklistCmd extends CommandBase {
	
	public BlacklistCmd(App bot) {
		super(bot);
		this.name = "blacklist";
		this.path = "bot.moderation.blacklist";
		this.children = new SlashCommand[]{new View(bot), new Remove(bot)};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.OPERATOR;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class View extends SlashCommand {
		public View(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "view";
			this.path = "bot.moderation.blacklist.view";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group", lu.getText(path+".group.help"), true, true).setMinValue(1),
				new OptionData(OptionType.INTEGER, "page", lu.getText(path+".page.help")).setMinValue(1)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Integer groupId = event.optInteger("group");
			String guildId = event.getGuild().getId();
			if ( !(bot.getDBUtil().group.isMaster(groupId, guildId) || bot.getDBUtil().group.canManage(groupId, guildId)) ) {
				// Is not group's owner or manager
				editError(event, path+".cant_view");
				return;
			}

			Integer page = event.optInteger("page", 1);
			List<Map<String, Object>> list = bot.getDBUtil().blacklist.getByPage(groupId, page);
			if (list.isEmpty()) {
				editHookEmbed(event, bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, path+".empty").formatted(page)).build());
				return;
			}
			int pages = (int) Math.ceil(bot.getDBUtil().blacklist.countEntries(groupId) / 20.0);

			EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
				.setTitle(lu.getText(event, path+".title").formatted(groupId, page, pages));
			list.forEach(map -> 
				builder.addField("ID: %s".formatted((Long) map.get("userId")), lu.getText(event, path+".value").formatted(
					Optional.ofNullable((Long) map.get("steam64")).map(bot.getSteamUtil()::convertSteam64toSteamID).orElse("-"),
					Optional.ofNullable((Long) map.get("guildId")).map(event.getJDA()::getGuildById).map(Guild::getName).orElse("-"),
					Optional.ofNullable((Long) map.get("modId")).map(v -> "<@%s> (%<s)".formatted(v)).orElse("-"),
					Optional.ofNullable((String) map.get("reason")).map(v -> bot.getMessageUtil().limitString(v, 100)).orElse("-")
				), true)
			);

			editHookEmbed(event, builder.build());
		}
	}

	private class Remove extends SlashCommand {
		public Remove(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "remove";
			this.path = "bot.moderation.blacklist.remove";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group", lu.getText(path+".group.help"), true, true).setMinValue(1),
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help")),
				new OptionData(OptionType.STRING, "steamid", lu.getText(path+".steamid.help")).setMaxLength(30)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			Integer groupId = event.optInteger("group");
			String guildId = event.getGuild().getId();
			if ( !(bot.getDBUtil().group.isMaster(groupId, guildId) || bot.getDBUtil().group.canManage(groupId, guildId)) ) {
				// Is not group's owner or manager
				editError(event, path+".cant_view");
				return;
			}

			if (event.hasOption("user")) {
				User user = event.optUser("user");
				if (bot.getDBUtil().blacklist.inGroupUser(groupId, user.getIdLong())) {
					bot.getDBUtil().blacklist.removeUser(groupId, user.getIdLong());
					editHookEmbed(event, bot.getEmbedUtil().getEmbed(event)
						.setColor(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, path+".done_user").formatted(user.getAsMention(), user.getId(), groupId))
						.build()
					);
				} else {
					editError(event, path+".no_user", "Received: "+user.getAsMention());
				}
			}
			else if (event.hasOption("steamid")) {
				String input = event.optString("steamid");

				Long steam64 = null;
				if (Pattern.matches("^STEAM_[0-5]:[01]:\\d+$", input)) {
					steam64 = bot.getSteamUtil().convertSteamIDtoSteam64(input);
				} else {
					try {
						steam64 = Long.valueOf(input);
					} catch (NumberFormatException ex) {
						editError(event, "errors.unknown", ex.getMessage());
						return;
					}
				}

				if (bot.getDBUtil().blacklist.inGroupSteam64(groupId, steam64)) {
					bot.getDBUtil().blacklist.removeSteam64(groupId, steam64);
					editHookEmbed(event, bot.getEmbedUtil().getEmbed(event)
						.setColor(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, path+".done_steam").formatted(bot.getSteamUtil().convertSteam64toSteamID(steam64), groupId))
						.build()
					);
				} else {
					editError(event, path+".no_steam", "Received: "+steam64);
				}
			}
			else {
				// No options
				editError(event, path+".no_options");
			}
		}
	}
	
}
