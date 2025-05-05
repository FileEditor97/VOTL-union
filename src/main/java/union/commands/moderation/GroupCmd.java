package union.commands.moderation;

import java.net.URL;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.interactions.commands.Command;
import union.base.command.CooldownScope;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.base.waiter.EventWaiter;
import union.commands.CommandBase;
import union.helper.Helper;
import union.objects.AnticrashAction;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
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
import union.utils.invite.InviteImpl;

public class GroupCmd extends CommandBase {
	
	private final EventWaiter waiter;

	public GroupCmd(EventWaiter waiter) {
		this.name = "group";
		this.path = "bot.moderation.group";
		this.children = new SlashCommand[]{new Create(), new Delete(), new Add(), new Remove(), new Modify(), new Manage(), new View()};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.OPERATOR;
		this.waiter = waiter;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Create extends SlashCommand {
		public Create() {
			this.name = "create";
			this.path = "bot.moderation.group.create";
			this.options = List.of(
				new OptionData(OptionType.STRING, "name", lu.getText(path+".name.help"), true).setMaxLength(120),
				new OptionData(OptionType.STRING, "invite", lu.getText(path+".invite.help"))
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

			if (event.hasOption("invite")) {
				String link = event.optString("invite");

				if (!isValidURL(link)) {
					editError(event, path+".invalid_invite", "Received invalid URL: `%s`".formatted(link));
					return;
				}

				InviteImpl.resolve(event.getJDA(), link.replaceFirst("(https://)?(discord)?(\\.?gg/)?", "").trim(), false).queue(invite -> {
					if (!invite.isFromGuild() || invite.isTemporal() || invite.getGuild().getIdLong() != event.getGuild().getIdLong()) {
						editError(event, path+".invalid_invite", "Link `%s`".formatted(invite.getUrl()));
						return;
					}

					int groupId;
					try {
						groupId = bot.getDBUtil().group.create(guildId, groupName, invite.getUrl());
					} catch (SQLException e) {
						editErrorDatabase(event, e, "group create");
						return;
					}
					bot.getLogger().group.onCreation(event, groupId, groupName);

					sendSuccess(event, groupName, groupId, true);
				}, failure -> editError(event, path+".invalid_invite", "Link `%s`\n%s".formatted(link, failure.toString())));
			} else {
				int groupId;
				try {
					groupId = bot.getDBUtil().group.create(guildId, groupName, null);
				} catch (SQLException e) {
					editErrorDatabase(event, e, "group create");
					return;
				}
				bot.getLogger().group.onCreation(event, groupId, groupName);

				sendSuccess(event, groupName, groupId, false);
			}
		}

		private boolean isValidURL(String urlString) {
			try {
				URL url = new URL(urlString);
				url.toURI();
				return true;
			} catch (Exception e) {
				return false;
			}
		}

		private void sendSuccess(SlashCommandEvent event, String groupName, int groupId, boolean hasInvite) {
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(
					groupName, groupId, Constants.SUCCESS, hasInvite ? Constants.SUCCESS : Constants.FAILURE
				)).build());
		}
	}

	private class Delete extends SlashCommand {
		public Delete() {
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

			try {
				bot.getDBUtil().group.deleteGroup(groupId);
				bot.getLogger().group.onDeletion(event, groupId, groupName);
				bot.getDBUtil().group.clearGroup(groupId);
			} catch (SQLException e) {
				editErrorDatabase(event, e, "group delete & clear");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(
					lu.getText(event, path+".done").formatted(groupName, groupId)
				).build()
			);
		}
	}

	private class Add extends SlashCommand {
		public Add() {
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
				editErrorOther(event, ex.getMessage());
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

			try {
				Guild guild = Optional.ofNullable(event.getJDA().getGuildById(targetId))
					.or(() -> {
						return Optional.ofNullable(Helper.getInstance()).map(helper -> helper.getJDA().getGuildById(targetId));
					})
					.orElseThrow();

				bot.getDBUtil().group.add(groupId, targetId, canManage);
				bot.getLogger().group.onGuildAdded(event, groupId, groupName, targetId, guild.getName());

				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done").formatted(
						guild.getName(), targetId, groupName
					)).build()
				);
			} catch (NoSuchElementException e) {
				editError(event, path+".no_guild", "Server ID: `%d`".formatted(targetId));
			} catch (SQLException e) {
				editErrorDatabase(event, e, "group add server");
			}
		}
	}

	private class Remove extends SlashCommand {
		public Remove() {
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
					guild = Optional.ofNullable(Helper.getInstance()).map(helper -> helper.getJDA().getGuildById(id)).orElse(null);
				}
                return guild;
			}).filter(Objects::nonNull).toList();
			if (guilds.isEmpty()) {
				editError(event, path+".no_guilds");
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);
			MessageEmbed embed = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".embed_title"))
				.setDescription(lu.getText(event, path+".embed_value").formatted(groupName))
				.build();

			List<ActionRow> rows = new ArrayList<>();
			List<SelectOption> options = new ArrayList<>();
			for (Guild guild : guilds) {
				options.add(SelectOption.of("%s (%s)".formatted(guild.getName(), guild.getId()), guild.getId()));
				if (options.size() >= 25) {
					rows.add(ActionRow.of(
							StringSelectMenu.create("menu:select-guild:"+(rows.size()+1))
								.setPlaceholder("Select")
								.setMaxValues(1)
								.addOptions(options)
								.build()
						)
					);
					options = new ArrayList<>();
				}
			}
			if (!options.isEmpty()) {
				rows.add(ActionRow.of(
						StringSelectMenu.create("menu:select-guild:"+(rows.size()+1))
							.setPlaceholder("Select")
							.setMaxValues(1)
							.addOptions(options)
							.build()
					)
				);
			}
			event.getHook().editOriginalEmbeds(embed).setComponents(rows).queue(msg -> {
				waiter.waitForEvent(
					StringSelectInteractionEvent.class,
					e -> msg.getIdLong() == e.getMessageIdLong(),
					actionMenu -> {
						long targetId = Long.parseLong(actionMenu.getSelectedOptions().get(0).getValue());
						Guild targetGuild = event.getJDA().getGuildById(targetId);
						if (targetGuild == null)
							targetGuild = Optional.ofNullable(Helper.getInstance()).map(helper -> helper.getJDA().getGuildById(targetId)).orElse(null);

						try {
							bot.getDBUtil().group.remove(groupId, targetId);
						} catch (SQLException e) {
							editErrorDatabase(event, e, "group remove server");
							return;
						}
						if (targetGuild != null)
							bot.getLogger().group.onGuildRemoved(event, targetGuild, groupId, groupName);

						event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
							.setDescription(lu.getText(event, path+".done").formatted(
								Optional.ofNullable(targetGuild).map(Guild::getName).orElse("*Unknown*"),
								groupName
							)).build()
						).setComponents().queue();
					},
					30,
					TimeUnit.SECONDS,
					() -> {
						event.getHook().editOriginalComponents(
							ActionRow.of(StringSelectMenu.create("timed_out").setPlaceholder(lu.getText(event, "errors.timed_out")).setDisabled(true).build())
						).queue();
					}
				);
			});
		}
	}

	private class Modify extends SlashCommand {
		public Modify() {
			this.name = "modify";
			this.path = "bot.moderation.group.modify";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true)
					.setMinValue(0),
				new OptionData(OptionType.STRING, "name", lu.getText(path+".name.help"))
					.setMaxLength(120),
				new OptionData(OptionType.STRING, "invite", lu.getText(path+".invite.help")),
				new OptionData(OptionType.INTEGER, "enable_verification", lu.getText(path+".enable_verification.help"))
					.setRequiredRange(-1, 10),
				new OptionData(OptionType.INTEGER, "anticrash_action", lu.getText(path+".anticrash_action.help"))
					.addChoices(
						new Command.Choice("Disabled", 0),
						new Command.Choice("Remove all roles", 1),
						new Command.Choice("Kick", 2),
						new Command.Choice("Ban", 3)
					),
				new OptionData(OptionType.INTEGER, "anticrash_trigger", lu.getText(path+".anticrash_trigger.help"))
					.setRequiredRange(1, 20)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			int groupId = event.optInteger("group_owned");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null) {
				editError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
				return;
			}
			if (event.getGuild().getIdLong() != ownerId) {
				editError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
				return;
			}

			String currentGroupName = bot.getDBUtil().group.getName(groupId);
			if (event.hasOption("invite")) {
				String link = event.optString("invite");

				if (link.equalsIgnoreCase("null")) {
					try {
						bot.getDBUtil().group.setSelfInvite(groupId, null);
					} catch (SQLException e) {
						editErrorDatabase(event, e, "group invite");
						return;
					}

					editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setTitle(lu.getText(event, path+".done_title").formatted(currentGroupName))
						.setDescription(lu.getText(event, path+".invite_change").formatted("-none-"))
						.setFooter("Group ID: `%s`".formatted(groupId))
						.build()
					);
					return;
				}

				if (!isValidURL(link)) {
					editError(event, path+".invalid_invite", "Received invalid URL: `%s`".formatted(link));
					return;
				}

				InviteImpl.resolve(event.getJDA(), link.replaceFirst("(https://)?(discord)?(\\.?gg/)?", "").trim(), false).queue(invite -> {
					if (!invite.isFromGuild() || invite.isTemporal() || invite.getGuild().getIdLong() != event.getGuild().getIdLong()) {
						editError(event, path+".invalid_invite", "Link `%s`".formatted(invite.getUrl()));
						return;
					}

					try {
						bot.getDBUtil().group.setSelfInvite(groupId, invite.getUrl());
					} catch (SQLException e) {
						editErrorDatabase(event, e, "group invite");
						return;
					}

					editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setTitle(lu.getText(event, path+".done_title").formatted(currentGroupName))
						.setDescription(lu.getText(event, path+".invite_change").formatted(invite.getUrl()))
						.setFooter("Group ID: `%s`".formatted(groupId))
						.build()
					);
				},
					failure -> editError(event, path+".invalid_invite", "Link `%s`\n%s".formatted(link, failure.toString()))
				);
			} else {
				StringBuilder builder = new StringBuilder();

				if (event.hasOption("name")) {
					String newName = event.optString("name");

					try {
						bot.getDBUtil().group.rename(groupId, newName);
					} catch (SQLException e) {
						editErrorDatabase(event, e, "group rename");
						return;
					}
					bot.getLogger().group.onRenamed(event, currentGroupName, groupId, newName);

					builder.append(lu.getText(event, path+".name_change").formatted(newName))
						.append("\n");
				}
				if (event.hasOption("enable_verification")) {
					int verifyValue = event.optInteger("enable_verification");
					try {
						bot.getDBUtil().group.setVerify(groupId, verifyValue);
					} catch (SQLException e) {
						editErrorDatabase(event, e, "group verification");
						return;
					}

					builder.append(lu.getText(event, path+".verify_change").formatted(
						verifyValue==-1 ? Constants.FAILURE : (verifyValue==0 ? "Kick" : "Ban for "+verifyValue)
					)).append("\n");
				}
				if (event.hasOption("anticrash_action")) {
					AnticrashAction actionGroup = AnticrashAction.byValue(event.optInteger("anticrash_action"));
					try {
						bot.getDBUtil().group.setAnticrashAction(groupId, actionGroup);
					} catch (SQLException e) {
						editErrorDatabase(event, e, "setup group anticrash");
						return;
					}
					// Clear anticrash cache (easier to purge all cache, than for of each member)
					bot.getDBUtil().guildSettings.purgeAnticrashCache();

					builder.append(lu.getText(event, path+".anticrash_change").formatted(actionGroup.name().toLowerCase()))
						.append("\n");
				}
				if (event.hasOption("anticrash_trigger")) {
					int triggerAmount = event.optInteger("anticrash_trigger");
					try {
						bot.getDBUtil().group.setAnticrashTrigger(groupId, triggerAmount);
					} catch (SQLException e) {
						editErrorDatabase(event, e, "group anticrash trigger");
						return;
					}

					builder.append(lu.getText(event, path+".trigger_change").formatted(triggerAmount))
						.append("\n");
				}

				if (builder.isEmpty()) {
					editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_WARNING)
						.setDescription(lu.getText(event, path+".no_options"))
						.setFooter("Group ID: `%s`".formatted(groupId))
						.build()
					);
				} else {
					editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setTitle(lu.getText(event, path+".done_title").formatted(currentGroupName))
						.setDescription(builder.toString())
						.setFooter("Group ID: #%s".formatted(groupId))
						.build()
					);
				}
			}
		}

		private boolean isValidURL(String urlString) {
			try {
				URL url = new URL(urlString);
				url.toURI();
				return true;
			} catch (Exception e) {
				return false;
			}
		}
	}

	private class Manage extends SlashCommand {
		public Manage() {
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

			final boolean canManage = event.optBoolean("manage");

			List<Guild> guilds = bot.getDBUtil().group.getGroupMembers(groupId).stream().map(id -> {
				Guild guild = event.getJDA().getGuildById(id);
				if (guild == null) {
					guild = Optional.ofNullable(Helper.getInstance()).map(helper -> helper.getJDA().getGuildById(id)).orElse(null);
				}
				return guild;
			}).filter(Objects::nonNull).toList();
			if (guilds.isEmpty()) {
				editError(event, path+".no_guilds");
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);
			MessageEmbed embed = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".embed_title"))
				.setDescription(lu.getText(event, path+".embed_value"))
				.build();

			List<ActionRow> rows = new ArrayList<>();
			List<SelectOption> options = new ArrayList<>();
			for (Guild guild : guilds) {
				options.add(SelectOption.of("%s (%s)".formatted(guild.getName(), guild.getId()), guild.getId()));
				if (options.size() >= 25) {
					rows.add(ActionRow.of(
						StringSelectMenu.create("menu:select-guild:"+(rows.size()+1))
							.setPlaceholder("Select")
							.setMaxValues(1)
							.addOptions(options)
							.build()
						)
					);
					options = new ArrayList<>();
				}
			}
			if (!options.isEmpty()) {
				rows.add(ActionRow.of(
					StringSelectMenu.create("menu:select-guild:"+(rows.size()+1))
						.setPlaceholder("Select")
						.setMaxValues(1)
						.addOptions(options)
						.build()
					)
				);
			}

			event.getHook().editOriginalEmbeds(embed).setComponents(rows).queue(msg -> {
				waiter.waitForEvent(
					StringSelectInteractionEvent.class,
					e -> msg.getIdLong() == e.getMessageIdLong(),
					actionMenu -> {
						long targetId = Long.parseLong(actionMenu.getSelectedOptions().get(0).getValue());
						Guild targetGuild = event.getJDA().getGuildById(targetId);
						if (targetGuild == null) {
							targetGuild = Optional.ofNullable(Helper.getInstance()).map(helper -> helper.getJDA().getGuildById(targetId)).orElse(null);
						}

						StringBuilder builder = new StringBuilder(lu.getText(event, path+".done")
							.formatted(targetGuild.getName(), groupName));

						try {
							bot.getDBUtil().group.setManage(groupId, targetId, canManage);
						} catch (SQLException e) {
							editErrorDatabase(event, e, "group can mange");
							return;
						}
						builder.append(lu.getText(event, path+".manage_change").formatted(canManage ? Constants.SUCCESS : Constants.FAILURE));

						event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
							.setDescription(builder.toString()).build()
						).setComponents().queue();
					},
					30,
					TimeUnit.SECONDS,
					() -> {
						event.getHook().editOriginalComponents(
							ActionRow.of(StringSelectMenu.create("timed_out").setPlaceholder(lu.getText(event, "errors.timed_out")).setDisabled(true).build())
						).queue();
					}
				);
			});
		}
	}

	private class View extends SlashCommand {
		public View() {
			this.name = "view";
			this.path = "bot.moderation.group.view";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), false, true).setMinValue(0),
				new OptionData(OptionType.INTEGER, "group_joined", lu.getText(path+".group_joined.help"), false, true).setMinValue(0)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			long guildId = event.getGuild().getIdLong();
			if (event.hasOption("group_owned")) {
				// View owned Group information - name, every guild info (name, ID, member count)
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
				List<Long> memberIds = bot.getDBUtil().group.getGroupMembers(groupId);
				int groupSize = memberIds.size();
				int verifyValue = bot.getDBUtil().group.getVerifyValue(groupId);

				EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
					.setAuthor(lu.getText(event, path+".embed_title").formatted(
						groupName, groupId
					))
					.setDescription(lu.getText(event, path+".embed_full").formatted(
						event.getGuild().getName(), event.getGuild().getId(), groupSize,
						Optional.ofNullable(bot.getDBUtil().group.getSelfInvite(groupId)).orElse("-"),
						Optional.ofNullable(bot.getSettings().getAppealGuildId()).map(String::valueOf).orElse("-"),
						verifyValue==-1 ? Constants.FAILURE : (verifyValue==0 ? "Kick" : "Ban for "+verifyValue),
						bot.getDBUtil().group.getAnticrashAction(groupId).name().toLowerCase(),
						bot.getDBUtil().group.getAnticrashTrigger(groupId)
					));
				
				if (groupSize > 0) {
					String fieldLabel = lu.getText(event, path+".embed_guilds");
					StringBuilder stringBuilder = new StringBuilder();
					String format = "%s | %s | `%s`";
					for (Long memberId : memberIds) {
						Guild guild = event.getJDA().getGuildById(memberId);
						if (guild == null) {
							guild = Optional.ofNullable(Helper.getInstance()).map(helper -> helper.getJDA().getGuildById(memberId)).orElse(null);
							if (guild == null) continue;
						}
	
						String line = format.formatted(guild.getName(), guild.getMemberCount(), guild.getId());
						if (stringBuilder.length() + line.length() + 2 > 1000) {
							builder.addField(fieldLabel, stringBuilder.toString(), false);
							stringBuilder.setLength(0);
							stringBuilder.append(line).append("\n");
							fieldLabel = "";
						} else {
							stringBuilder.append(line).append("\n");
						}
					}
					builder.addField(fieldLabel, stringBuilder.toString(), false);
				}
				editEmbed(event, builder.build());
			} else if (event.hasOption("group_joined")) {
				// View joined Group information - name, master name/ID, guild count
				Integer groupId = event.optInteger("group_joined");
				Long ownerId = bot.getDBUtil().group.getOwner(groupId);
				if (ownerId == null || !bot.getDBUtil().group.isMember(groupId, guildId)) {
					editError(event, path+".no_group", "Group ID: `%s`".formatted(groupId));
					return;
				}
				
				String groupName = bot.getDBUtil().group.getName(groupId);
				String masterName = event.getJDA().getGuildById(ownerId).getName();
				int groupSize = bot.getDBUtil().group.countMembers(groupId);

				EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
					.setAuthor(lu.getText(event, "logger.groups.title").formatted(groupName, groupId))
					.setDescription(lu.getText(event, path+".embed_short").formatted(
						masterName, ownerId, groupSize
					));
				editEmbed(event, builder.build());
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
					StringBuilder stringBuilder = new StringBuilder();
					for (Integer groupId : ownedGroups) {
						stringBuilder.append("%s | #%s\n".formatted(bot.getDBUtil().group.getName(groupId), groupId));
					}
					builder.addField(fieldLabel, stringBuilder.toString(), false);
				}

				fieldLabel = lu.getText(event, path+".embed_member");
				if (joinedGroupIds.isEmpty()) {
					builder.addField(fieldLabel, lu.getText(event, path+".none"), false);
				} else {
					StringBuilder stringBuilder = new StringBuilder();
					for (Integer groupId : joinedGroupIds) {
						stringBuilder.append("%s | #%s\n".formatted(bot.getDBUtil().group.getName(groupId), groupId));
					}
					builder.addField(fieldLabel, stringBuilder.toString(), false);
				}

				editEmbed(event, builder.build());
			}
		}
	}

}
