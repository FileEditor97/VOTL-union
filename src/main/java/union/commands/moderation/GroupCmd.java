package union.commands.moderation;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import union.App;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.Emotes;
import union.objects.command.CooldownScope;
import union.objects.command.SlashCommand;
import union.objects.command.SlashCommandEvent;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;

public class GroupCmd extends CommandBase {
	
	private static EventWaiter waiter;

	public GroupCmd(App bot, EventWaiter waiter) {
		super(bot);
		this.name = "group";
		this.path = "bot.moderation.group";
		this.children = new SlashCommand[]{new Create(bot), new Delete(bot), new Add(bot), new Join(bot), new Leave(bot), new Remove(bot), new Rename(bot), new Manage(bot), new View(bot)};
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
				new OptionData(OptionType.BOOLEAN, "shared", lu.getText(path+".shared.help"), false)
			);
			this.cooldown = 30;
			this.cooldownScope = CooldownScope.GUILD;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String guildId = event.getGuild().getId();
			if (bot.getDBUtil().group.getOwnedGroups(guildId).size() >= 3) {
				createError(event, path+".max_amount");
				return;
			}

			String groupName = event.optString("name");
			Boolean isShared = event.optBoolean("shared", false);

			bot.getDBUtil().group.create(guildId, groupName, isShared);
			Integer groupId = bot.getDBUtil().group.getIncrement();
			bot.getLogListener().onGroupCreation(event, groupId, groupName);

			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS)
				.setDescription(
					lu.getText(event, path+".done").replace("{group_name}", groupName).replace("{group_id}", groupId.toString())
					.replace("{is_shared}", (isShared ? Emotes.CHECK_C.getEmote() : Emotes.CROSS_C.getEmote()))
				)
				.build();
			createReplyEmbed(event, embed);
		}

	}

	private class Delete extends SlashCommand {

		public Delete(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "delete";
			this.path = "bot.moderation.group.delete";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true).setMinValue(0)
			);
			this.cooldown = 30;
			this.cooldownScope = CooldownScope.GUILD;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer groupId = event.optInteger("group_owned");
			String masterId = bot.getDBUtil().group.getMaster(groupId);
			if (masterId == null) {
				createError(event, path+".no_group", "Group ID: `%s`".formatted(groupId));
				return;
			}
			if (!event.getGuild().getId().equals(masterId)) {
				createError(event, path+".not_owned", "Group ID: `%s`\nGroup owner's ID: `%s`".formatted(groupId, masterId));
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);

			bot.getDBUtil().group.delete(groupId);
			bot.getLogListener().onGroupDeletion(event, groupId, groupName);

			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS)
				.setDescription(
					lu.getText(event, path+".done").replace("{group_name}", groupName).replace("{group_id}", groupId.toString())
				)
				.build();
			createReplyEmbed(event, embed);
		}

	}

	private class Add extends SlashCommand {

		// TODO: use invite system, to confirm that selected server is WILLFUL to join server Group
		// for currect requirement is enough, but as major release - NO
		public Add(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "add";
			this.path = "bot.moderation.group.add";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true).setMinValue(0),
				new OptionData(OptionType.STRING, "server", lu.getText(path+".server.help"), true).setRequiredLength(16, 20),
				new OptionData(OptionType.BOOLEAN, "manage", lu.getText(path+".manage.help"), false)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer groupId = event.optInteger("group_owned");
			String masterId = bot.getDBUtil().group.getMaster(groupId);
			if (masterId == null) {
				createError(event, path+".no_group", "Group ID: `%s`".formatted(groupId));
				return;
			}
			if (!event.getGuild().getId().equals(masterId)) {
				createError(event, path+".not_owned", "Group ID: `%s`\nGroup owner's ID: `%s`".formatted(groupId, masterId));
				return;
			}

			String targetId = event.optString("server");
			if (event.getGuild().getId().equals(targetId)) {
				createError(event, path+".failed_join", "This server is this Group's owner.\nGroup ID: `%s`".formatted(groupId));
				return;
			}
			if (bot.getDBUtil().group.alreadyMember(groupId, targetId)) {
				createError(event, path+".is_member", "Group ID: `%s`".formatted(groupId));
				return;
			}
			Boolean canManage = event.optBoolean("manage", false);
			if (bot.getDBUtil().group.isShared(groupId) && canManage) canManage = false;

			// Search for server in both bots
			String groupName = bot.getDBUtil().group.getName(groupId);
			Guild guild = null;
			try {
				guild = event.getJDA().getGuildById(targetId);
			} catch (NumberFormatException ex) {
				createError(event, path+".no_guild", "Server ID: `%s`".formatted(targetId));
				return;
			} 
			if (guild == null) {
				guild = Optional.ofNullable(bot.getHelper()).map(helper -> helper.getJDA().getGuildById(targetId)).orElse(null);
				if (guild == null) {
					createError(event, path+".no_guild", "Server ID: `%s`".formatted(targetId));
					return;
				} else {
					bot.getDBUtil().group.add(groupId, targetId, canManage);
					bot.getLogListener().onGroupAdded(event, groupId, groupName, targetId, guild.getName());
				}
			} else {
				bot.getDBUtil().group.add(groupId, targetId, canManage);
				bot.getLogListener().onGroupAdded(event, groupId, groupName, targetId, guild.getName());
			}

			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS)
				.setDescription(
					lu.getText(event, path+".done").replace("{server_id}", targetId).replace("{server_name}", guild.getName())
						.replace("{group_name}", groupName)
				)
				.build();
			createReplyEmbed(event, embed);
		}

	}

	private class Join extends SlashCommand {

		public Join(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "join";
			this.path = "bot.moderation.group.join";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "id", lu.getText(path+".id.help"), true).setMinValue(0)
			);
			this.cooldown = 30;
			this.cooldownScope = CooldownScope.GUILD;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String guildId = event.getGuild().getId();
			if (bot.getDBUtil().group.getGuildGroups(guildId).size() >= 5) {
				createError(event, path+".max_amount");
				return;
			}

			Integer groupId = event.optInteger("id");
			String masterId = bot.getDBUtil().group.getMaster(groupId);
			if (masterId == null) {
				createError(event, path+".no_group", "Group ID: `%s`".formatted(groupId));
				return;
			}
			if (event.getGuild().getId().equals(masterId)) {
				createError(event, path+".failed_join", "This server is this Group's owner.\nGroup ID: `%s`".formatted(groupId));
				return;
			}
			if (!bot.getDBUtil().group.isShared(groupId)) {
				createError(event, path+".failed_join", "Can't join this Group.\nGroup ID: `%s`".formatted(groupId));
				return;
			}
			if (bot.getDBUtil().group.alreadyMember(groupId, guildId)) {
				createError(event, path+".is_member", "Group ID: `%s`".formatted(groupId));
				return;
			}

			event.deferReply(true).queue();
			String groupName = bot.getDBUtil().group.getName(groupId);
			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getText(event, path+".embed_title"))
				.setDescription(lu.getText(event, path+".embed_value").replace("{group_name}", groupName).replace("{group_id}", groupId.toString()))
				.build();
			ActionRow buttons = ActionRow.of(
				Button.of(ButtonStyle.PRIMARY, "button:confirm", lu.getText(event, path+".button_confirm")),
				Button.of(ButtonStyle.DANGER, "button:abort", lu.getText(event, path+".button_abort"))
			);
			event.getHook().editOriginalEmbeds(embed).setComponents(buttons).queue(msg -> {
				waiter.waitForEvent(
					ButtonInteractionEvent.class,
					e -> msg.getId().equals(e.getMessageId()) && (e.getComponentId().equals("button:confirm") || e.getComponentId().equals("button:abort")),
					action -> {
					EmbedBuilder embedEdit = bot.getEmbedUtil().getEmbed(event);
						if (action.getComponentId().equals("button:confirm")) {
							bot.getDBUtil().group.add(groupId, guildId, false);
							bot.getLogListener().onGroupJoin(event, groupId, groupName);

							embedEdit.setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path+".done").replace("{group_name}", groupName));
							event.getHook().editOriginalEmbeds(embedEdit.build()).setComponents().queue();
						} else {
							embedEdit.setColor(Constants.COLOR_FAILURE).setDescription(lu.getText(event, path+".abort"));
							event.getHook().editOriginalEmbeds(embedEdit.build()).setComponents().queue();
						}
					},
					20,
					TimeUnit.SECONDS,
					() -> {
						event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(event).setDescription(lu.getText(event, path+".abort")).setColor(Constants.COLOR_FAILURE).build())
							.setComponents().queue();
					}
				);
			});
		}

	}

	private class Leave extends SlashCommand {

		public Leave(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "leave";
			this.path = "bot.moderation.group.leave";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_joined", lu.getText(path+".group_joined.help"), true, true).setMinValue(0)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String guildId = event.getGuild().getId();
			Integer groupId = event.optInteger("group_joined");
			String masterId = bot.getDBUtil().group.getMaster(groupId);
			if (masterId == null || !bot.getDBUtil().group.alreadyMember(groupId, guildId)) {
				createError(event, path+".no_group", "Group ID: `%s`".formatted(groupId));
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);

			bot.getDBUtil().group.remove(groupId, guildId);
			bot.getLogListener().onGroupLeave(event, groupId, groupName);

			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS)
				.setDescription(
					lu.getText(event, path+".done").replace("{group_name}", groupName).replace("{group_id}", groupId.toString())
				)
				.build();
			event.replyEmbeds(embed).queue();
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
			Integer groupId = event.optInteger("group_owned");
			String masterId = bot.getDBUtil().group.getMaster(groupId);
			if (masterId == null) {
				createError(event, path+".no_group", "Group ID: `%s`".formatted(groupId));
				return;
			}
			if (!event.getGuild().getId().equals(masterId)) {
				createError(event, path+".not_owned", "Group ID: `%s`\nGroup owner's ID: `%s`".formatted(groupId, masterId));
				return;
			}

			List<String> guildIds = bot.getDBUtil().group.getGroupGuildIds(groupId);
			if (guildIds.isEmpty()) {
				createError(event, path+".no_guilds");
				return;
			}

			event.deferReply(true).queue();
			String groupName = bot.getDBUtil().group.getName(groupId);
			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getText(event, path+".embed_title"))
				.setDescription(lu.getText(event, path+".embed_value").replace("{group_name}", groupName))
				.build();
			StringSelectMenu menu = StringSelectMenu.create("menu:remove-guild")
				.setPlaceholder("Select")
				.setMaxValues(1)
				.addOptions(guildIds.stream().map(
					guildId -> {
						String guildName = event.getJDA().getGuildById(guildId).getName();
						return SelectOption.of("%s (%s)".formatted(guildName, guildId), guildId);
					}
				).collect(Collectors.toList()))
				.build();
			event.getHook().editOriginalEmbeds(embed).setActionRow(menu).queue(msg -> {
				waiter.waitForEvent(
					StringSelectInteractionEvent.class,
					e -> e.getComponentId().equals("menu:remove-guild") && e.getMessageId().equals(msg.getId()),
					actionMenu -> {
						String targetId = actionMenu.getSelectedOptions().get(0).getValue();
						Guild targetGuild = event.getJDA().getGuildById(targetId);

						bot.getDBUtil().group.remove(groupId, targetId);
						bot.getLogListener().onGroupRemove(event, targetGuild, groupId, groupName);

						MessageEmbed editEmbed = bot.getEmbedUtil().getEmbed(event)
							.setColor(Constants.COLOR_SUCCESS)
							.setDescription(lu.getText(event, path+".done").replace("{guild_name}", targetGuild.getName()).replace("{group_name}", groupName))
							.build();
						event.getHook().editOriginalEmbeds(editEmbed).setComponents().queue();
					},
					30,
					TimeUnit.SECONDS,
					() -> {
						event.getHook().editOriginalComponents(
							ActionRow.of(menu.createCopy().setPlaceholder(lu.getText(event, path+".timed_out")).setDisabled(true).build())
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
			String masterId = bot.getDBUtil().group.getMaster(groupId);
			if (masterId == null) {
				createError(event, path+".no_group", "Group ID: `%s`".formatted(groupId));
				return;
			}
			if (!event.getGuild().getId().equals(masterId)) {
				createError(event, path+".not_owned", "Group ID: `%s`\nGroup owner's ID: `%s`".formatted(groupId, masterId));
				return;
			}

			String oldName = bot.getDBUtil().group.getName(groupId);
			String newName = event.optString("name");

			bot.getDBUtil().group.rename(groupId, newName);
			bot.getLogListener().onGroupRename(event, oldName, groupId, newName);

			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS)
				.setDescription(
					lu.getText(event, path+".done").replace("{old_name}", oldName).replace("{new_name}", newName)
					.replace("{group_id}", groupId.toString())
				)
				.build();
			createReplyEmbed(event, embed);
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
			Integer groupId = event.optInteger("group_owned");
			String masterId = bot.getDBUtil().group.getMaster(groupId);
			if (masterId == null) {
				createError(event, path+".no_group", "Group ID: `%s`".formatted(groupId));
				return;
			}
			if (!event.getGuild().getId().equals(masterId)) {
				createError(event, path+".not_owned", "Group ID: `%s`\nGroup owner's ID: `%s`".formatted(groupId, masterId));
				return;
			}

			List<String> guildIds = bot.getDBUtil().group.getGroupGuildIds(groupId);
			if (guildIds.isEmpty()) {
				createError(event, path+".no_guilds");
				return;
			}

			Boolean canManage = event.optBoolean("manage", false);
			if (bot.getDBUtil().group.isShared(groupId) && canManage) {
				createError(event, path+".is_shared");
				return;
			};

			event.deferReply(true).queue();
			String groupName = bot.getDBUtil().group.getName(groupId);
			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getText(event, path+".embed_title"))
				.setDescription(lu.getText(event, path+".embed_value").replace("{group_name}", groupName))
				.build();
			StringSelectMenu menu = StringSelectMenu.create("menu:select-guild")
				.setPlaceholder("Select")
				.setMaxValues(1)
				.addOptions(guildIds.stream().map(
					guildId -> {
						String guildName = event.getJDA().getGuildById(guildId).getName();
						return SelectOption.of("%s (%s)".formatted(guildName, guildId), guildId);
					}
				).collect(Collectors.toList()))
				.build();
			event.getHook().editOriginalEmbeds(embed).setActionRow(menu).queue(msg -> {
				waiter.waitForEvent(
					StringSelectInteractionEvent.class,
					e -> e.getComponentId().equals("menu:select-guild") && e.getMessageId().equals(msg.getId()),
					actionMenu -> {
						String targetId = actionMenu.getSelectedOptions().get(0).getValue();
						Guild targetGuild = event.getJDA().getGuildById(targetId);

						bot.getDBUtil().group.setManage(groupId, targetId, canManage);

						MessageEmbed editEmbed = bot.getEmbedUtil().getEmbed(event)
							.setColor(Constants.COLOR_SUCCESS)
							.setDescription(
								lu.getText(event, path+".done").replace("{guild_name}", targetGuild.getName()).replace("{group_name}", groupName)
								.replace("{manage}", canManage.toString())
							)
							.build();
						event.getHook().editOriginalEmbeds(editEmbed).setComponents().queue();
					},
					30,
					TimeUnit.SECONDS,
					() -> {
						event.getHook().editOriginalComponents(
							ActionRow.of(menu.createCopy().setPlaceholder(lu.getText(event, path+".timed_out")).setDisabled(true).build())
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
			String guildId = event.getGuild().getId();
			if (event.hasOption("group_owned")) {
				// View owned Group information - name, every guild info (name, ID, member count)
				Integer groupId = event.optInteger("group_owned");
				String masterId = bot.getDBUtil().group.getMaster(groupId);
				if (masterId == null) {
					createError(event, path+".no_group", "Group ID: `%s`".formatted(groupId));
					return;
				}
				if (!event.getGuild().getId().equals(masterId)) {
					createError(event, path+".not_owned", "Group ID: `%s`\nGroup owner's ID: `%s`".formatted(groupId, masterId));
					return;
				}

				String groupName = bot.getDBUtil().group.getName(groupId);
				List<String> groupGuildIds = bot.getDBUtil().group.getGroupGuildIds(groupId);
				Integer groupSize = groupGuildIds.size();

				EmbedBuilder builder = new EmbedBuilder(bot.getEmbedUtil().getEmbed(event))
					.setAuthor(lu.getText(event, path+".embed_title").replace("{group_name}", groupName).replace("{group_id}", groupId.toString()))
					.setDescription(
						lu.getText(event, path+".embed_value").replace("{guild_name}", event.getGuild().getName())
						.replace("{guild_id}", masterId).replace("{size}", groupSize.toString())
						.replace("{is_shared}", (bot.getDBUtil().group.isShared(groupId) ? Emotes.CHECK_C.getEmote() : Emotes.CROSS_C.getEmote()))
					);
				
				if (groupSize > 0) {
					String fieldLabel = lu.getText(event, path+".embed_guilds");
					StringBuffer buffer = new StringBuffer();
					String format = "%s | %s | `%s`";
					for (String groupGuildId : groupGuildIds) {
						Guild guild = event.getJDA().getGuildById(groupGuildId);
						if (guild == null) {
							guild = Optional.ofNullable(bot.getHelper()).map(helper -> helper.getJDA().getGuildById(groupGuildId)).orElse(null);
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
				String masterId = bot.getDBUtil().group.getMaster(groupId);
				if (masterId == null || !bot.getDBUtil().group.alreadyMember(groupId, guildId)) {
					createError(event, path+".no_group", "Group ID: `%s`".formatted(groupId));
					return;
				}
				
				String groupName = bot.getDBUtil().group.getName(groupId);
				String masterName = event.getJDA().getGuildById(masterId).getName();
				Integer groupSize = bot.getDBUtil().group.getGroupGuildIds(groupId).size();

				EmbedBuilder builder = new EmbedBuilder(bot.getEmbedUtil().getEmbed(event))
					.setAuthor(lu.getText(event, "logger.group.title").replace("{group_name}", groupName).replace("{group_id}", groupId.toString()))
					.setDescription(lu.getText(event, path+".embed_value").replace("{guild_name}", masterName).replace("{guild_id}", masterId).replace("{size}", groupSize.toString()));
				createReplyEmbed(event, builder.build());
			} else {
				// No options provided - reply with all groups that this guild is connected
				List<Integer> ownedGroups = bot.getDBUtil().group.getOwnedGroups(guildId);
				List<Integer> joinedGroupIds = bot.getDBUtil().group.getGuildGroups(guildId);

				EmbedBuilder builder = new EmbedBuilder(bot.getEmbedUtil().getEmbed(event))
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


