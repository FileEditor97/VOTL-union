package union.commands.moderation;

import java.net.URL;
import java.util.ArrayList;
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
	
	private static EventWaiter waiter;

	public GroupCmd(App bot, EventWaiter waiter) {
		super(bot);
		this.name = "group";
		this.path = "bot.moderation.group";
		this.children = new SlashCommand[]{new Create(bot), new Delete(bot), new Add(bot), new Remove(bot), new Modify(bot), new Manage(bot), new View(bot)};
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
				new OptionData(OptionType.STRING, "appeal_server", lu.getText(path+".appeal_server.help")).setRequiredLength(12, 20),
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

			if (event.hasOption("invite")) {
				String link = event.optString("invite");

				if (!isValidURL(link)) {
					createError(event, path+".invalid_invite", "Received invalid URL: `%s`".formatted(link));
					return;
				}

				long appealGuildIdTemp = appealGuildId;
				InviteImpl.resolve(bot.JDA, link.replaceFirst("(https://)?(discord)?(\\.?gg/)?", "").trim(), false).queue(invite -> {
					if (!invite.isFromGuild() || invite.isTemporal() || invite.getGuild().getIdLong() != event.getGuild().getIdLong()) {
						editError(event, path+".invalid_invite", "Link `%s`".formatted(invite.getUrl()));
						return;
					}
					bot.getDBUtil().group.create(guildId, groupName, appealGuildIdTemp, invite.getUrl());
					int groupId = bot.getDBUtil().group.getIncrement();
					bot.getLogger().group.onCreation(event, groupId, groupName);

					sendSuccess(event, groupName, groupId, true);
				}, failure -> editError(event, path+".invalid_invite", "Link `%s`\n%s".formatted(link, failure.toString())));
			} else {
				bot.getDBUtil().group.create(guildId, groupName, appealGuildId, null);
				int groupId = bot.getDBUtil().group.getIncrement();
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
			editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(
					groupName, groupId, Constants.SUCCESS, hasInvite ? Constants.SUCCESS : Constants.FAILURE
				)).build());
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
			bot.getLogger().group.onDeletion(event, groupId, groupName);

			editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(
					lu.getText(event, path+".done").formatted(groupName, groupId)
				).build()
			);
		}
	}

	private class Add extends SlashCommand {
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
			Guild guild;
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
					bot.getLogger().group.onGuildAdded(event, groupId, groupName, targetId, guild.getName());
				}
			} else {
				bot.getDBUtil().group.add(groupId, targetId, canManage);
				bot.getLogger().group.onGuildAdded(event, groupId, groupName, targetId, guild.getName());
			}

			editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(
					guild.getName(), targetId, groupName
				)).build()
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
					e -> e.getMessageId().equals(msg.getId()),
					actionMenu -> {
						long targetId = Long.parseLong(actionMenu.getSelectedOptions().get(0).getValue());
						Guild targetGuild = event.getJDA().getGuildById(targetId);
						if (targetGuild == null)
							targetGuild = Optional.ofNullable(bot.getHelper()).map(helper -> helper.getJDA().getGuildById(targetId)).orElse(null);

						bot.getDBUtil().group.remove(groupId, targetId);
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
		public Modify(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "modify";
			this.path = "bot.moderation.group.modify";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true)
					.setMinValue(0),
				new OptionData(OptionType.STRING, "name", lu.getText(path+".name.help"))
					.setMaxLength(120),
				new OptionData(OptionType.STRING, "appeal_server", lu.getText(path+".appeal_server.help"))
					.setRequiredLength(12, 20),
				new OptionData(OptionType.STRING, "invite", lu.getText(path+".invite.help")),
				new OptionData(OptionType.INTEGER, "enable_verification", lu.getText(path+".enable_verification.help"))
					.setRequiredRange(-1, 10)
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

			if (event.getOptions().size() != 2) {
				editError(event, path+".only_one_change");
				return;
			}

			if (event.hasOption("name")) {
				String oldName = bot.getDBUtil().group.getName(groupId);
				String newName = event.optString("name");

				bot.getDBUtil().group.rename(groupId, newName);
				bot.getLogger().group.onRenamed(event, oldName, groupId, newName);

				editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done").formatted(
						oldName, lu.getText(event, path+".name_change").formatted(newName), groupId
					)).build()
				);
			}
			else if (event.hasOption("appeal_server")) {
				long appealGuildId;

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

				bot.getDBUtil().group.setAppealGuildId(groupId, appealGuildId);

				String groupName = bot.getDBUtil().group.getName(groupId);
				editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done").formatted(
						groupName, lu.getText(event, path+".appeal_change").formatted(appealGuildId), groupId
					)).build()
				);
			}
			else if (event.hasOption("invite")) {
				String link = event.optString("invite");
				String groupName = bot.getDBUtil().group.getName(groupId);

				if (link.equalsIgnoreCase("null")) {
					bot.getDBUtil().group.setSelfInvite(groupId, null);

					editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, path+".done").formatted(
							groupName, lu.getText(event, path+".invite_change").formatted("- none -"), groupId
						)).build()
					);
					return;
				}

				if (!isValidURL(link)) {
					editError(event, path+".invalid_invite", "Received invalid URL: `%s`".formatted(link));
					return;
				}

				InviteImpl.resolve(bot.JDA, link.replaceFirst("(https://)?(discord)?(\\.?gg/)?", "").trim(), false).queue(invite -> {
					if (!invite.isFromGuild() || invite.isTemporal() || invite.getGuild().getIdLong() != event.getGuild().getIdLong()) {
						editError(event, path+".invalid_invite", "Link `%s`".formatted(invite.getUrl()));
						return;
					}

					bot.getDBUtil().group.setSelfInvite(groupId, invite.getUrl());

					editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, path+".done").formatted(
							groupName, lu.getText(event, path+".invite_change").formatted(invite.getUrl()), groupId
						)).build()
					);
				}, failure -> editError(event, path+".invalid_invite", "Link `%s`\n%s".formatted(link, failure.toString())));
			}
			else if (event.hasOption("enable_verification")) {
				int verifyValue = event.optInteger("enable_verification");
				bot.getDBUtil().group.setVerify(groupId, verifyValue);

				String groupName = bot.getDBUtil().group.getName(groupId);
				String text = verifyValue==-1 ? Constants.FAILURE : (verifyValue==0 ? "Kick" : "Ban for "+verifyValue);
				editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done").formatted(
						groupName, lu.getText(event, path+".verify_change").formatted(text), groupId
					))
					.build()
				);
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
		public Manage(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "manage";
			this.path = "bot.moderation.group.manage";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true).setMinValue(0),
				new OptionData(OptionType.BOOLEAN, "manage", lu.getText(path+".manage.help"))
//				new OptionData(OptionType.BOOLEAN, "enable_verification", lu.getText(path+".enable_verification.help"))
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

			final Boolean canManage;
			if (event.hasOption("manage")) canManage = event.optBoolean("manage");
			else canManage = null;

			if (canManage==null) {
				editError(event, path+".no_options");
				return;
			}

			List<Guild> guilds = bot.getDBUtil().group.getGroupMembers(groupId).stream().map(id -> {
				Guild guild = event.getJDA().getGuildById(id);
				if (guild == null) {
					guild = Optional.ofNullable(bot.getHelper()).map(helper -> helper.getJDA().getGuildById(id)).orElse(null);
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
					e -> e.getMessageId().equals(msg.getId()),
					actionMenu -> {
						long targetId = Long.parseLong(actionMenu.getSelectedOptions().get(0).getValue());
						Guild targetGuild = event.getJDA().getGuildById(targetId);
						if (targetGuild == null) {
							targetGuild = Optional.ofNullable(bot.getHelper()).map(helper -> helper.getJDA().getGuildById(targetId)).orElse(null);
						}

						StringBuilder builder = new StringBuilder(lu.getText(event, path+".done")
							.formatted(targetGuild.getName(), groupName));

						if (canManage!=null) {
							bot.getDBUtil().group.setManage(groupId, targetId, canManage);
							builder.append(lu.getText(event, path+".manage_change").formatted(canManage ? Constants.SUCCESS : Constants.FAILURE));
						}

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
				int groupSize = memberIds.size();
				int verifyValue = bot.getDBUtil().group.getVerifyValue(groupId);

				EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
					.setAuthor(lu.getText(event, path+".embed_title").formatted(
						groupName, groupId
					))
					.setDescription(lu.getText(event, path+".embed_full").formatted(
						event.getGuild().getName(), event.getGuild().getId(), groupSize,
						Optional.ofNullable(bot.getDBUtil().group.getSelfInvite(groupId)).orElse("-"),
						Optional.ofNullable(bot.getDBUtil().group.getAppealGuildId(groupId)).map(String::valueOf).orElse("-"),
						verifyValue==-1 ? Constants.FAILURE : (verifyValue==0 ? "Kick" : "Ban for "+verifyValue)
					));
				
				if (groupSize > 0) {
					String fieldLabel = lu.getText(event, path+".embed_guilds");
					StringBuilder stringBuilder = new StringBuilder();
					String format = "%s | %s | `%s`";
					for (Long memberId : memberIds) {
						Guild guild = event.getJDA().getGuildById(memberId);
						if (guild == null) {
							guild = Optional.ofNullable(bot.getHelper()).map(helper -> helper.getJDA().getGuildById(memberId)).orElse(null);
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
				int groupSize = bot.getDBUtil().group.countMembers(groupId);

				EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
					.setAuthor(lu.getText(event, "logger.groups.title").formatted(groupName, groupId))
					.setDescription(lu.getText(event, path+".embed_short").formatted(
						masterName, ownerId, groupSize
					));
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

				createReplyEmbed(event, builder.build());
			}
		}
	}

}
