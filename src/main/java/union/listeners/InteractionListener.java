package union.listeners;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import union.App;
import union.base.command.CooldownScope;
import union.base.waiter.EventWaiter;
import union.objects.CaseType;
import union.objects.CmdAccessLevel;
import union.objects.Emotes;
import union.objects.annotation.NotNull;
import union.objects.annotation.Nullable;
import union.objects.constants.Constants;
import union.objects.constants.Links;
import union.utils.database.DBUtil;
import union.utils.database.managers.CaseManager.CaseData;
import union.utils.database.managers.TicketTagManager.Tag;
import union.utils.message.LocaleUtil;
import union.utils.message.MessageUtil;
import union.utils.message.SteamUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.exceptions.ContextException;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.managers.channel.concrete.VoiceChannelManager;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.dv8tion.jda.internal.utils.tuple.Pair;

public class InteractionListener extends ListenerAdapter {

	private final App bot;
	private final LocaleUtil lu;
	private final DBUtil db;
	private final EventWaiter waiter;

	private final List<Permission> AdminPerms = List.of(Permission.ADMINISTRATOR, Permission.MANAGE_SERVER, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_ROLES);

	public InteractionListener(App bot, EventWaiter waiter) {
		this.bot = bot;
		this.lu = bot.getLocaleUtil();
		this.db = bot.getDBUtil();
		this.waiter = waiter;
	}

	public void editError(IReplyCallback event, String... text) {
		if (text.length > 1) {
			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, text[0], text[1])).queue();
		} else {
			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, text[0])).queue();
		}
	}

	public void sendError(IReplyCallback event, String path) {
		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getError(event, path)).setEphemeral(true).queue();
	}

	public void sendError(IReplyCallback event, String path, String info) {
		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getError(event, path, info)).setEphemeral(true).queue();
	}

	public void sendSuccess(IReplyCallback event, String path) {
		event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path)).build()).setEphemeral(true).queue();
	}

	// Check for cooldown parametrs, if exists - check if cooldown active, else apply it
	private void runButtonInteraction(ButtonInteractionEvent event, @Nullable Cooldown cooldown, @NotNull Runnable function) {
		if (cooldown != null) {
			String key = getCooldownKey(cooldown, event);
			int remaining = bot.getClient().getRemainingCooldown(key);
			if (remaining > 0) {
				event.getHook().sendMessage(getCooldownErrorString(cooldown, event, remaining)).setEphemeral(true).queue();
				return;
			} else {
				bot.getClient().applyCooldown(key, cooldown.getTime());
			}
		}
		function.run();
	}

	private final List<String> acceptableButtons = List.of("verify", "role", "ticket", "tag", "invites", "delete", "voice", "blacklist", "strikes");

	private boolean isAcceptedId(final String id) {
		for (String match : acceptableButtons) {
			if (id.startsWith(match)) {
				return true;
			}
		}
		return false;
	}
	

	@Override
	public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
		String buttonId = event.getComponentId();

		if (!isAcceptedId(buttonId)) return;

		// Acknowledge interaction
		event.deferEdit().queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));

		try {
			if (buttonId.startsWith("verify")) {
				runButtonInteraction(event, Cooldown.BUTTON_VERIFY, () -> buttonVerify(event));
				return;
			}
			// Check verified
			if (event.isFromGuild() && !isVerified(event)) return;
	
			// Continue...
			if (buttonId.startsWith("role")) {
				String action = buttonId.split(":")[1];
				switch (action) {
					case "start_request":
						runButtonInteraction(event, Cooldown.BUTTON_ROLE_SHOW, () -> buttonRoleShowSelection(event));
						break;
					case "other":
						runButtonInteraction(event, Cooldown.BUTTON_ROLE_OTHER, () -> buttonRoleSelectionOther(event));
						break;
					case "clear":
						runButtonInteraction(event, Cooldown.BUTTON_ROLE_CLEAR, () -> buttonRoleSelectionClear(event));
						break;
					case "remove":
						runButtonInteraction(event, Cooldown.BUTTON_ROLE_REMOVE, () -> buttonRoleRemove(event));
						break;
					case "toggle":
						runButtonInteraction(event, Cooldown.BUTTON_ROLE_TOGGLE, () -> buttonRoleToggle(event));
						break;
					default:
						break;
				}
			} else if (buttonId.startsWith("ticket")) {
				String action = buttonId.split(":")[1];
				switch (action) {
					case "role_create":
						runButtonInteraction(event, Cooldown.BUTTON_ROLE_TICKET, () -> buttonRoleTicketCreate(event));
						break;
					case "role_approve":
						runButtonInteraction(event, Cooldown.BUTTON_ROLE_APPROVE, () -> buttonRoleTicketApprove(event));
						break;
					case "close":
						runButtonInteraction(event, Cooldown.BUTTON_TICKET_CLOSE, () -> buttonTicketClose(event));
						break;
					case "cancel":
						runButtonInteraction(event, Cooldown.BUTTON_TICKET_CANCEL, () -> buttonTicketCloseCancel(event));
						break;
					case "claim":
						runButtonInteraction(event, Cooldown.BUTTON_TICKET_CLAIM, () -> buttonTicketClaim(event));
						break;
					case "unclaim":
						runButtonInteraction(event, Cooldown.BUTTON_TICKET_UNCLAIM, () -> buttonTicketUnclaim(event));
						break;
					default:
						break;
				}
			} else if (buttonId.startsWith("tag")) {
				runButtonInteraction(event, Cooldown.BUTTON_TICKET_CREATE, () -> buttonTagCreateTicket(event));
			} else if (buttonId.startsWith("invites")) {
				runButtonInteraction(event, Cooldown.BUTTON_INVITES, () -> buttonShowInvites(event));
			} else if (buttonId.startsWith("delete")) {
				runButtonInteraction(event, Cooldown.BUTTON_REPORT_DELETE, () -> buttonReportDelete(event));
			} else if (buttonId.startsWith("voice")) {
				if (!event.getMember().getVoiceState().inAudioChannel()) {
					sendError(event, "bot.voice.listener.not_in_voice");
					return;
				}
				Long channelId = db.voice.getChannel(event.getUser().getIdLong());
				if (channelId == null) {
					sendError(event, "errors.no_channel");
					return;
				}
				VoiceChannel vc = event.getGuild().getVoiceChannelById(channelId);
				if (vc == null) return;
				String action = buttonId.split(":")[1];
				switch (action) {
					case "lock":
						runButtonInteraction(event, null, () -> buttonVoiceLock(event, vc));
						break;
					case "unlock":
						runButtonInteraction(event, null, () -> buttonVoiceUnlock(event, vc));
						break;
					case "ghost":
						runButtonInteraction(event, null, () -> buttonVoiceGhost(event, vc));
						break;
					case "unghost":
						runButtonInteraction(event, null, () -> buttonVoiceUnghost(event, vc));
						break;
					/* case "name":
						runButtonInteraction(event, null, () -> buttonVoiceName(event));
						break;
					case "limit":
						runButtonInteraction(event, null, () -> buttonVoiceLimit(event));
						break; */
					case "permit":
						runButtonInteraction(event, null, () -> buttonVoicePermit(event));
						break;
					case "reject":
						runButtonInteraction(event, null, () -> buttonVoiceReject(event));
						break;
					case "perms":
						runButtonInteraction(event, null, () -> buttonVoicePerms(event, vc));
						break;
					case "delete":
						runButtonInteraction(event, null, () -> buttonVoiceDelete(event, vc));
						break;
					default:
						break;
				}
			} else if (buttonId.startsWith("blacklist")) {
				runButtonInteraction(event, null, () -> buttonBlacklist(event));
			} else if (buttonId.startsWith("strikes")) {
				runButtonInteraction(event, Cooldown.BUTTON_SHOW_STRIKES, () -> buttonShowStrikes(event));
			}
		} catch(Throwable t) {
			// Logs throwable and trys to respond to the user with the error
			// Thrown errors are not user's error, but code's fault as such things should be catched earlier and replied properly
			bot.getLogger().error("ButtonInteraction Exception", t);
			event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_FAILURE)
				.setTitle(lu.getLocalized(event.getUserLocale(), "errors.title"))
				.setDescription(lu.getLocalized(event.getUserLocale(), "errors.unknown"))
				.addField(lu.getLocalized(event.getUserLocale(), "errors.additional"), MessageUtil.limitString(t.getMessage(), 1024), false)
				.build()
			).setEphemeral(true).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));
		}
	}

	// Check verified
	private Boolean isVerified(IReplyCallback event) {
		Guild guild = event.getGuild();
		if (!bot.getDBUtil().getVerifySettings(guild).isCheckEnabled()) return true;

		User user = event.getUser();
		if (bot.getDBUtil().verifyCache.isVerified(user.getIdLong())) return true;

		Role role = guild.getRoleById(db.getVerifySettings(guild).getRoleId());
		if (role == null) return true;
		
		// check if still has account connected
		Long steam64 = Optional.ofNullable(bot.getDBUtil().unionVerify.getSteam64(user.getId())).map(Long::valueOf).orElse(null);
		if (steam64 == null) {
			// remove verification role from user
			try {
				guild.removeRoleFromMember(user, role).reason("Autocheck: No account connected").queue(
					success -> {
						user.openPrivateChannel().queue(dm ->
							dm.sendMessage(bot.getLocaleUtil().getLocalized(guild.getLocale(), "bot.verification.role_removed").replace("{server}", guild.getName())).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER))
						);
						bot.getLogListener().verify.onUnverified(user, null, guild, "Autocheck: No account connected");
					}
				);
			} catch (Exception ex) {}
			return false;
		} else {
			// add user to local database
			bot.getDBUtil().verifyCache.addUser(user.getIdLong(), steam64);
			return true;
		}
	}

	// Verify
	private void buttonVerify(ButtonInteractionEvent event) {
		Member member = event.getMember();
		Guild guild = event.getGuild();

		Long roleId = db.getVerifySettings(guild).getRoleId();
		if (roleId == null) {
			sendError(event, "bot.verification.failed_role", "The verification role is not configured");
			return;
		}
		Role role = guild.getRoleById(roleId);
		if (role == null) {
			sendError(event, "bot.verification.failed_role", "Verification role not found");
			return;
		}
		if (member.getRoles().contains(role)) {
			sendError(event, "bot.verification.you_verified");
			return;
		}

		// Check if user is blacklisted
		List<Integer> groupIds = new ArrayList<Integer>();
		groupIds.addAll(db.group.getOwnedGroups(guild.getIdLong()));
		groupIds.addAll(db.group.getGuildGroups(guild.getIdLong()));
		for (int groupId : groupIds) {
			if (db.blacklist.inGroupUser(groupId, member.getIdLong()) && db.group.getAppealGuildId(groupId)!=guild.getIdLong()) {
				sendError(event, "bot.verification.blacklisted", "DiscordID: "+member.getId());
				bot.getLogListener().verify.onVerifiedAttempt(member.getUser(), null, guild,
					lu.getText(event, "logger_embed.verify.blacklisted").formatted(groupId));
				return;
			}
		}

		final Long steam64 = Optional.ofNullable(bot.getDBUtil().unionVerify.getSteam64(member.getId())).map(Long::valueOf).orElse(null);
		if (steam64 != null) {
			// Check if steam64 is not blacklisted
			for (int groupId : groupIds) {
				if (db.blacklist.inGroupSteam64(groupId, steam64) && db.group.getAppealGuildId(groupId)!=guild.getIdLong()) {
					sendError(event, "bot.verification.blacklisted", "SteamID: "+SteamUtil.convertSteam64toSteamID(steam64));
					bot.getLogListener().verify.onVerifiedAttempt(member.getUser(), steam64, guild,
						lu.getText(event, "logger_embed.verify.blacklisted").formatted(groupId));
					return;
				}
			}
			// Check if user has required playtime
			try {
				final int minumumPlaytime = db.getVerifySettings(guild).getMinimumPlaytime();
				if (minumumPlaytime > -1) {
					final Long playtime = db.unionPlayers.getPlayTime(guild.getIdLong(), SteamUtil.convertSteam64toSteamID(steam64));
					// if user has not joined at least once
					if (playtime == null) {
						sendError(event, "bot.verification.playtime_none", "[Your profile (link)](https://unionteams.ru/player/%s)".formatted(steam64));
						bot.getLogListener().verify.onVerifiedAttempt(member.getUser(), steam64, guild,
							lu.getText(event, "logger_embed.verify.playtime").formatted("none", minumumPlaytime));
						return;
					}
					// if user doesn't have minimum playtime required
					final long played = Math.floorDiv(playtime, 3600);
					if (played < minumumPlaytime) {
						sendError(event, "bot.verification.playtime_minimum", "Required minimum - %s hour/-s\n[Your profile (link)](https://unionteams.ru/player/%s)".formatted(minumumPlaytime, steam64));
						bot.getLogListener().verify.onVerifiedAttempt(member.getUser(), steam64, guild,
							lu.getText(event, "logger_embed.verify.playtime").formatted(played, minumumPlaytime));
						return;
					}
				}
			} catch (Exception ex) {
				bot.getLogger().warn("Exception at playtime check, skipped.", ex);
			}
			
			// Give verify role to user
			guild.addRoleToMember(member, role).reason("Verification completed - "+steam64).queue(
				success -> {
					bot.getLogListener().verify.onVerified(member.getUser(), steam64, guild);
					bot.getDBUtil().verifyCache.addUser(member.getIdLong(), steam64);
				},
				failure -> {
					sendError(event, "bot.verification.failed_role");
					bot.getLogger().warn("Was unable to add verify role to user in "+guild.getName()+"("+guild.getId()+")", failure);
				});
		} else {
			Button refresh = Button.of(ButtonStyle.DANGER, "verify-refresh", lu.getText(event, "bot.verification.listener.refresh"), Emoji.fromUnicode("🔁"));
			// Check if user pressed refresh button
			if (event.getButton().getId().endsWith("refresh")) {
				// Ask user to wait for 30 seconds each time
				event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_FAILURE).setTitle(lu.getText(event, "bot.verification.listener.wait_title"))
					.setDescription(lu.getText(event, "bot.verification.listener.wait_value")).build()).setEphemeral(true).queue();
				event.editButton(refresh.asDisabled()).queue(success -> event.editButton(refresh).queueAfter(30, TimeUnit.SECONDS, null, new ErrorHandler().ignore(ContextException.class)));
			} else {
				// Reply with instruction on how to verify, buttons - link and refresh
				Button verify = Button.link(Links.UNIONTEAMS, lu.getText(event, "bot.verification.listener.connect"));
				EmbedBuilder builder = new EmbedBuilder().setColor(bot.getDBUtil().getGuildSettings(guild).getColor())
					.setTitle(lu.getText(event, "bot.verification.embed.title"))
					.setDescription(lu.getText(event, "bot.verification.embed.description"))
					.addField(lu.getText(event, "bot.verification.embed.howto"), lu.getText(event, "bot.verification.embed.guide"), false);

				event.getHook().sendMessageEmbeds(builder.build()).setActionRow(verify, refresh).setEphemeral(true).queue();
			}
		}
	}

	// Role selection
	private void buttonRoleShowSelection(ButtonInteractionEvent event) {
		Guild guild = event.getGuild();
		String guildId = guild.getId();

		String channelId = db.ticket.getOpenedChannel(event.getMember().getId(), guildId, 0);
		if (channelId != null) {
			ThreadChannel channel = guild.getThreadChannelById(channelId);
			if (channel != null) {
				event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_FAILURE)
					.setDescription(lu.getText(event, "bot.ticketing.listener.ticket_exists").replace("{channel}", channel.getAsMention()))
					.build()
				).setEphemeral(true).queue();
				return;
			}
			db.ticket.closeTicket(Instant.now(), channelId, "BOT: Channel deleted (not found)");
		}

		List<ActionRow> actionRows = new ArrayList<ActionRow>();
		// String select menu IDs "menu:role_row:1/2/3"
		for (int row = 1; row <= 3; row++) {
			ActionRow actionRow = createRoleRow(guild, row);
			if (actionRow != null) {
				actionRows.add(actionRow);
			}
		}
		if (db.getTicketSettings(guild).otherRoleEnabled()) {
			actionRows.add(ActionRow.of(Button.secondary("role:other", lu.getText(event, "bot.ticketing.listener.request_other"))));
		}
		actionRows.add(ActionRow.of(Button.danger("role:clear", lu.getText(event, "bot.ticketing.listener.request_clear")),
			Button.success("ticket:role_create", lu.getText(event, "bot.ticketing.listener.request_continue"))));

		MessageEmbed embed = new EmbedBuilder()
			.setColor(Constants.COLOR_DEFAULT)
			.setDescription(lu.getText(event, "bot.ticketing.listener.request_title"))
			.build();

		event.getHook().sendMessageEmbeds(embed).setComponents(actionRows).setEphemeral(true).queue();
	}

	private ActionRow createRoleRow(final Guild guild, int row) {
		List<Map<String, Object>> assignRoles = bot.getDBUtil().role.getAssignableByRow(guild.getId(), row);
		if (assignRoles.isEmpty()) return null;
		List<SelectOption> options = new ArrayList<SelectOption>();
		for (Map<String, Object> data : assignRoles) {
			if (options.size() >= 25) break;
			String roleId = (String) data.getOrDefault("roleId", "0");
			Role role = guild.getRoleById(roleId);
			if (role == null) continue;
			String description = (String) data.getOrDefault("description", "-");
			options.add(SelectOption.of(role.getName(), roleId).withDescription(description));
		}
		StringSelectMenu menu = StringSelectMenu.create("menu:role_row:"+row)
			.setPlaceholder(db.getTicketSettings(guild).getRowText(row))
			.setMaxValues(25)
			.addOptions(options)
			.build();
		return ActionRow.of(menu);
	}

	private void buttonRoleSelectionOther(ButtonInteractionEvent event) {
		List<Field> fields = event.getMessage().getEmbeds().get(0).getFields();
		List<String> roleIds = bot.getMessageUtil().getIdsFromString(fields.isEmpty() ? "" : fields.get(0).getValue());
		if (roleIds.contains("0"))
			roleIds.remove("0");
		else
			roleIds.add("0");
		
		MessageEmbed embed = new EmbedBuilder(event.getMessage().getEmbeds().get(0))
			.clearFields()
			.addField(lu.getText(event, "bot.ticketing.listener.request_selected"), selectedRolesString(roleIds, event.getUserLocale()), false)
			.build();
		event.getHook().editOriginalEmbeds(embed).queue();
	}

	private void buttonRoleSelectionClear(ButtonInteractionEvent event) {
		MessageEmbed embed = new EmbedBuilder(event.getMessage().getEmbeds().get(0))
			.clearFields()
			.addField(lu.getText(event, "bot.ticketing.listener.request_selected"), selectedRolesString(Collections.emptyList(), event.getUserLocale()), false)
			.build();
		event.getHook().editOriginalEmbeds(embed).queue();
	}

	private void buttonRoleRemove(ButtonInteractionEvent event) {
		Guild guild = event.getGuild();
		List<Role> currentRoles = event.getMember().getRoles();

		List<Role> allRoles = new ArrayList<Role>();
		db.role.getAssignable(guild.getId()).forEach(data -> allRoles.add(guild.getRoleById(data.get("roleId").toString())));
		db.role.getCustom(guild.getId()).forEach(data -> allRoles.add(guild.getRoleById(data.get("roleId").toString())));
		List<Role> roles = allRoles.stream().filter(role -> currentRoles.contains(role)).collect(Collectors.toList());
		if (roles.isEmpty()) {
			event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.no_assigned")).setEphemeral(true).queue();
			return;
		}

		List<SelectOption> options = roles.stream().map(role -> SelectOption.of(role.getName(), role.getId())).toList();	
		StringSelectMenu menu = StringSelectMenu.create("menu:role_remove")
			.setPlaceholder(lu.getLocalized(event.getUserLocale(), "bot.ticketing.listener.request_template"))
			.setMaxValues(options.size())
			.addOptions(options)
			.build();
		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, "bot.ticketing.listener.remove_title")).build()).setActionRow(menu).setEphemeral(true).queue(msg ->
			waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> e.getComponentId().equals("menu:role_remove"),
				actionEvent -> {
					List<Role> remove = actionEvent.getSelectedOptions().stream().map(option -> guild.getRoleById(option.getValue())).toList();
					guild.modifyMemberRoles(event.getMember(), null, remove).reason("User request").queue(done -> {
						msg.editMessageEmbeds(bot.getEmbedUtil().getEmbed()
							.setDescription(lu.getText(event, "bot.ticketing.listener.remove_done").replace("{roles}", remove.stream().map(role -> role.getAsMention()).collect(Collectors.joining(", "))))
							.setColor(Constants.COLOR_SUCCESS)
							.build()
						).setComponents().queue();
					}, failure -> {
						msg.editMessageEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.remove_failed", failure.getMessage())).setComponents().queue();
					});
				},
				40,
				TimeUnit.SECONDS,
				() -> msg.editMessageComponents(ActionRow.of(
					menu.createCopy().setPlaceholder(lu.getText(event, "errors.timed_out")).setDisabled(true).build())
				).queue()
			)
		);
	}

	private void buttonRoleToggle(ButtonInteractionEvent event) {
		String roleId = event.getButton().getId().split(":")[2];
		Role role = event.getGuild().getRoleById(roleId);
		if (role == null || !db.role.isToggleable(roleId)) {
			sendError(event, "bot.ticketing.listener.toggle_failed", "Role not found or can't be toggled");
			return;
		}

		if (event.getMember().getRoles().contains(role)) {
			event.getGuild().removeRoleFromMember(event.getMember(), role).queue(done -> {
				event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed()
					.setDescription(lu.getText(event, "bot.ticketing.listener.toggle_removed").replace("{role}", role.getAsMention()))
					.setColor(Constants.COLOR_SUCCESS)
					.build()
				).setEphemeral(true).queue();
			}, failure -> {
				sendError(event, "bot.ticketing.listener.toggle_failed", failure.getMessage());
			});
		} else {
			event.getGuild().addRoleToMember(event.getMember(), role).queue(done -> {
				event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed()
					.setDescription(lu.getText(event, "bot.ticketing.listener.toggle_added").replace("{role}", role.getAsMention()))
					.setColor(Constants.COLOR_SUCCESS)
					.build()
				).setEphemeral(true).queue();
			}, failure -> {
				sendError(event, "bot.ticketing.listener.toggle_failed", failure.getMessage());
			});
		}
	}
	
	// Role ticket
	private void buttonRoleTicketCreate(ButtonInteractionEvent event) {
		Guild guild = event.getGuild();
		String guildId = guild.getId();

		// Check if user has selected any role
		List<Field> fields = event.getMessage().getEmbeds().get(0).getFields();
		List<String> roleIds = bot.getMessageUtil().getIdsFromString(fields.isEmpty() ? "" : fields.get(0).getValue());
		if (roleIds.isEmpty()) {
			sendError(event, "bot.ticketing.listener.request_none");
			return;
		}
		// Check if bot is able to give selected roles
		boolean otherRole = roleIds.contains("0");
		List<Role> memberRoles = event.getMember().getRoles();
		List<Role> add = roleIds.stream().filter(option -> !option.equals("0")).map(option -> guild.getRoleById(option))
			.filter(role -> role != null && !memberRoles.contains(role)).toList();
		if (!otherRole && add.isEmpty()) {
			sendError(event, "bot.ticketing.listener.request_empty");
			return;
		}

		Integer ticketId = 1 + db.ticket.lastIdByTag(guildId, 0);
		event.getChannel().asTextChannel().createThreadChannel(lu.getLocalized(event.getGuildLocale(), "ticket.role")+"-"+ticketId.toString(), true).setInvitable(false).queue(
			channel -> {
				db.ticket.addRoleTicket(ticketId, event.getMember().getId(), guildId, channel.getId(), String.join(";", roleIds));
				
				StringBuffer mentions = new StringBuffer(event.getMember().getAsMention());
				db.access.getRoles(guildId, CmdAccessLevel.MOD).forEach(roleId -> mentions.append(" <@&"+roleId+">"));
				channel.sendMessage(mentions.toString()).queue(msg -> msg.delete().queueAfter(5, TimeUnit.SECONDS, null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_CHANNEL)));
				
				Long steam64 = db.verifyCache.getSteam64(event.getMember().getIdLong());
				String rolesString = String.join(" ", add.stream().map(role -> role.getAsMention()).collect(Collectors.joining(" ")), (otherRole ? lu.getLocalized(event.getGuildLocale(), "bot.ticketing.embeds.other") : ""));
				String proofString = add.stream().map(role -> db.role.getDescription(role.getId())).filter(str -> str != null).distinct().collect(Collectors.joining("\n- ", "- ", ""));
				MessageEmbed embed = new EmbedBuilder().setColor(db.getGuildSettings(guild).getColor())
					.setDescription(String.format("SteamID\n> %s\n%s\n> %s\n\n%s, %s\n%s\n\n%s",
						(steam64 == null ? "None" : SteamUtil.convertSteam64toSteamID(steam64) + "\n> [UnionTeams](https://unionteams.ru/player/"+steam64+")"),
						lu.getLocalized(event.getGuildLocale(), "ticket.role_title"),
						rolesString,
						event.getMember().getEffectiveName(),
						lu.getLocalized(event.getGuildLocale(), "ticket.role_header"),
						(proofString.length() < 3 ? lu.getLocalized(event.getGuildLocale(), "ticket.role_proof") : proofString),
						lu.getLocalized(event.getGuildLocale(), "ticket.role_footer")
					))
					.build();
				Button approve = Button.success("ticket:role_approve", lu.getLocalized(event.getGuildLocale(), "ticket.role_approve"));
				Button close = Button.danger("ticket:close", lu.getLocalized(event.getGuildLocale(), "ticket.close")).withEmoji(Emoji.fromUnicode("🔒")).asDisabled();
				channel.sendMessageEmbeds(embed).setAllowedMentions(Collections.emptyList()).addActionRow(approve, close).queue(msg -> {
					msg.editMessageComponents(ActionRow.of(approve, close.asEnabled())).queueAfter(10, TimeUnit.SECONDS);
				});

				// Log
				bot.getLogListener().ticket.onCreate(guild, channel, event.getUser());
				// Send reply
				event.getHook().editOriginalEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, "bot.ticketing.listener.created").replace("{channel}", channel.getAsMention()))
					.build()
				).setComponents().queue();
			}, failure -> {
				event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.cant_create", failure.getMessage())).setComponents().queue();
			}
		);
	}

	private void buttonRoleTicketApprove(ButtonInteractionEvent event) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.MOD)) {
			// User has no Mod access (OR admin, server owner, dev) to approve role request
			sendError(event, "errors.interaction.no_access");
			return;
		}
		String channelId = event.getChannel().getId();
		if (!db.ticket.isOpened(channelId)) {
			sendError(event, "bot.ticketing.listener.is_closed");
			return;
		}
		Guild guild = event.getGuild();
		String userId = db.ticket.getUserId(channelId);

		guild.retrieveMemberById(userId).queue(member -> {
			List<Role> roles = db.ticket.getRoleIds(channelId).stream().map(id -> guild.getRoleById(id)).filter(role -> role != null).toList();
			String ticketId = db.ticket.getTicketId(channelId);
			if (roles.isEmpty()) {
				event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getText(event, "bot.ticketing.listener.role_none"))
					.setColor(Constants.COLOR_WARNING)
					.build()
				).setEphemeral(true).queue();
			} else {
				guild.modifyMemberRoles(member, roles, null).reason("Request role-"+ticketId+" approved by "+event.getMember().getEffectiveName()).queue(done -> {
					bot.getLogListener().role.onApproved(member, event.getMember(), guild, roles, ticketId);
					db.ticket.setClaimed(channelId, event.getMember().getId());
					event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.role_added"))
						.setColor(Constants.COLOR_SUCCESS)
						.build()
					).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_WEBHOOK));
					member.getUser().openPrivateChannel().queue(dm -> {
						Button showInvites = Button.secondary("invites:"+guild.getId(), lu.getLocalized(guild.getLocale(), "bot.ticketing.listener.invites.button"));
						dm.sendMessage(lu.getLocalized(guild.getLocale(), "bot.ticketing.listener.role_dm")
							.replace("{roles}", roles.stream().map(role -> role.getName()).collect(Collectors.joining(" | ")))
							.replace("{server}", guild.getName())
							.replace("{id}", ticketId)
							.replace("{mod}", event.getMember().getEffectiveName())
						).addActionRow(showInvites).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
					});
				}, failure -> {
					sendError(event, "bot.ticketing.listener.role_failed", failure.getMessage());
				});
			}
		}, failure -> {
			sendError(event, "bot.ticketing.listener.no_member", failure.getMessage());
		});
	}

	private void buttonTicketClose(ButtonInteractionEvent event) {
		String channelId = event.getChannel().getId();
		if (!db.ticket.isOpened(channelId)) {
			// Ticket is closed
			event.getChannel().delete().queue();
			return;
		}
		event.editButton(Button.danger("ticket:close", bot.getLocaleUtil().getLocalized(event.getGuildLocale(), "ticket.close")).withEmoji(Emoji.fromUnicode("🔒")).asDisabled()).queue();
		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event).setDescription(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.delete_countdown")).build()).queue(msg -> {
			bot.getTicketUtil().closeTicket(channelId, event.getUser(), (db.ticket.getUserId(channelId).equals(event.getUser().getId()) ? "Closed by ticket's author" : "Closed by Support"), failure -> {
				msg.editMessageEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.close_failed", failure.getMessage())).queue();
				bot.getLogger().error("Couldn't close ticket with channelID:"+channelId, failure);
			});
		});
	}

	private void buttonTicketCloseCancel(ButtonInteractionEvent event) {
		String channelId = event.getChannel().getId();
		Guild guild = event.getGuild();
		if (!db.ticket.isOpened(channelId)) {
			// Ticket is closed
			event.getChannel().delete().queue();
			return;
		}
		db.ticket.setRequestStatus(channelId, -1L);
		MessageEmbed embed = new EmbedBuilder()
			.setColor(db.getGuildSettings(guild).getColor())
			.setDescription(bot.getLocaleUtil().getLocalized(guild.getLocale(), "ticket.autoclose_cancel"))
			.build();
		event.getHook().editOriginalEmbeds(embed).setComponents().queue();
	}
	
	// Ticket management
	private void buttonTicketClaim(ButtonInteractionEvent event) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.HELPER)) {
			// User has no Helper's access or higher to approve role request
			sendError(event, "errors.interaction.no_access");
			return;
		}
		String channelId = event.getChannel().getId();
		if (!db.ticket.isOpened(channelId)) {
			sendError(event, "bot.ticketing.listener.is_closed");
			return;
		}

		db.ticket.setClaimed(channelId, event.getUser().getId());
		event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
			.setDescription(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.claimed").replace("{user}", event.getUser().getAsMention()))
			.build()
		).queue();

		Button close = Button.danger("ticket:close", lu.getLocalized(event.getGuildLocale(), "ticket.close")).withEmoji(Emoji.fromUnicode("🔒"));
		Button claimed = Button.primary("ticket:claimed", lu.getLocalized(event.getGuildLocale(), "ticket.claimed").formatted(event.getUser().getName())).asDisabled();
		Button unclaim = Button.primary("ticket:unclaim", lu.getLocalized(event.getGuildLocale(), "ticket.unclaim"));
		event.getMessage().editMessageComponents(ActionRow.of(close, claimed, unclaim)).queue();
	}

	private void buttonTicketUnclaim(ButtonInteractionEvent event) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.HELPER)) {
			// User has no Helper's access or higher to approve role request
			sendError(event, "errors.interaction.no_access");
			return;
		}
		String channelId = event.getChannel().getId();
		if (!db.ticket.isOpened(channelId)) {
			sendError(event, "bot.ticketing.listener.is_closed");
			return;
		}

		db.ticket.setUnclaimed(channelId);
		event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
			.setDescription(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.unclaimed"))
			.build()
		).queue();

		Button close = Button.danger("ticket:close", lu.getLocalized(event.getGuildLocale(), "ticket.close")).withEmoji(Emoji.fromUnicode("🔒"));
		Button claim = Button.primary("ticket:claim", lu.getLocalized(event.getGuildLocale(), "ticket.claim"));
		event.getMessage().editMessageComponents(ActionRow.of(close, claim)).queue();
	}

	// Tag, create ticket
	private void buttonTagCreateTicket(ButtonInteractionEvent event) {
		String guildId = event.getGuild().getId();
		Integer tagId = Integer.valueOf(event.getComponentId().split(":")[1]);

		String channelId = db.ticket.getOpenedChannel(event.getMember().getId(), guildId, tagId);
		if (channelId != null) {
			GuildChannel channel = event.getGuild().getGuildChannelById(channelId);
			if (channel != null) {
				event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_FAILURE)
					.setDescription(lu.getText(event, "bot.ticketing.listener.ticket_exists").replace("{channel}", channel.getAsMention()))
					.build()
				).setEphemeral(true).queue();
				return;
			}
			db.ticket.closeTicket(Instant.now(), channelId, "BOT: Channel deleted (not found)");
		}

		Tag tag = db.tags.getTagInfo(tagId);
		if (tag == null) {
			sendTicketError(event, "Unknown tag with ID: "+tagId);
			return;
		}

		User user = event.getUser();

		StringBuffer mentions = new StringBuffer(user.getAsMention());
		List<String> supportRoles = Optional.ofNullable(tag.getSupportRoles()).map(text -> Arrays.asList(text.split(";"))).orElse(Collections.emptyList());
		supportRoles.forEach(roleId -> mentions.append(" <@&%s>".formatted(roleId)));

		String message = Optional.ofNullable(tag.getMessage())
			.map(text -> text.replace("{username}", user.getName()).replace("{tag_username}", user.getAsMention()))
			.orElse("Ticket's controls");
		
		Integer ticketId = 1 + db.ticket.lastIdByTag(guildId, tagId);
		String ticketName = (tag.getTicketName()+ticketId).replace("{username}", user.getName());
		if (tag.getTagType() == 1) {
			// Thread ticket
			event.getChannel().asTextChannel().createThreadChannel(ticketName, true).setInvitable(false).queue(channel -> {
				db.ticket.addTicket(ticketId, user.getId(), guildId, channel.getId(), tagId);
				
				bot.getTicketUtil().createTicket(event, channel, mentions.toString(), message);
			},
			failure -> {
				sendTicketError(event, "Unable to create new thread in this channel");
			});
		} else {
			// Channel ticket
			Category category = Optional.ofNullable(tag.getLocation()).map(id -> event.getGuild().getCategoryById(id)).orElse(event.getChannel().asTextChannel().getParentCategory());
			if (category == null) {
				sendTicketError(event, "Target category not found, with ID: "+tag.getLocation());
				return;
			}

			ChannelAction<TextChannel> action = category.createTextChannel(ticketName).clearPermissionOverrides();
			for (String roleId : supportRoles) action = action.addRolePermissionOverride(Long.valueOf(roleId), EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), null);
			action.addPermissionOverride(event.getGuild().getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
				.addMemberPermissionOverride(user.getIdLong(), EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), null)
				.queue(channel -> {
				db.ticket.addTicket(ticketId, user.getId(), guildId, channel.getId(), tagId);

				bot.getTicketUtil().createTicket(event, channel, mentions.toString(), message);
			}, 
			failure -> {
				sendTicketError(event, "Unable to create new channel in target category, with ID: "+tag.getLocation());
			});
		}
	}

	private void sendTicketError(ButtonInteractionEvent event, String reason) {
		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.cant_create", reason)).setEphemeral(true).queue();
	}

	// Report
	private void buttonReportDelete(ButtonInteractionEvent event) {
		event.getHook().editOriginalComponents().queue();

		String channelId = event.getComponentId().split(":")[1];
		String messageId = event.getComponentId().split(":")[2];
		
		TextChannel channel = event.getGuild().getTextChannelById(channelId);
		if (channel == null) {
			event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getError(event, "misc.unknown", "Unknown channel")).queue();
			return;
		}
		channel.deleteMessageById(messageId).reason("Deleted by %s".formatted(event.getMember().getEffectiveName())).queue(success ->
			event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getLocalized(event.getGuildLocale(), "menus.report.deleted").replace("{name}", event.getMember().getAsMention()))
				.build()
			).queue(),
		failure -> event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getError(event, "misc.unknown", failure.getMessage())).queue()
		);
	}

	// Voice
	private void buttonVoiceLock(ButtonInteractionEvent event, VoiceChannel vc) {
		// Verify role
		Long verifyRoleId = bot.getDBUtil().getVerifySettings(event.getGuild()).getRoleId();

		try {
			if (verifyRoleId != null) {
				Role verifyRole = event.getGuild().getRoleById(verifyRoleId);
				if (verifyRole != null) {
					vc.upsertPermissionOverride(verifyRole).deny(Permission.VOICE_CONNECT).queue();
				}
			} else {
				vc.upsertPermissionOverride(event.getGuild().getPublicRole()).deny(Permission.VOICE_CONNECT).queue();
			}
		} catch (InsufficientPermissionException ex) {
			event.getHook().sendMessage(bot.getEmbedUtil().createPermError(event, ex.getPermission(), true)).setEphemeral(true).queue();
			return;
		}
		sendSuccess(event, "bot.voice.listener.panel.lock");
	}

	private void buttonVoiceUnlock(ButtonInteractionEvent event, VoiceChannel vc) {
		// Verify role
		Long verifyRoleId = bot.getDBUtil().getVerifySettings(event.getGuild()).getRoleId();

		try {
			if (verifyRoleId != null) {
				Role verifyRole = event.getGuild().getRoleById(verifyRoleId);
				if (verifyRole != null) {
					vc.upsertPermissionOverride(verifyRole).setAllowed(Permission.VOICE_CONNECT).queue();
				}
			} else {
				vc.upsertPermissionOverride(event.getGuild().getPublicRole()).clear(Permission.VOICE_CONNECT).queue();
			}
		} catch (InsufficientPermissionException ex) {
			event.getHook().sendMessage(bot.getEmbedUtil().createPermError(event, ex.getPermission(), true)).setEphemeral(true).queue();
			return;
		}
		sendSuccess(event, "bot.voice.listener.panel.unlock");
	}

	private void buttonVoiceGhost(ButtonInteractionEvent event, VoiceChannel vc) {
		// Verify role
		Long verifyRoleId = bot.getDBUtil().getVerifySettings(event.getGuild()).getRoleId();

		try {
			if (verifyRoleId != null) {
				Role verifyRole = event.getGuild().getRoleById(verifyRoleId);
				if (verifyRole != null) {
					vc.upsertPermissionOverride(verifyRole).deny(Permission.VIEW_CHANNEL).queue();
				}
			} else {
				vc.upsertPermissionOverride(event.getGuild().getPublicRole()).deny(Permission.VIEW_CHANNEL).queue();
			}
		} catch (InsufficientPermissionException ex) {
			event.getHook().sendMessage(bot.getEmbedUtil().createPermError(event, ex.getPermission(), true)).setEphemeral(true).queue();
			return;
		}
		sendSuccess(event, "bot.voice.listener.panel.ghost");
	}

	private void buttonVoiceUnghost(ButtonInteractionEvent event, VoiceChannel vc) {
		// Verify role
		Long verifyRoleId = bot.getDBUtil().getVerifySettings(event.getGuild()).getRoleId();

		try {
			if (verifyRoleId != null) {
				Role verifyRole = event.getGuild().getRoleById(verifyRoleId);
				if (verifyRole != null) {
					vc.upsertPermissionOverride(verifyRole).setAllowed(Permission.VIEW_CHANNEL).queue();
				}
			} else {
				vc.upsertPermissionOverride(event.getGuild().getPublicRole()).clear(Permission.VIEW_CHANNEL).queue();
			}
		} catch (InsufficientPermissionException ex) {
			event.getHook().sendMessage(bot.getEmbedUtil().createPermError(event, ex.getPermission(), true)).setEphemeral(true).queue();
			return;
		}
		sendSuccess(event, "bot.voice.listener.panel.unghost");
	}

	/* private void buttonVoiceName(ButtonInteractionEvent event) {
		TextInput textInput = TextInput.create("name", lu.getText(event, "bot.voice.listener.panel.name_label"), TextInputStyle.SHORT)
			.setPlaceholder("{user}'s channel")
			.setMaxLength(100)
			.build();
		event.getHook().replyModal(Modal.create("voice:name", lu.getText(event, "bot.voice.listener.panel.modal")).addActionRow(textInput).build()).queue();
	}

	private void buttonVoiceLimit(ButtonInteractionEvent event) {
		TextInput textInput = TextInput.create("limit", lu.getText(event, "bot.voice.listener.panel.limit_label"), TextInputStyle.SHORT)
			.setPlaceholder("0 / 99")
			.setRequiredRange(1, 2)
			.build();
		event.replyModal(Modal.create("voice:limit", lu.getText(event, "bot.voice.listener.panel.modal")).addActionRow(textInput).build()).queue();
	} */

	private void buttonVoicePermit(ButtonInteractionEvent event) {
		String text = lu.getText(event, "bot.voice.listener.panel.permit_label");
		event.getHook().sendMessage(text).addActionRow(EntitySelectMenu.create("voice:permit", SelectTarget.USER, SelectTarget.ROLE).setMaxValues(10).build()).setEphemeral(true).queue();
	}

	private void buttonVoiceReject(ButtonInteractionEvent event) {
		String text = lu.getText(event, "bot.voice.listener.panel.reject_label");
		event.getHook().sendMessage(text).addActionRow(EntitySelectMenu.create("voice:reject", SelectTarget.USER, SelectTarget.ROLE).setMaxValues(10).build()).setEphemeral(true).queue();
	}

	private void buttonVoicePerms(ButtonInteractionEvent event, VoiceChannel vc) {
		Guild guild = event.getGuild();
		EmbedBuilder embedBuilder = bot.getEmbedUtil().getEmbed()
			.setTitle(lu.getText(event, "bot.voice.listener.panel.perms.title").replace("{channel}", vc.getName()))
			.setDescription(lu.getText(event, "bot.voice.listener.panel.perms.field")+"\n\n");
		
		//@Everyone
		PermissionOverride publicOverride = vc.getPermissionOverride(guild.getPublicRole());

		String view = contains(publicOverride, Permission.VIEW_CHANNEL);
		String join = contains(publicOverride, Permission.VOICE_CONNECT);
		
		embedBuilder = embedBuilder.appendDescription("> %s | %s | `%s`\n\n%s\n".formatted(view, join, lu.getText(event, "bot.voice.listener.panel.perms.everyone"),
			lu.getText(event, "bot.voice.listener.panel.perms.roles")));

		//Roles
		List<PermissionOverride> overrides = new ArrayList<>(vc.getRolePermissionOverrides()); // cause given override list is immutable
		try {
			overrides.remove(vc.getPermissionOverride(guild.getBotRole())); // removes bot's role
			overrides.remove(vc.getPermissionOverride(guild.getPublicRole())); // removes @everyone role
		} catch (NullPointerException ex) {
			bot.getLogger().warn("PermsCmd null pointer at role override remove");
		}
		
		if (overrides.isEmpty()) {
			embedBuilder.appendDescription(lu.getText(event, "bot.voice.listener.panel.perms.none") + "\n");
		} else {
			for (PermissionOverride ov : overrides) {
				view = contains(ov, Permission.VIEW_CHANNEL);
				join = contains(ov, Permission.VOICE_CONNECT);

				embedBuilder.appendDescription("> %s | %s | `%s`\n".formatted(view, join, ov.getRole().getName()));
			}
		}
		embedBuilder.appendDescription("\n%s\n".formatted(lu.getText(event, "bot.voice.listener.panel.perms.members")));

		//Members
		overrides = new ArrayList<>(vc.getMemberPermissionOverrides());
		try {
			overrides.remove(vc.getPermissionOverride(event.getMember())); // removes user
			overrides.remove(vc.getPermissionOverride(guild.getSelfMember())); // removes bot
		} catch (NullPointerException ex) {
			bot.getLogger().warn("PermsCmd null pointer at member override remove");
		}

		EmbedBuilder embedBuilder2 = embedBuilder;
		List<PermissionOverride> ovs = overrides;

		guild.retrieveMembersByIds(false, overrides.stream().map(ov -> ov.getId()).toArray(String[]::new)).onSuccess(
			members -> {
				if (members.isEmpty()) {
					embedBuilder2.appendDescription(lu.getText(event, "bot.voice.listener.panel.perms.none") + "\n");
				} else {
					for (PermissionOverride ov : ovs) {
						String view2 = contains(ov, Permission.VIEW_CHANNEL);
						String join2 = contains(ov, Permission.VOICE_CONNECT);

						Member find = members.stream().filter(m -> m.getId().equals(ov.getId())).findFirst().get(); 
						embedBuilder2.appendDescription("> %s | %s | `%s`\n".formatted(view2, join2, find.getEffectiveName()));
					}
				}

				event.getHook().sendMessageEmbeds(embedBuilder2.build()).setEphemeral(true).queue();
			}
		);
	}

	private void buttonVoiceDelete(ButtonInteractionEvent event, VoiceChannel vc) {
		bot.getDBUtil().voice.remove(vc.getIdLong());
		vc.delete().reason("Channel owner request").queue();
		sendSuccess(event, "bot.voice.listener.panel.delete");
	}

	// Show role invites
	private void buttonShowInvites(ButtonInteractionEvent event) {
		if (event.isFromGuild()) {
			Guild guild = event.getGuild();
			Map<String, String> roles = bot.getDBUtil().role.getRolesWithInvites(guild.getId());
			Map<String, String> invites = event.getMember().getRoles().stream()
				.filter(role -> roles.containsKey(role.getId()))
				.collect(Collectors.toMap(Role::getName, role -> roles.get(role.getId())));
			sendInvites(event, guild, invites);
		} else {
			Guild guild = event.getJDA().getGuildById(event.getComponentId().split(":")[1]);
			if (guild == null) {
				sendError(event, "bot.ticketing.listener.invites.no_guild");
				return;
			}
			guild.retrieveMember(event.getUser()).queue(member -> {
				Map<String, String> roles = bot.getDBUtil().role.getRolesWithInvites(guild.getId());
				Map<String, String> invites = member.getRoles().stream()
					.filter(role -> roles.containsKey(role.getId()))
					.collect(Collectors.toMap(Role::getName, role -> roles.get(role.getId())));
				sendInvites(event, guild, invites);
			}, failure -> {
				sendError(event, "bot.ticketing.listener.invites.no_guild", "Server ID: "+guild.getId());
			});
		}
	}

	private void sendInvites(ButtonInteractionEvent event, Guild guild, Map<String, String> invites) {
		if (invites.isEmpty()) {
			sendError(event, "bot.ticketing.listener.invites.none");
			return;
		}
		EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setAuthor(lu.getLocalized(event.getUserLocale(), "bot.ticketing.listener.invites.title").formatted(guild.getName()), null, guild.getIconUrl());
		invites.forEach((k, v) -> builder.appendDescription("%s\n> %s\n".formatted(k, v)));
		event.getHook().sendMessageEmbeds(builder.build()).setEphemeral(true).queue();
	}

	// Blacklist
	private void buttonBlacklist(ButtonInteractionEvent event) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.OPERATOR)) {
			sendError(event, "errors.interaction.no_access");
			return;
		}

		String userId = event.getComponentId().split(":")[1];
		CaseData caseData = db.cases.getMemberActive(Long.parseLong(userId), event.getGuild().getIdLong(), CaseType.BAN);
		if (caseData == null || !caseData.getDuration().isZero()) {
			sendError(event, "bot.moderation.blacklist.expired");
			return;
		}

		long guildId = event.getGuild().getIdLong();
		List<Integer> groupIds = new ArrayList<Integer>();
		groupIds.addAll(bot.getDBUtil().group.getOwnedGroups(guildId));
		groupIds.addAll(bot.getDBUtil().group.getManagedGroups(guildId));
		if (groupIds.isEmpty()) {
			sendError(event, "bot.moderation.blacklist.no_groups");
			return;
		}

		if (bot.getHelper() == null) {
			sendError(event, "errors.no_helper");
			return;
		}

		MessageEmbed embed = bot.getEmbedUtil().getEmbed()
			.setColor(Constants.COLOR_WARNING)
			.setDescription(lu.getText(event, "bot.moderation.blacklist.title"))
			.build();
		StringSelectMenu menu = StringSelectMenu.create("groupId")
			.setPlaceholder(lu.getText(event, "bot.moderation.blacklist.value"))
			.addOptions(groupIds.stream().map(groupId ->
				SelectOption.of(bot.getDBUtil().group.getName(groupId), groupId.toString()).withDescription("ID: "+groupId)
			).collect(Collectors.toList()))
			.setMaxValues(1)
			.build();

		event.getHook().sendMessageEmbeds(embed).setActionRow(menu).setEphemeral(true).queue(msg -> {
			waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> e.getMessageId().equals(msg.getId()),
				selectEvent -> {
					selectEvent.deferEdit().queue();
					List<String> selected = selectEvent.getValues();

					event.getJDA().retrieveUserById(userId).queue(user -> {
						Long steam64 = db.verifyCache.getSteam64(user.getIdLong());
						selected.forEach(id -> {
							Integer groupId = Integer.parseInt(id);
							if (!db.blacklist.inGroupUser(groupId, caseData.getTargetId()))
								db.blacklist.add(selectEvent.getGuild().getIdLong(), groupId, user.getIdLong(), steam64, caseData.getReason(), selectEvent.getUser().getIdLong());
	
							bot.getHelper().runBan(groupId, event.getGuild(), user, caseData.getReason());
						});

						// Log to master
						bot.getLogListener().mod.onBlacklistAdded(event.getUser(), user, steam64, groupIds);
						// Reply
						selectEvent.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed()
							.setColor(Constants.COLOR_SUCCESS)
							.setDescription(lu.getText(event, "bot.moderation.blacklist.done"))
							.build())
						.setComponents().queue();
					},
					failure -> {
						selectEvent.getHook().editOriginalEmbeds(
							bot.getEmbedUtil().getError(selectEvent, "bot.moderation.blacklist.no_user", failure.getMessage())
						).setComponents().queue();
					});
				},
				20,
				TimeUnit.SECONDS,
				() -> msg.editMessageComponents(ActionRow.of(menu.asDisabled())).queue()
			);
		});
	}

	// Strikes
	private void buttonShowStrikes(ButtonInteractionEvent event) {
		Long guildId = Long.valueOf(event.getComponentId().split(":")[1]);
		Pair<Integer, Integer> strikeData = bot.getDBUtil().strike.getDataCountAndDate(guildId, event.getUser().getIdLong());
		if (strikeData == null) {
			event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, "bot.moderation.no_strikes")).build()).queue();
			return;
		}
		
		Instant time = Instant.ofEpochSecond(strikeData.getRight());
		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed()
			.setDescription(lu.getText(event, "bot.moderation.strikes_embed").formatted(strikeData.getLeft(), TimeFormat.RELATIVE.atInstant(time)))
			.build()
		).queue();
	}

	@Override
	public void onModalInteraction(@NotNull ModalInteractionEvent event) {
		event.deferReply(true).queue();
		String modalId = event.getModalId();

		if (modalId.equals("vfpanel")) {
			if (event.getValues().size() == 0) {
				sendError(event, "errors.interaction.no_values");
				return;
			}

			String main = event.getValue("main").getAsString();
			db.verifySettings.setMainText(event.getGuild().getIdLong(), main.isBlank() ? "NULL" : main);

			event.getHook().editOriginalEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, "bot.verification.vfpanel.text.done"))
				.build()
			).queue();
		}/*  else if (modalId.startsWith("voice")) {
			if (!event.getMember().getVoiceState().inAudioChannel()) {
				replyError(event, "bot.voice.listener.not_in_voice");
				return;
			}
			String channelId = db.voice.getChannel(event.getUser().getId());
			if (channelId == null) {
				replyError(event, "errors.no_channel");
				return;
			}
			VoiceChannel vc = event.getGuild().getVoiceChannelById(channelId);
			if (vc == null) return;
			String userId = event.getUser().getId();
			String action = modalId.split(":")[1];
			if (action.equals("name")) {
				String name = event.getValue("name").getAsString();
				name = name.replace("{user}", event.getMember().getEffectiveName());
				vc.getManager().setName(name.substring(0, Math.min(100, name.length()))).queue();

				if (!bot.getDBUtil().user.exists(userId)) bot.getDBUtil().user.add(userId);
				bot.getDBUtil().user.setName(userId, name);

				event.replyEmbeds( 
					bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getText(event, "bot.voice.listener.panel.name_done").replace("{name}", name))
						.build()
				).setEphemeral(true).queue();
			} else if (action.equals("limit")) {
				Integer limit;
				try {
					limit = Integer.parseInt(event.getValue("limit").getAsString());
				} catch (NumberFormatException ex) {
					event.deferEdit().queue();
					return;
				}
				if (limit < 0 || limit > 99) {
					event.deferEdit().queue();
					return;
				}
				vc.getManager().setUserLimit(limit).queue();
				
				if (!bot.getDBUtil().user.exists(userId)) bot.getDBUtil().user.add(userId);
				bot.getDBUtil().user.setLimit(userId, limit);

				event.replyEmbeds( 
					bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getText(event, "bot.voice.listener.panel.limit_done").replace("{limit}", limit.toString()))
						.build()
				).setEphemeral(true).queue();
			}
		} */
	}

	@Override
	public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
		String menuId = event.getComponentId();

		if (menuId.startsWith("menu:role_row")) {
			event.deferEdit().queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));

			List<Field> fields = event.getMessage().getEmbeds().get(0).getFields();
			List<String> roleIds = bot.getMessageUtil().getIdsFromString(fields.isEmpty() ? "" : fields.get(0).getValue());
			event.getSelectedOptions().forEach(option -> {
				String value = option.getValue();
				if (!roleIds.contains(value)) roleIds.add(value);
			});

			MessageEmbed embed = new EmbedBuilder(event.getMessage().getEmbeds().get(0))
				.clearFields()
				.addField(lu.getText(event, "bot.ticketing.listener.request_selected"), selectedRolesString(roleIds, event.getUserLocale()), false)
				.build();
			event.getHook().editOriginalEmbeds(embed).queue();
		}
	}

	@Override
	public void onEntitySelectInteraction(@NotNull EntitySelectInteractionEvent event) {
		String menuId = event.getComponentId();
		if (menuId.startsWith("voice")) {
			event.deferEdit().queue();

			Member author = event.getMember();
			if (!author.getVoiceState().inAudioChannel()) {
				sendError(event, "bot.voice.listener.not_in_voice");
				return;
			}
			Long channelId = db.voice.getChannel(author.getIdLong());
			if (channelId == null) {
				sendError(event, "errors.no_channel");
				return;
			}
			Guild guild = event.getGuild();
			VoiceChannel vc = guild.getVoiceChannelById(channelId);
			if (vc == null) return;
			String action = menuId.split(":")[1];
			if (action.equals("permit") || action.equals("reject")) {
				Mentions mentions = event.getMentions();
				
				List<Member> members = mentions.getMembers();
				List<Role> roles = mentions.getRoles();
				if (members.isEmpty() && roles.isEmpty()) {
					return;
				}
				if (members.contains(author) || members.contains(guild.getSelfMember())) {
					event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.voice.listener.panel.not_self"))
						.setContent("").setComponents().queue();
					return;
				}

				List<String> mentionStrings = new ArrayList<>();
				String text = "";

				VoiceChannelManager manager = vc.getManager();
				
				if (action.equals("permit")) {
					for (Member member : members) {
						manager = manager.putPermissionOverride(member, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null);
						mentionStrings.add(member.getEffectiveName());
					}
		
					for (Role role : roles) {
						if (!role.hasPermission(AdminPerms)) {
							manager = manager.putPermissionOverride(role, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null);
							mentionStrings.add(role.getName());
						}
					}
	
					text = lu.getUserText(event, "bot.voice.listener.panel.permit_done", mentionStrings);
				} else {
					for (Member member : members) {
						manager = manager.putPermissionOverride(member, null, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL));
						if (vc.getMembers().contains(member)) {
							guild.kickVoiceMember(member).queue();
						}
						mentionStrings.add(member.getEffectiveName());
					}
		
					for (Role role : roles) {
						if (!role.hasPermission(AdminPerms)) {
							manager = manager.putPermissionOverride(role, null, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL));
							mentionStrings.add(role.getName());
						}
					}

					text = lu.getUserText(event, "bot.voice.listener.panel.reject_done", mentionStrings);
				}

				final MessageEmbed embed = bot.getEmbedUtil().getEmbed(event).setDescription(text).build();
				manager.queue(done -> {
					event.getHook().editOriginalEmbeds(embed)
						.setContent("").setComponents().queue();
				}, failure -> {
					event.getHook().editOriginal(MessageEditData.fromCreateData(bot.getEmbedUtil().createPermError(event, Permission.MANAGE_PERMISSIONS, true)))
						.setContent("").setComponents().queue();
				});
			}
		}
	}


	// Tools
	private String selectedRolesString(List<String> roleIds, DiscordLocale locale) {
		if (roleIds.isEmpty()) return "None";
		return roleIds.stream().map(id -> (id.equals("0") ? "+"+lu.getLocalized(locale, "bot.ticketing.embeds.other") : "<@&"+id+">")).collect(Collectors.joining(", "));
	}
	
	private String contains(PermissionOverride override, Permission perm) {
		if (override != null) {
			if (override.getAllowed().contains(perm))
				return Emotes.CHECK_C.getEmote();
			else if (override.getDenied().contains(perm))
				return Emotes.CROSS_C.getEmote();
		}
		return Emotes.NONE.getEmote();
	}


	private enum Cooldown {
		BUTTON_VERIFY(10, CooldownScope.USER),
		BUTTON_ROLE_SHOW(20, CooldownScope.USER),
		BUTTON_ROLE_OTHER(2, CooldownScope.USER),
		BUTTON_ROLE_CLEAR(4, CooldownScope.USER),
		BUTTON_ROLE_REMOVE(10, CooldownScope.USER),
		BUTTON_ROLE_TOGGLE(2, CooldownScope.USER),
		BUTTON_ROLE_TICKET(30, CooldownScope.USER),
		BUTTON_ROLE_APPROVE(10, CooldownScope.CHANNEL),
		BUTTON_TICKET_CLOSE(10, CooldownScope.CHANNEL),
		BUTTON_TICKET_CANCEL(4, CooldownScope.CHANNEL),
		BUTTON_TICKET_CLAIM(20, CooldownScope.USER_CHANNEL),
		BUTTON_TICKET_UNCLAIM(20, CooldownScope.USER_CHANNEL),
		BUTTON_TICKET_CREATE(30, CooldownScope.USER),
		BUTTON_INVITES(10, CooldownScope.USER),
		BUTTON_REPORT_DELETE(3, CooldownScope.GUILD),
		BUTTON_SHOW_STRIKES(30, CooldownScope.USER);

		private final int time;
		private final CooldownScope scope;

		Cooldown(@NotNull int time, @NotNull CooldownScope scope) {
			this.time = time;
			this.scope = scope;
		}

		public int getTime() {
			return this.time;
		}

		public CooldownScope getScope() {
			return this.scope;
		}
	}

	private String getCooldownKey(Cooldown cooldown, GenericInteractionCreateEvent event) {
		String name = cooldown.toString();
		CooldownScope cooldownScope = cooldown.getScope();
		switch (cooldown.getScope()) {
			case USER:         return cooldownScope.genKey(name,event.getUser().getIdLong());
			case USER_GUILD:   return Optional.of(event.getGuild()).map(g -> cooldownScope.genKey(name,event.getUser().getIdLong(),g.getIdLong()))
				.orElse(CooldownScope.USER_CHANNEL.genKey(name,event.getUser().getIdLong(), event.getChannel().getIdLong()));
			case USER_CHANNEL: return cooldownScope.genKey(name,event.getUser().getIdLong(),event.getChannel().getIdLong());
			case GUILD:        return Optional.of(event.getGuild()).map(g -> cooldownScope.genKey(name,g.getIdLong()))
				.orElse(CooldownScope.CHANNEL.genKey(name,event.getChannel().getIdLong()));
			case CHANNEL:      return cooldownScope.genKey(name,event.getChannel().getIdLong());
			case SHARD:
				event.getJDA().getShardInfo();
				return cooldownScope.genKey(name, event.getJDA().getShardInfo().getShardId());
			case USER_SHARD:
				event.getJDA().getShardInfo();
				return cooldownScope.genKey(name,event.getUser().getIdLong(),event.getJDA().getShardInfo().getShardId());
			case GLOBAL:       return cooldownScope.genKey(name, 0);
			default:           return "";
		}
	}

	private MessageCreateData getCooldownErrorString(Cooldown cooldown, GenericInteractionCreateEvent event, int remaining) {
		if (remaining <= 0)
			return null;
		
		StringBuilder front = new StringBuilder(lu.getLocalized(event.getUserLocale(), "errors.cooldown.cooldown_button")
			.replace("{time}", Integer.toString(remaining))
		);
		CooldownScope cooldownScope = cooldown.getScope();
		if (cooldownScope.equals(CooldownScope.USER))
			{}
		else if (cooldownScope.equals(CooldownScope.USER_GUILD) && event.getGuild()==null)
			front.append(" " + lu.getLocalized(event.getUserLocale(), CooldownScope.USER_CHANNEL.getErrorPath()));
		else if (cooldownScope.equals(CooldownScope.GUILD) && event.getGuild()==null)
			front.append(" " + lu.getLocalized(event.getUserLocale(), CooldownScope.CHANNEL.getErrorPath()));
		else
			front.append(" " + lu.getLocalized(event.getUserLocale(), cooldownScope.getErrorPath()));

		return MessageCreateData.fromContent(Objects.requireNonNull(front.append("!").toString()));
	}

}
