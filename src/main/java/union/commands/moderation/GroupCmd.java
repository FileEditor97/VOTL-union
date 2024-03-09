package union.commands.moderation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import union.App;
import union.base.command.CooldownScope;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.base.waiter.EventWaiter;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.Emotes;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

public class GroupCmd extends CommandBase {
	
	private static EventWaiter waiter;

	public GroupCmd(App bot, EventWaiter waiter) {
		super(bot);
		this.name = "group";
		this.path = "bot.moderation.group";
		this.children = new SlashCommand[]{new Create(bot), new Delete(bot), new Add(bot), new Remove(bot), new Rename(bot), new Manage(bot), new View(bot)};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.OPERATOR;
		GroupCmd.waiter = waiter;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Create extends SlashCommand {

		public Create(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "create";
			this.path = "bot.moderation.group.create";
			this.options = List.of(
				new OptionData(OptionType.STRING, "name", lu.getText(path+".name.help"), true).setMaxLength(120),
				new OptionData(OptionType.STRING, "appeal_server", lu.getText(path+".appeal_server.help")).setRequiredLength(12, 20)
			);
			this.cooldown = 30;
			this.cooldownScope = CooldownScope.GUILD;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			long guildId = event.getGuild().getIdLong();
			if (bot.getDBUtil().group.getOwnedGroups(guildId).size() >= 3) {
				editError(event, path+".max_amount");
				return;
			}

			String groupName = event.optString("name");

			long appealGuildId = 0L;
			if (event.hasOption("appeal_server")) {
				try {
					appealGuildId = Long.parseLong(event.optString("appeal_server"));
				} catch (NumberFormatException ex) {
					editError(event, "errors.error", ex.getMessage());
					return;
				}
				if (appealGuildId != 0L && event.getJDA().getGuildById(appealGuildId) == null) {
					editError(event, "errors.error", "Unknown appeal server ID.\nReceived: "+appealGuildId);
					return;
				}
			}
			
			bot.getDBUtil().group.create(guildId, groupName, appealGuildId);
			Integer groupId = bot.getDBUtil().group.getIncrement();
			bot.getLogListener().group.onCreation(event, groupId, groupName);

			editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(
					lu.getText(event, path+".done").replace("{group_name}", groupName).replace("{group_id}", groupId.toString())
					.replace("{is_shared}", Emotes.CROSS_C.getEmote())
				)
				.build()
			);
		}

	}

	private class Delete extends SlashCommand {

		public Delete(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "delete";
			this.path = "bot.moderation.group.delete";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true).setMinValue(1)
			);
			this.cooldown = 30;
			this.cooldownScope = CooldownScope.GUILD;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			Integer groupId = event.optInteger("group_owned");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null) {
				editError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
				return;
			}
			if (event.getGuild().getIdLong() != ownerId) {
				editError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);

			bot.getDBUtil().group.deleteGroup(groupId);
			bot.getLogListener().group.onDeletion(event, groupId, groupName);

			editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(
					lu.getText(event, path+".done").replace("{group_name}", groupName).replace("{group_id}", groupId.toString())
				)
				.build()
			);
		}

	}

	private class Add extends SlashCommand {
		// for currect requirement is enough, but as major release - NO
		public Add(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "add";
			this.path = "bot.moderation.group.add";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true).setMinValue(1),
				new OptionData(OptionType.STRING, "server", lu.getText(path+".server.help"), true).setRequiredLength(12, 20),
				new OptionData(OptionType.BOOLEAN, "manage", lu.getText(path+".manage.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			Integer groupId = event.optInteger("group_owned");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null) {
				editError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
				return;
			}
			if (event.getGuild().getIdLong() != ownerId) {
				editError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
				return;
			}

			long targetId;
			try {
				targetId = Long.parseLong(event.optString("server"));
			} catch (NumberFormatException ex) {
				editError(event, "errors.error", ex.getMessage());
				return;
			}
			if (event.getGuild().getIdLong() == targetId) {
				editError(event, path+".failed_join", "This server is this Group's owner.\nGroup ID: `%s`".formatted(groupId));
				return;
			}
			if (bot.getDBUtil().group.isMember(groupId, targetId)) {
				editError(event, path+".is_member", "Group ID: `%s`".formatted(groupId));
				return;
			}
			
			// Search for server in both bots
			String groupName = bot.getDBUtil().group.getName(groupId);
			Boolean canManage = event.optBoolean("manage", false);
			Guild guild = null;
			try {
				guild = event.getJDA().getGuildById(targetId);
			} catch (NumberFormatException ex) {
				editError(event, path+".no_guild", "Server ID: `%d`".formatted(targetId));
				return;
			} 
			if (guild == null) {
				guild = Optional.ofNullable(bot.getHelper()).map(helper -> helper.getJDA().getGuildById(targetId)).orElse(null);
				if (guild == null) {
					editError(event, path+".no_guild", "Server ID: `%d`".formatted(targetId));
					return;
				} else {
					bot.getDBUtil().group.add(groupId, targetId, canManage);
					bot.getLogListener().group.onGuildAdded(event, groupId, groupName, targetId, guild.getName());
				}
			} else {
				bot.getDBUtil().group.add(groupId, targetId, canManage);
				bot.getLogListener().group.onGuildAdded(event, groupId, groupName, targetId, guild.getName());
			}

			editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(
					lu.getText(event, path+".done").replace("{server_id}", String.valueOf(targetId)).replace("{server_name}", guild.getName())
						.replace("{group_name}", groupName)
				)
				.build()
			);
		}

	}

	private class Remove extends SlashCommand {

		public Remove(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "remove";
			this.path = "bot.moderation.group.remove";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true).setMinValue(0)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			Integer groupId = event.optInteger("group_owned");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null) {
				editError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
				return;
			}
			if (event.getGuild().getIdLong() != ownerId) {
				editError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
				return;
			}

			List<Guild> guilds = bot.getDBUtil().group.getGroupMembers(groupId).stream().map(id -> {
				Guild guild = event.getJDA().getGuildById(id);
				if (guild == null) {
					guild = Optional.ofNullable(bot.getHelper()).map(helper -> helper.getJDA().getGuildById(id)).orElse(null);
				};
				return guild;
			}).filter(Objects::nonNull).toList();
			if (guilds.isEmpty()) {
				editError(event, path+".no_guilds");
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);
			MessageEmbed embed = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".embed_title"))
				.setDescription(lu.getText(event, path+".embed_value").replace("{group_name}", groupName))
				.build();
			
			StringSelectMenu menu = StringSelectMenu.create("menu:remove-guild")
				.setPlaceholder("Select")
				.setMaxValues(1)
				.addOptions(guilds.stream().map(guild -> {
					return SelectOption.of("%s (%s)".formatted(guild.getName(), guild.getId()), guild.getId());
				}).limit(25).toList())
				.build();
			event.getHook().editOriginalEmbeds(embed).setActionRow(menu).queue(msg -> {
				waiter.waitForEvent(
					StringSelectInteractionEvent.class,
					e -> e.getComponentId().equals("menu:remove-guild") && e.getMessageId().equals(msg.getId()),
					actionMenu -> {
						Long targetId = Long.valueOf(actionMenu.getSelectedOptions().get(0).getValue());
						Guild targetGuild = event.getJDA().getGuildById(targetId);
						if (targetGuild == null)
							targetGuild = Optional.ofNullable(bot.getHelper()).map(helper -> helper.getJDA().getGuildById(targetId)).orElse(null);

						bot.getDBUtil().group.remove(groupId, targetId);
						if (targetGuild != null)
							bot.getLogListener().group.onGuildRemoved(event, targetGuild, groupId, groupName);

						event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
							.setDescription(lu.getText(event, path+".done").replace("{guild_name}", Optional.ofNullable(targetGuild.getName()).orElse("*Unknown*")).replace("{group_name}", groupName))
							.build()
						).setComponents().queue();
					},
					30,
					TimeUnit.SECONDS,
					() -> {
						event.getHook().editOriginalComponents(
							ActionRow.of(menu.createCopy().setPlaceholder(lu.getText(event, "errors.timed_out")).setDisabled(true).build())
						).queue();
					}
				);
			});
		}

	}

	private class Rename extends SlashCommand {

		public Rename(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "rename";
			this.path = "bot.moderation.group.rename";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true).setMinValue(0),
				new OptionData(OptionType.STRING, "name", lu.getText(path+".name.help"), true).setMaxLength(120)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer groupId = event.optInteger("group_owned");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null) {
				createError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
				return;
			}
			if (event.getGuild().getIdLong() != ownerId) {
				createError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
				return;
			}

			String oldName = bot.getDBUtil().group.getName(groupId);
			String newName = event.optString("name");

			bot.getDBUtil().group.rename(groupId, newName);
			bot.getLogListener().group.onRenamed(event, oldName, groupId, newName);

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(
					lu.getText(event, path+".done").replace("{old_name}", oldName).replace("{new_name}", newName)
					.replace("{group_id}", groupId.toString())
				)
				.build()
			);
		}

	}

	private class Manage extends SlashCommand {

		public Manage(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "manage";
			this.path = "bot.moderation.group.manage";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true).setMinValue(0),
				new OptionData(OptionType.BOOLEAN, "manage", lu.getText(path+".manage.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			Integer groupId = event.optInteger("group_owned");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null) {
				editError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
				return;
			}
			if (event.getGuild().getIdLong() != ownerId) {
				editError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
				return;
			}

			Boolean canManage = event.optBoolean("manage", false);

			List<Guild> guilds = bot.getDBUtil().group.getGroupMembers(groupId).stream().map(id -> {
				Guild guild = event.getJDA().getGuildById(id);
				if (guild == null) {
					guild = Optional.ofNullable(bot.getHelper()).map(helper -> helper.getJDA().getGuildById(id)).orElse(null);
				};
				return guild;
			}).filter(Objects::nonNull).toList();
			if (guilds.isEmpty()) {
				editError(event, path+".no_guilds");
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);
			MessageEmbed embed = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".embed_title"))
				.setDescription(lu.getText(event, path+".embed_value").replace("{group_name}", groupName))
				.build();
			
			StringSelectMenu menu = StringSelectMenu.create("menu:select-guild")
				.setPlaceholder("Select")
				.setMaxValues(1)
				.addOptions(guilds.stream().map(guild -> {
					return SelectOption.of("%s (%s)".formatted(guild.getName(), guild.getId()), guild.getId());
				}).limit(25).toList())
				.build();
			event.getHook().editOriginalEmbeds(embed).setActionRow(menu).queue(msg -> {
				waiter.waitForEvent(
					StringSelectInteractionEvent.class,
					e -> e.getComponentId().equals("menu:select-guild") && e.getMessageId().equals(msg.getId()),
					actionMenu -> {
						Long targetId = Long.valueOf(actionMenu.getSelectedOptions().get(0).getValue());
						Guild targetGuild = event.getJDA().getGuildById(targetId);

						bot.getDBUtil().group.setManage(groupId, targetId, canManage);

						event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
							.setDescription(
								lu.getText(event, path+".done").replace("{guild_name}", targetGuild.getName()).replace("{group_name}", groupName)
								.replace("{manage}", canManage.toString())
							)
							.build()
						).setComponents().queue();
					},
					30,
					TimeUnit.SECONDS,
					() -> {
						event.getHook().editOriginalComponents(
							ActionRow.of(menu.createCopy().setPlaceholder(lu.getText(event, "errors.timed_out")).setDisabled(true).build())
						).queue();
					}
				);
			});
		}
	}

	private class View extends SlashCommand {

		public View(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "view";
			this.path = "bot.moderation.group.view";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), false, true).setMinValue(0),
				new OptionData(OptionType.INTEGER, "group_joined", lu.getText(path+".group_joined.help"), false, true).setMinValue(0)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			long guildId = event.getGuild().getIdLong();
			if (event.hasOption("group_owned")) {
				// View owned Group information - name, every guild info (name, ID, member count)
				Integer groupId = event.optInteger("group_owned");
				Long ownerId = bot.getDBUtil().group.getOwner(groupId);
				if (ownerId == null) {
					createError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
					return;
				}
				if (event.getGuild().getIdLong() != ownerId) {
					createError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
					return;
				}

				String groupName = bot.getDBUtil().group.getName(groupId);
				List<Long> memberIds = bot.getDBUtil().group.getGroupMembers(groupId);
				Integer groupSize = memberIds.size();

				EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
					.setAuthor(lu.getText(event, path+".embed_title").replace("{group_name}", groupName).replace("{group_id}", groupId.toString()))
					.setDescription(
						lu.getText(event, path+".embed_value").replace("{guild_name}", event.getGuild().getName())
						.replace("{guild_id}", String.valueOf(ownerId)).replace("{size}", groupSize.toString())
						.replace("{is_shared}", Emotes.CROSS_C.getEmote())
					);
				
				if (groupSize > 0) {
					String fieldLabel = lu.getText(event, path+".embed_guilds");
					StringBuffer buffer = new StringBuffer();
					String format = "%s | %s | `%s`";
					for (Long memberId : memberIds) {
						Guild guild = event.getJDA().getGuildById(memberId);
						if (guild == null) {
							guild = Optional.ofNullable(bot.getHelper()).map(helper -> helper.getJDA().getGuildById(memberId)).orElse(null);
							if (guild == null) continue;
						};
	
						String line = format.formatted(guild.getName(), guild.getMemberCount(), guild.getId());
						if (buffer.length() + line.length() + 2 > 1000) {
							builder.addField(fieldLabel, buffer.toString(), false);
							buffer.setLength(0);
							buffer.append(line+"\n");
							fieldLabel = "";
						} else {
							buffer.append(line+"\n");
						}
					}
					builder.addField(fieldLabel, buffer.toString(), false);
				}
				createReplyEmbed(event, builder.build());
			} else if (event.hasOption("group_joined")) {
				// View joined Group information - name, master name/ID, guild count
				Integer groupId = event.optInteger("group_joined");
				Long ownerId = bot.getDBUtil().group.getOwner(groupId);
				if (ownerId == null || !bot.getDBUtil().group.isMember(groupId, guildId)) {
					createError(event, path+".no_group", "Group ID: `%s`".formatted(groupId));
					return;
				}
				
				String groupName = bot.getDBUtil().group.getName(groupId);
				String masterName = event.getJDA().getGuildById(ownerId).getName();
				Integer groupSize = bot.getDBUtil().group.countMembers(groupId);

				EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
					.setAuthor(lu.getText(event, "logger_embed.group.title").replace("{group_name}", groupName).replace("{group_id}", groupId.toString()))
					.setDescription(lu.getText(event, path+".embed_value").replace("{guild_name}", masterName)
					.replace("{guild_id}", ownerId.toString()).replace("{size}", groupSize.toString())
					.replace("{is_shared}", Emotes.CROSS_C.getEmote()));
				createReplyEmbed(event, builder.build());
			} else {
				// No options provided - reply with all groups that this guild is connected
				List<Integer> ownedGroups = bot.getDBUtil().group.getOwnedGroups(guildId);
				List<Integer> joinedGroupIds = bot.getDBUtil().group.getGuildGroups(guildId);

				EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
					.setDescription("Group name | #ID");
				
				String fieldLabel = lu.getText(event, path+".embed_owned");
				if (ownedGroups.isEmpty()) {
					builder.addField(fieldLabel, lu.getText(event, path+".none"), false);
				} else {
					StringBuffer buffer = new StringBuffer();
					for (Integer groupId : ownedGroups) {
						buffer.append("%s | #%s\n".formatted(bot.getDBUtil().group.getName(groupId), groupId));
					}
					builder.addField(fieldLabel, buffer.toString(), false);
				}

				fieldLabel = lu.getText(event, path+".embed_member");
				if (joinedGroupIds.isEmpty()) {
					builder.addField(fieldLabel, lu.getText(event, path+".none"), false);
				} else {
					StringBuffer buffer = new StringBuffer();
					for (Integer groupId : joinedGroupIds) {
						buffer.append("%s | #%s\n".formatted(bot.getDBUtil().group.getName(groupId), groupId));
					}
					builder.addField(fieldLabel, buffer.toString(), false);
				}

				createReplyEmbed(event, builder.build());
			}
		}

	}

}


