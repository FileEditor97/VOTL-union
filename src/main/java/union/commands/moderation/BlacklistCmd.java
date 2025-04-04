package union.commands.moderation;

import static union.utils.CastUtil.castLong;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import net.dv8tion.jda.api.entities.MessageEmbed;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.SteamUtil;
import union.utils.database.managers.BlacklistManager;
import union.utils.message.MessageUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class BlacklistCmd extends CommandBase {
	
	public BlacklistCmd() {
		this.name = "blacklist";
		this.path = "bot.moderation.blacklist";
		this.children = new SlashCommand[]{
			new View(), new Remove(), new AddSteam(), new Search()
		};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.OPERATOR;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class View extends SlashCommand {
		public View() {
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
			long guildId = event.getGuild().getIdLong();
			if ( !(bot.getDBUtil().group.isOwner(groupId, guildId) || bot.getDBUtil().group.canManage(groupId, guildId)) ) {
				// Is not group's owner or manager
				editError(event, path+".cant_view");
				return;
			}

			Integer page = event.optInteger("page", 1);
			List<Map<String, Object>> list = bot.getDBUtil().blacklist.getByPage(groupId, page);
			if (list.isEmpty()) {
				editEmbed(event, bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, path+".empty").formatted(page)).build());
				return;
			}
			int pages = (int) Math.ceil(bot.getDBUtil().blacklist.countEntries(groupId) / 20.0);

			EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
				.setTitle(lu.getText(event, path+".title").formatted(groupId, page, pages));
			list.forEach(map -> 
				builder.addField("ID: %s".formatted(castLong(map.get("userId"))), lu.getText(event, path+".value").formatted(
					Optional.ofNullable(castLong(map.get("steam64"))).map(SteamUtil::convertSteam64toSteamID).orElse("-"),
					Optional.ofNullable(castLong(map.get("guildId"))).map(event.getJDA()::getGuildById).map(Guild::getName).orElse("-"),
					Optional.ofNullable(castLong(map.get("modId"))).map("<@%s>"::formatted).orElse("-"),
					Optional.ofNullable((String) map.get("reason")).map(v -> MessageUtil.limitString(v, 100)).orElse("-")
				), true)
			);

			editEmbed(event, builder.build());
		}
	}

	private class Remove extends SlashCommand {
		public Remove() {
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
			long guildId = event.getGuild().getIdLong();
			if ( !(bot.getDBUtil().group.isOwner(groupId, guildId) || bot.getDBUtil().group.canManage(groupId, guildId)) ) {
				// Is not group's owner or manager
				editError(event, path+".cant_view");
				return;
			}

			if (event.hasOption("user")) {
				User user = event.optUser("user");
				if (bot.getDBUtil().blacklist.inGroupUser(groupId, user.getIdLong())) {
					try {
						bot.getDBUtil().blacklist.removeUser(groupId, user.getIdLong());
					} catch (SQLException e) {
						editErrorDatabase(event, e, "blacklist remove user");
						return;
					}
					// Log into master
					bot.getLogger().mod.onBlacklistRemoved(event.getUser(), user, null, groupId);
					// Reply
					editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, path+".done_user").formatted(user.getAsMention(), user.getId(), groupId))
						.build()
					);
				} else {
					editError(event, path+".no_user", "Received: "+user.getAsMention());
				}
			}
			else if (event.hasOption("steamid")) {
				String input = event.optString("steamid");

				long steam64;
				if (Pattern.matches("^STEAM_[0-5]:[01]:\\d+$", input)) {
					steam64 = SteamUtil.convertSteamIDtoSteam64(input);
				} else {
					try {
						steam64 = Long.parseLong(input);
					} catch (NumberFormatException ex) {
						editErrorOther(event, ex.getMessage());
						return;
					}
				}

				if (bot.getDBUtil().blacklist.inGroupSteam64(groupId, steam64)) {
					try {
						bot.getDBUtil().blacklist.removeSteam64(groupId, steam64);
					} catch (SQLException e) {
						editErrorDatabase(event, e, "blacklist remove steam");
						return;
					}
					// Log into master
					bot.getLogger().mod.onBlacklistRemoved(event.getUser(), null, steam64, groupId);
					// Reply
					editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, path+".done_steam").formatted(SteamUtil.convertSteam64toSteamID(steam64), groupId))
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

	private class AddSteam extends SlashCommand {
		public AddSteam() {
			this.name = "add";
			this.path = "bot.moderation.blacklist.add";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group", lu.getText(path+".group.help"), true, true).setMinValue(1),
				new OptionData(OptionType.STRING, "steamid", lu.getText(path+".steamid.help"), true).setMaxLength(30)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			Integer groupId = event.optInteger("group");
			long guildId = event.getGuild().getIdLong();
			if ( !(bot.getDBUtil().group.isOwner(groupId, guildId) || bot.getDBUtil().group.canManage(groupId, guildId)) ) {
				// Is not group's owner or manager
				editError(event, path+".cant_view");
				return;
			}

			String input = event.optString("steamid");

			long steam64;
			if (Pattern.matches("^STEAM_[0-5]:[01]:\\d+$", input)) {
				steam64 = SteamUtil.convertSteamIDtoSteam64(input);
			} else {
				try {
					steam64 = Long.parseLong(input);
				} catch (NumberFormatException ex) {
					editErrorOther(event, ex.getMessage());
					return;
				}
			}

			if (!bot.getDBUtil().blacklist.inGroupSteam64(groupId, steam64)) {
				try {
					bot.getDBUtil().blacklist.addSteam(guildId, groupId, steam64, event.getUser().getIdLong());
				} catch (SQLException e) {
					editErrorDatabase(event, e, "blacklist add steam");
					return;
				}
				// Log into master
				bot.getLogger().mod.onBlacklistAdded(event.getUser(), null, steam64, List.of(groupId));
				// Reply
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, path+".done").formatted(SteamUtil.convertSteam64toSteamID(steam64), groupId))
						.build()
				);
			} else {
				editError(event, path+".already", "Received: "+steam64);
			}
		}
	}

	private class Search extends SlashCommand {
		public Search() {
			this.name = "search";
			this.path = "bot.moderation.blacklist.search";
			this.options = List.of(
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help")),
				new OptionData(OptionType.STRING, "steamid", lu.getText(path+".steamid.help")).setMaxLength(30)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			long guildId = event.getGuild().getIdLong();
			final List<Integer> groupIds = new ArrayList<>();
			groupIds.addAll(bot.getDBUtil().group.getOwnedGroups(guildId));
			groupIds.addAll(bot.getDBUtil().group.getGuildGroups(guildId));
			if (groupIds.isEmpty()) {
				editError(event, path+".cant_view");
				return;
			}

			if (event.hasOption("user")) {
				User user = event.optUser("user");

				List<MessageEmbed> embeds = new ArrayList<>();
				for (BlacklistManager.BlacklistData data : bot.getDBUtil().blacklist.searchUserId(user.getIdLong())) {
					embeds.add(bot.getEmbedUtil().getEmbed()
						.setTitle("Group #`%s`".formatted(data.getGroupId()))
						.setDescription(lu.getText(event, path+".value")
							.formatted(
								"%s `%s`".formatted(user.getAsMention(), user.getId()),
								Optional.ofNullable(data.getSteam64()).map(SteamUtil::convertSteam64toSteamID).orElse("-"),
								Optional.ofNullable(event.getJDA().getGuildById(data.getGuildId())).map(Guild::getName).orElse("-"),
								"<@%s>".formatted(data.getModId()),
								Optional.ofNullable(data.getReason()).map(v -> MessageUtil.limitString(v, 100)).orElse("-")
							)
						).build()
					);
				}

				if (embeds.isEmpty()) {
					editEmbed(event, bot.getEmbedUtil().getEmbed()
						.setDescription(lu.getText(event, path+".not_found").formatted(user.getAsMention()))
						.build());
				} else {
					event.getHook().editOriginalEmbeds(embeds).queue();
				}
			} else if (event.hasOption("steamid")) {
				String input = event.optString("steamid");

				long steam64;
				if (Pattern.matches("^STEAM_[0-5]:[01]:\\d+$", input)) {
					steam64 = SteamUtil.convertSteamIDtoSteam64(input);
				} else {
					try {
						steam64 = Long.parseLong(input);
					} catch (NumberFormatException ex) {
						editErrorOther(event, ex.getMessage());
						return;
					}
				}

				List<MessageEmbed> embeds = new ArrayList<>();
				for (BlacklistManager.BlacklistData data : bot.getDBUtil().blacklist.searchSteam64(steam64)) {
					embeds.add(bot.getEmbedUtil().getEmbed()
						.setTitle("Group #`%s`".formatted(data.getGroupId()))
						.setDescription(lu.getText(event, path+".value")
							.formatted(
								Optional.ofNullable(data.getUserId()).map("<@%s> `%<s`"::formatted).orElse("-"),
								SteamUtil.convertSteam64toSteamID(steam64),
								Optional.ofNullable(event.getJDA().getGuildById(data.getGuildId())).map(Guild::getName).orElse("-"),
								"<@%s>".formatted(data.getModId()),
								Optional.ofNullable(data.getReason()).map(v -> MessageUtil.limitString(v, 100)).orElse("-")
							)
						).build()
					);
				}

				if (embeds.isEmpty()) {
					editEmbed(event, bot.getEmbedUtil().getEmbed()
						.setDescription(lu.getText(event, path+".not_found").formatted(steam64))
						.build());
				} else {
					event.getHook().editOriginalEmbeds(embeds).queue();
				}
			} else {
				// No options
				editError(event, path+".no_options");
			}
		}
	}
	
}
