package union.listeners;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.entities.*;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import union.App;
import union.base.command.CooldownScope;
import union.base.waiter.EventWaiter;
import union.helper.Helper;
import union.metrics.Metrics;
import union.objects.CaseType;
import union.objects.CmdAccessLevel;
import union.objects.Emotes;
import union.objects.constants.Constants;
import union.objects.constants.Links;
import union.utils.CastUtil;
import union.utils.SteamUtil;
import union.utils.database.DBUtil;
import union.utils.database.managers.CaseManager.CaseData;
import union.utils.database.managers.RoleManager;
import union.utils.database.managers.TicketTagManager.Tag;
import union.utils.exception.FormatterException;
import union.utils.file.lang.LocaleUtil;
import union.utils.message.MessageUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
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
import union.utils.message.TimeUtil;

import static union.utils.CastUtil.castLong;

@SuppressWarnings("DataFlowIssue")
public class InteractionListener extends ListenerAdapter {

	private final App bot;
	private final LocaleUtil lu;
	private final DBUtil db;
	private final EventWaiter waiter;

	private final Set<Permission> adminPerms = Set.of(Permission.ADMINISTRATOR, Permission.MANAGE_SERVER, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_ROLES);
	private final int MAX_GROUP_SELECT = 1;

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

	public void sendErrorLive(IReplyCallback event, String path) {
		event.replyEmbeds(bot.getEmbedUtil().getError(event, path)).setEphemeral(true).queue();
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

	// Check for cooldown parameters, if exists - check if cooldown active, else apply it
	private void runButtonInteraction(ButtonInteractionEvent event, @Nullable Cooldown cooldown, @NotNull Runnable function) {
		// Acknowledge interaction
		event.deferEdit().queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));

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

	@Override
	public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
		String[] actions = event.getComponentId().split(":");

		try {
			if (actions[0].equals("verify")) {
				Metrics.interactionReceived.labelValue("verify").inc();
				runButtonInteraction(event, Cooldown.BUTTON_VERIFY, () -> buttonVerify(event));
				return;
			}
			// Check verified
			if (event.isFromGuild() && !isVerified(event)) return;

			// Continue...
			switch (actions[0]) {
				case "role" -> {
					// Metrics
					Metrics.interactionReceived.labelValue("role:"+actions[1]).inc();
					switch (actions[1]) {
						case "start_request" -> runButtonInteraction(event, Cooldown.BUTTON_ROLE_SHOW, () -> buttonRoleShowSelection(event));
						case "other" -> runButtonInteraction(event, Cooldown.BUTTON_ROLE_OTHER, () -> buttonRoleSelectionOther(event));
						case "clear" -> runButtonInteraction(event, Cooldown.BUTTON_ROLE_CLEAR, () -> buttonRoleSelectionClear(event));
						case "remove" -> runButtonInteraction(event, Cooldown.BUTTON_ROLE_REMOVE, () -> buttonRoleRemove(event));
						case "toggle" -> runButtonInteraction(event, Cooldown.BUTTON_ROLE_TOGGLE, () -> buttonRoleToggle(event));
						case "manage-confirm" -> runButtonInteraction(event, Cooldown.BUTTON_MODIFY_CONFIRM, () -> buttonModifyConfirm(event));
					}
				}
				case "ticket" -> {
					// Metrics
					Metrics.interactionReceived.labelValue("ticket:"+actions[1]).inc();
					switch (actions[1]) {
						case "role_create" -> runButtonInteraction(event, Cooldown.BUTTON_ROLE_TICKET, () -> buttonRoleTicketCreate(event));
						case "role_approve" -> runButtonInteraction(event, Cooldown.BUTTON_ROLE_APPROVE, () -> buttonRoleTicketApprove(event));
						case "close" -> runButtonInteraction(event, Cooldown.BUTTON_TICKET_CLOSE, () -> buttonTicketClose(event));
						case "cancel" -> runButtonInteraction(event, Cooldown.BUTTON_TICKET_CANCEL, () -> buttonTicketCloseCancel(event));
						case "claim" -> runButtonInteraction(event, Cooldown.BUTTON_TICKET_CLAIM, () -> buttonTicketClaim(event));
						case "unclaim" -> runButtonInteraction(event, Cooldown.BUTTON_TICKET_UNCLAIM, () -> buttonTicketUnclaim(event));
					}
				}
				case "tag" -> {
					// Metrics
					Metrics.interactionReceived.labelValue("tag").inc();
					runButtonInteraction(event, Cooldown.BUTTON_TICKET_CREATE, () -> buttonTagCreateTicket(event));
				}
				case "invites" -> {
					// Metrics
					Metrics.interactionReceived.labelValue("invites").inc();
					runButtonInteraction(event, Cooldown.BUTTON_INVITES, () -> buttonShowInvites(event));
				}
				case "delete" -> {
					// Metrics
					Metrics.interactionReceived.labelValue("delete").inc();
					runButtonInteraction(event, Cooldown.BUTTON_REPORT_DELETE, () -> buttonReportDelete(event));
				}
				case "voice" -> {
					if (!event.getMember().getVoiceState().inAudioChannel()) {
						sendErrorLive(event, "bot.voice.listener.not_in_voice");
						return;
					}
					Long channelId = db.voice.getChannel(event.getUser().getIdLong());
					if (channelId == null) {
						sendErrorLive(event, "errors.no_channel");
						return;
					}
					VoiceChannel vc = event.getGuild().getVoiceChannelById(channelId);
					if (vc == null) return;
					// Metrics
					Metrics.interactionReceived.labelValue("voice:"+actions[1]).inc();
					switch (actions[1]) {
						case "lock" -> runButtonInteraction(event, null, () -> buttonVoiceLock(event, vc));
						case "unlock" -> runButtonInteraction(event, null, () -> buttonVoiceUnlock(event, vc));
						case "ghost" -> runButtonInteraction(event, null, () -> buttonVoiceGhost(event, vc));
						case "unghost" -> runButtonInteraction(event, null, () -> buttonVoiceUnghost(event, vc));
						case "permit" -> runButtonInteraction(event, null, () -> buttonVoicePermit(event));
						case "reject" -> runButtonInteraction(event, null, () -> buttonVoiceReject(event));
						case "perms" -> runButtonInteraction(event, null, () -> buttonVoicePerms(event, vc));
						case "delete" -> runButtonInteraction(event, null, () -> buttonVoiceDelete(event, vc));
					}
				}
				case "blacklist" -> {
					// Metrics
					Metrics.interactionReceived.labelValue("blacklist").inc();
					runButtonInteraction(event, Cooldown.BUTTON_SYNC_ACTION, () -> buttonBlacklist(event));
				}
				case "sync_unban" -> {
					// Metrics
					Metrics.interactionReceived.labelValue("sync_unban").inc();
					runButtonInteraction(event, null, () -> buttonSyncUnban(event));
				}
				case "sync_ban" -> {
					// Metrics
					Metrics.interactionReceived.labelValue("sync_ban").inc();
					runButtonInteraction(event, Cooldown.BUTTON_SYNC_ACTION, () -> buttonSyncBan(event));
				}
				case "sync_kick" -> {
					// Metrics
					Metrics.interactionReceived.labelValue("sync_kick").inc();
					runButtonInteraction(event, Cooldown.BUTTON_SYNC_ACTION, () -> buttonSyncKick(event));
				}
				case "strikes" -> {
					// Metrics
					Metrics.interactionReceived.labelValue("strikes").inc();
					runButtonInteraction(event, Cooldown.BUTTON_SHOW_STRIKES, () -> buttonShowStrikes(event));
				}
				case "thread" -> {
					// Metrics
					Metrics.interactionReceived.labelValue("thread").inc();
					switch (actions[1]) {
						case "delete" -> runButtonInteraction(event, Cooldown.BUTTON_THREAD_DELETE, () -> buttonThreadDelete(event));
						case "lock" -> runButtonInteraction(event, Cooldown.BUTTON_THREAD_LOCK, () -> buttonThreadLock(event));
					}
				}
				case "comments" -> {
					Metrics.interactionReceived.labelValue("comments").inc();
					if (actions[1].equals("show")) {
						runButtonInteraction(event, Cooldown.BUTTON_COMMENTS_SHOW, () -> buttonCommentsShow(event));
					}
				}
				default -> bot.getAppLogger().debug("Unknown button interaction: {}", event.getComponentId());
			}
		} catch(Throwable t) {
			// Log throwable and try to respond to the user with the error
			// Thrown errors are not user's error, but code's fault as such things should be caught earlier and replied properly
			bot.getAppLogger().error("ButtonInteraction Exception", t);
			bot.getEmbedUtil().sendUnknownError(event.getHook(), event.getUserLocale(), t.getMessage());
		}
	}

	// Check verified
	private Boolean isVerified(IReplyCallback event) {
		Guild guild = event.getGuild();
		if (!bot.getDBUtil().getVerifySettings(guild).isCheckEnabled()) return true;
		if (bot.getSettings().isDbVerifyDisabled()) return true;

		User user = event.getUser();
		if (bot.getDBUtil().verifyCache.isVerified(user.getIdLong())) return true;

		Role role = guild.getRoleById(db.getVerifySettings(guild).getRoleId());
		if (role == null) return true;
		
		// check if still has account connected
		Long steam64 = bot.getDBUtil().unionVerify.getSteam64(user.getId());
		if (steam64 == null) {
			// remove verification role from user
			try {
				guild.removeRoleFromMember(user, role).reason("Autocheck: No account connected").queue(
					success -> {
						user.openPrivateChannel().queue(dm ->
							dm.sendMessage(bot.getLocaleUtil().getLocalized(guild.getLocale(), "bot.verification.role_removed").replace("{server}", guild.getName()))
								.queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER))
						);
						bot.getLogger().verify.onUnverified(user, null, guild, "Autocheck: No account connected");
					}
				);
			} catch (Exception ignored) {}
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

		Long verifyRoleId = db.getVerifySettings(guild).getRoleId();
		if (verifyRoleId == null) {
			sendError(event, "bot.verification.failed_role", "The verification role is not configured");
			return;
		}
		Role verifyRole = guild.getRoleById(verifyRoleId);
		if (verifyRole == null) {
			sendError(event, "bot.verification.failed_role", "Verification role not found");
			return;
		}
		if (member.getRoles().contains(verifyRole)) {
			sendError(event, "bot.verification.you_verified");
			return;
		}

		// Check if user is blacklisted
		List<Integer> groupIds = new ArrayList<>();
		groupIds.addAll(db.group.getOwnedGroups(guild.getIdLong()));
		groupIds.addAll(db.group.getGuildGroups(guild.getIdLong()));
		for (int groupId : groupIds) {
			if (db.blacklist.inGroupUser(groupId, member.getIdLong()) && db.group.getAppealGuildId(groupId)!=guild.getIdLong()) {
				sendError(event, "bot.verification.blacklisted", "DiscordID: "+member.getId());
				bot.getLogger().verify.onVerifyBlacklisted(member.getUser(), null, guild,
					lu.getText(event, "logger_embed.verify.blacklisted").formatted(groupId));
				return;
			}
		}

		if (bot.getSettings().isDbVerifyDisabled()) {
			// Use simple verification
			// Give verify role to user, do not add to the cache
			Set<Long> additionalRoles = db.getVerifySettings(guild).getAdditionalRoles();
			if (additionalRoles.isEmpty()) {
				guild.addRoleToMember(member, verifyRole).reason("Verification completed - NO STEAM, database disabled").queue(
					success -> {
						bot.getLogger().verify.onVerified(member.getUser(), null, guild);
						event.getHook().sendMessage(Constants.SUCCESS).setEphemeral(true).queue();
					},
					failure -> {
						sendError(event, "bot.verification.failed_role");
						bot.getAppLogger().warn("Was unable to add verify role to user in {}({})", guild.getName(), guild.getId(), failure);
					}
				);
			} else {
				List<Role> finalRoles = new ArrayList<>(member.getRoles());
				// add verify role
				finalRoles.add(verifyRole);
				// add each additional role
				for (Long roleId : additionalRoles) {
					Role role = guild.getRoleById(roleId);
					if (role != null)
						finalRoles.add(role);
				}
				// modify
				guild.modifyMemberRoles(member, finalRoles).reason("Verification completed - NO STEAM, database disabled").queue(
					success -> {
						bot.getLogger().verify.onVerified(member.getUser(), null, guild);
						event.getHook().sendMessage(Constants.SUCCESS).setEphemeral(true).queue();
					},
					failure -> {
						sendError(event, "bot.verification.failed_role");
						bot.getAppLogger().warn("Was unable to add roles to user in {}({})", guild.getName(), guild.getId(), failure);
					}
				);
			}
			return;
		}

		final Long steam64 = bot.getDBUtil().unionVerify.getSteam64(member.getId());
		if (steam64 != null) {
			// Check if steam64 is not blacklisted
			for (int groupId : groupIds) {
				if (db.blacklist.inGroupSteam64(groupId, steam64) && db.group.getAppealGuildId(groupId)!=guild.getIdLong()) {
					sendError(event, "bot.verification.blacklisted", "SteamID: "+SteamUtil.convertSteam64toSteamID(steam64));
					bot.getLogger().verify.onVerifyBlacklisted(member.getUser(), steam64, guild,
						lu.getText(event, "logger_embed.verify.blacklisted").formatted(groupId));
					return;
				}
			}
			// Check if user has required playtime
			try {
				final int minimumPlaytime = db.getVerifySettings(guild).getMinimumPlaytime();
				if (minimumPlaytime > -1) {
					String steamid = SteamUtil.convertSteam64toSteamID(steam64);
					final Long playtime = db.unionPlayers.getPlayTime(guild.getIdLong(), steamid);
					// if user has not joined at least once
					if (playtime == null) {
						// Check backup table, if exists in other table but not in SAM - skip
						if (db.unionPlayers.existsAxePlayer(guild.getIdLong(), steamid)) return;
						// No user
						sendError(event, "bot.verification.playtime_none", "[Your profile (link)](https://unionteams.ru/player/%s)".formatted(steam64));
						bot.getLogger().verify.onVerifyAttempted(member.getUser(), steam64, guild,
							lu.getText(event, "logger_embed.verify.playtime").formatted("none", minimumPlaytime));
						return;
					}
					// if user doesn't have minimum playtime required
					final long played = Math.floorDiv(playtime, 3600);
					if (played < minimumPlaytime) {
						sendError(event, "bot.verification.playtime_minimum", "Required minimum - %s hour/-s\n[Your profile (link)](https://unionteams.ru/player/%s)".formatted(minimumPlaytime, steam64));
						bot.getLogger().verify.onVerifyAttempted(member.getUser(), steam64, guild,
							lu.getText(event, "logger_embed.verify.playtime").formatted(played, minimumPlaytime));
						return;
					}
				}
			} catch (Exception ex) {
				bot.getAppLogger().warn("Exception at playtime check, skipped.", ex);
			}

			Set<Long> additionalRoles = db.getVerifySettings(guild).getAdditionalRoles();
			if (additionalRoles.isEmpty()) {
				guild.addRoleToMember(member, verifyRole).reason("Verification completed - "+steam64).queue(
					success -> {
						bot.getLogger().verify.onVerified(member.getUser(), steam64, guild);
						bot.getDBUtil().verifyCache.addUser(member.getIdLong(), steam64);
						event.getHook().sendMessage(Constants.SUCCESS).setEphemeral(true).queue();
					},
					failure -> {
						sendError(event, "bot.verification.failed_role");
						bot.getAppLogger().warn("Was unable to add verify role to user in {}({})", guild.getName(), guild.getId(), failure);
					}
				);
			} else {
				List<Role> finalRoles = new ArrayList<>(member.getRoles());
				// add verify role
				finalRoles.add(verifyRole);
				// add each additional role
				for (Long roleId : additionalRoles) {
					Role role = guild.getRoleById(roleId);
					if (role != null)
						finalRoles.add(role);
				}
				// modify
				guild.modifyMemberRoles(member, finalRoles).reason("Verification completed - "+steam64).queue(
					success -> {
						bot.getLogger().verify.onVerified(member.getUser(), steam64, guild);
						bot.getDBUtil().verifyCache.addUser(member.getIdLong(), steam64);
						event.getHook().sendMessage(Constants.SUCCESS).setEphemeral(true).queue();
					},
					failure -> {
						sendError(event, "bot.verification.failed_role");
						bot.getAppLogger().warn("Was unable to add roles to user in {}({})", guild.getName(), guild.getId(), failure);
					}
				);
			}
		} else {
			Button refresh = Button.primary("verify:refresh", lu.getText(event, "bot.verification.listener.refresh"))
				.withEmoji(Emoji.fromUnicode("🔁"));
			// Check if user pressed refresh button
			if (event.getButton().getId().endsWith("refresh")) {
				// Ask user to wait for 30 seconds each time
				event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_FAILURE).setTitle(lu.getText(event, "bot.verification.listener.wait_title"))
					.setDescription(lu.getText(event, "bot.verification.listener.wait_value")).build()).setEphemeral(true).queue();
				event.editButton(refresh.asDisabled()).queue(success -> event.editButton(refresh).queueAfter(30, TimeUnit.SECONDS, null, new ErrorHandler().ignore(ContextException.class)));
			} else {
				DiscordLocale locale = event.getGuildLocale();
				// Reply with instruction on how to verify, buttons - link and refresh
				Button verify = Button.link(Links.UNIONTEAM, lu.getLocalized(locale, "bot.verification.listener.connect"));
				EmbedBuilder builder = new EmbedBuilder().setColor(bot.getDBUtil().getGuildSettings(guild).getColor())
					.setTitle(lu.getLocalized(locale, "bot.verification.embed.title"))
					.setDescription(lu.getLocalized(locale, "bot.verification.embed.description"))
					.addField(lu.getLocalized(locale, "bot.verification.embed.howto"), lu.getText(event, "bot.verification.embed.guide"), false)
					.addField(lu.getLocalized(locale, "bot.verification.embed.video"), Links.VERIFY_VIDEO_GUIDE, false);

				event.getHook().sendMessageEmbeds(builder.build()).setActionRow(verify, refresh).setEphemeral(true).queue();
			}
		}
	}

	// Role selection
	private void buttonRoleShowSelection(ButtonInteractionEvent event) {
		Guild guild = event.getGuild();
		long guildId = guild.getIdLong();

		Long channelId = db.ticket.getOpenedChannel(event.getMember().getIdLong(), guildId, 0);
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

		List<ActionRow> actionRows = new ArrayList<>();
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
		List<RoleManager.RoleData> assignRoles = bot.getDBUtil().role.getAssignableByRow(guild.getIdLong(), row);
		if (assignRoles.isEmpty()) return null;
		List<SelectOption> options = new ArrayList<>();
		for (RoleManager.RoleData data : assignRoles) {
			if (options.size() >= 25) break;
			Role role = guild.getRoleById(data.getIdLong());
			if (role == null) continue;
			String description = data.getDescription("-");
			options.add(SelectOption.of(role.getName(), data.getId()).withDescription(description));
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
		List<Long> roleIds = MessageUtil.getRoleIdsFromString(fields.isEmpty() ? "" : fields.get(0).getValue());
		if (roleIds.contains(0L))
			roleIds.remove(0L);
		else
			roleIds.add(0L);
		
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

		List<Role> allRoles = new ArrayList<>();
		db.role.getAssignable(guild.getIdLong()).forEach(data -> allRoles.add(guild.getRoleById(data.get("roleId").toString())));
		db.role.getCustom(guild.getIdLong()).forEach(data -> allRoles.add(guild.getRoleById(data.get("roleId").toString())));
		List<Role> roles = allRoles.stream().filter(currentRoles::contains).toList();
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
				e -> msg.getIdLong() == e.getMessageIdLong(),
				actionEvent -> {
					List<Role> remove = actionEvent.getSelectedOptions().stream().map(option -> guild.getRoleById(option.getValue())).toList();
					guild.modifyMemberRoles(event.getMember(), null, remove).reason("User request").queue(done -> {
						msg.editMessageEmbeds(bot.getEmbedUtil().getEmbed()
							.setDescription(lu.getText(event, "bot.ticketing.listener.remove_done").replace("{roles}", remove.stream().map(Role::getAsMention).collect(Collectors.joining(", "))))
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
		long roleId = Long.parseLong(event.getButton().getId().split(":")[2]);
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
		long guildId = guild.getIdLong();

		// Check if user has selected any role
		List<Field> fields = event.getMessage().getEmbeds().get(0).getFields();
		List<Long> roleIds = MessageUtil.getRoleIdsFromString(fields.isEmpty() ? "" : fields.get(0).getValue());
		if (roleIds.isEmpty()) {
			sendError(event, "bot.ticketing.listener.request_none");
			return;
		}
		// Check if bot is able to give selected roles
		boolean otherRole = roleIds.contains(0L);
		List<Role> memberRoles = event.getMember().getRoles();
		List<Role> add = roleIds.stream()
			.filter(option -> !option.equals(0L))
			.map(guild::getRoleById)
			.filter(role -> role != null && !memberRoles.contains(role))
			.toList();
		if (!otherRole && add.isEmpty()) {
			sendError(event, "bot.ticketing.listener.request_empty");
			return;
		}

		// final role IDs list
		List<String> finalRoleIds = new ArrayList<>();
		add.forEach(role -> {
			String id = role.getId();
			if (db.role.isTemp(role.getIdLong()))
				finalRoleIds.add("t"+id);
			else
				finalRoleIds.add(id);
		});

		int ticketId = 1 + db.ticket.lastIdByTag(guildId, 0);
		event.getChannel().asTextChannel().createThreadChannel(lu.getLocalized(event.getGuildLocale(), "ticket.role")+"-"+ticketId, true).setInvitable(false).queue(
			channel -> {
				int time = bot.getDBUtil().getTicketSettings(guild).getTimeToReply();
				db.ticket.addRoleTicket(ticketId, event.getMember().getIdLong(), guildId, channel.getIdLong(), String.join(";", finalRoleIds), time);
				
				StringBuilder mentions = new StringBuilder(event.getMember().getAsMention());
				// Get either support roles or use mod roles
				List<Long> supportRoleIds = db.ticketSettings.getSettings(guild).getRoleSupportIds();
				if (supportRoleIds.isEmpty()) supportRoleIds = db.access.getRoles(guild.getIdLong(), CmdAccessLevel.MOD);
				supportRoleIds.forEach(roleId -> mentions.append(" <@&").append(roleId).append(">"));
				// Send message
				channel.sendMessage(mentions.toString()).queue(msg -> msg.delete().queueAfter(5, TimeUnit.SECONDS, null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_CHANNEL)));
				
				Long steam64 = db.verifyCache.getSteam64(event.getMember().getIdLong());
				String rolesString = String.join(" ", add.stream().map(Role::getAsMention).collect(Collectors.joining(" ")), (otherRole ? lu.getLocalized(event.getGuildLocale(), "bot.ticketing.embeds.other") : ""));
				String proofString = add.stream().map(role -> db.role.getDescription(role.getIdLong())).filter(Objects::nonNull).distinct().collect(Collectors.joining("\n- ", "- ", ""));
				MessageEmbed embed = new EmbedBuilder().setColor(db.getGuildSettings(guild).getColor())
					.setDescription(String.format("SteamID\n> %s\n%s\n> %s\n\n%s, %s\n%s\n\n%s",
						(steam64 == null ? "None" : SteamUtil.convertSteam64toSteamID(steam64) + "\n> [UnionTeam](https://unionteams.ru/player/"+steam64+")"),
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
				bot.getLogger().ticket.onCreate(guild, channel, event.getUser());
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
		long channelId = event.getChannelIdLong();
		if (db.ticket.isClosed(channelId)) {
			sendError(event, "bot.ticketing.listener.is_closed");
			return;
		}
		Guild guild = event.getGuild();
		Long userId = db.ticket.getUserId(channelId);

		guild.retrieveMemberById(userId).queue(member -> {
			List<Role> tempRoles = new ArrayList<>();
			List<Role> roles = new ArrayList<>();
			db.ticket.getRoleIds(channelId).forEach(v -> {
				if (v.charAt(0) == 't') {
					Role role = guild.getRoleById(CastUtil.castLong(v.substring(1)));
					if (role != null) tempRoles.add(role);
				} else {
					Role role = guild.getRoleById(CastUtil.castLong(v));
					if (role != null) roles.add(role);
				}
			});
			if (!tempRoles.isEmpty()) {
				// Has temp roles - send modal
				List<ActionRow> rows = new ArrayList<>();
				for (Role role : tempRoles) {
					if (rows.size() >= 5) continue;
					TextInput input = TextInput.create(role.getId(), role.getName(), TextInputStyle.SHORT)
						.setPlaceholder("1w - 1 Week, 30d - 30 Days")
						.setRequired(true)
						.setMaxLength(10)
						.build();
					rows.add(ActionRow.of(input));
				}

				Modal modal = Modal.create("ticket:role_temp:"+channelId, lu.getText(event, "bot.ticketing.listener.temp_time"))
					.addComponents(rows)
					.build();
				Button continueButton = Button.success("ticket:role_temp_continue", "Continue");
				event.getHook().sendMessageEmbeds(
					bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getText(event, "bot.ticketing.listener.temp_continue").formatted(rows.size()))
						.build()
					).setActionRow(continueButton)
					.setEphemeral(true)
					.queue(msg -> {
						waiter.waitForEvent(
							ButtonInteractionEvent.class,
							e -> msg.getIdLong() == e.getMessageIdLong(),
							buttonEvent -> {
								buttonEvent.replyModal(modal).queue();
								msg.delete().queue();
								// Maybe reply, that other mod started to fill modal
							},
							10,
							TimeUnit.SECONDS,
							() -> msg.delete().queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE))
						);
					}
				);
				return;
			}
			if (roles.isEmpty()) {
				event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getText(event, "bot.ticketing.listener.role_none"))
					.setColor(Constants.COLOR_WARNING)
					.build()
				).setEphemeral(true).queue();
				return;
			}

			String ticketId = db.ticket.getTicketId(channelId);
			guild.modifyMemberRoles(member, roles, null)
				.reason("Request role-"+ticketId+" approved by "+event.getMember().getEffectiveName())
				.queue(done -> {
				bot.getLogger().role.onApproved(member, event.getMember(), guild, roles, ticketId);
				db.ticket.setClaimed(channelId, event.getMember().getIdLong());
				event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.role_added"))
					.setColor(Constants.COLOR_SUCCESS)
					.build()
				).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_WEBHOOK));
				member.getUser().openPrivateChannel().queue(dm -> {
					Button showInvites = Button.secondary("invites:"+guild.getId(), lu.getLocalized(guild.getLocale(), "bot.ticketing.listener.invites.button"));
					dm.sendMessage(lu.getLocalized(guild.getLocale(), "bot.ticketing.listener.role_dm")
						.replace("{roles}", roles.stream().map(Role::getName).collect(Collectors.joining(" | ")))
						.replace("{server}", guild.getName())
						.replace("{id}", ticketId)
						.replace("{mod}", event.getMember().getEffectiveName())
					).addActionRow(showInvites).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
				});
			}, failure -> {
				sendError(event, "bot.ticketing.listener.role_failed", failure.getMessage());
			});
		}, failure -> {
			sendError(event, "bot.ticketing.listener.no_member", failure.getMessage());
		});
	}

	private void buttonTicketClose(ButtonInteractionEvent event) {
		long channelId = event.getChannelIdLong();
		if (db.ticket.isClosed(channelId)) {
			// Ticket is closed
			event.getChannel().delete().queue();
			return;
		}
		// Check who can close tickets
		final boolean isAuthor = db.ticket.getUserId(channelId).equals(event.getUser().getIdLong());
		if (!isAuthor) {
			switch (db.getTicketSettings(event.getGuild()).getAllowClose()) {
				case EVERYONE -> {}
				case HELPER -> {
					// Check if user has Helper+ access
					if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.HELPER)) {
						// No access - reject
						sendError(event, "errors.interaction.no_access", "Helper+ access");
						return;
					}
				}
				case SUPPORT -> {
					// Check if user is ticket support or has Admin+ access
					int tagId = db.ticket.getTag(channelId);
					if (tagId==0) {
						// Role request ticket
						List<Long> supportRoleIds = db.getTicketSettings(event.getGuild()).getRoleSupportIds();
						if (supportRoleIds.isEmpty()) supportRoleIds = db.access.getRoles(event.getGuild().getIdLong(), CmdAccessLevel.MOD);
						// Check
						if (denyCloseSupport(supportRoleIds, event.getMember())) {
							sendError(event, "errors.interaction.no_access", "'Support' for this ticket or Admin+ access");
							return;
						}
					} else {
						// Standard ticket
						final List<Long> supportRoleIds = Stream.of(db.tags.getSupportRolesString(tagId).split(";"))
							.map(Long::parseLong)
							.toList();
						// Check
						if (denyCloseSupport(supportRoleIds, event.getMember())) {
							sendError(event, "errors.interaction.no_access", "'Support' for this ticket or Admin+ access");
							return;
						}
					}
				}
			}
		}
		// Close
		String reason = isAuthor ? lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.closed_author")
			: lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.closed_support");
		event.editButton(Button.danger("ticket:close", bot.getLocaleUtil().getLocalized(event.getGuildLocale(), "ticket.close")).withEmoji(Emoji.fromUnicode("🔒")).asDisabled()).queue();
		// Send message
		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event).setDescription(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.delete_countdown")).build()).queue(msg -> {
			bot.getTicketUtil().closeTicket(channelId, event.getUser(), reason, failure -> {
				if (ErrorResponse.UNKNOWN_CHANNEL.test(failure)) return; // skip if channel aready gone ;(
				msg.editMessageEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.close_failed", failure.getMessage())).queue();
				bot.getAppLogger().error("Couldn't close ticket with channelID: {}", channelId, failure);
			});
		});
	}

	private boolean denyCloseSupport(List<Long> supportRoleIds, Member member) {
		if (supportRoleIds.isEmpty()) return false; // No data to check against
		final List<Role> roles = member.getRoles(); // Check if user has any support role
		if (!roles.isEmpty() && roles.stream().anyMatch(r -> supportRoleIds.contains(r.getIdLong()))) return false;
		return !bot.getCheckUtil().hasAccess(member, CmdAccessLevel.ADMIN); // if user has Admin access
	}

	private void buttonTicketCloseCancel(ButtonInteractionEvent event) {
		long channelId = event.getChannelIdLong();
		Guild guild = event.getGuild();

		if (db.ticket.isClosed(channelId)) {
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
		long channelId = event.getChannelIdLong();
		if (db.ticket.isClosed(channelId)) {
			sendError(event, "bot.ticketing.listener.is_closed");
			return;
		}

		db.ticket.setClaimed(channelId, event.getUser().getIdLong());
		event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
			.setDescription(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.claimed").replace("{user}", event.getUser().getAsMention()))
			.build()
		).queue();

		Button close = Button.danger("ticket:close", lu.getLocalized(event.getGuildLocale(), "ticket.close")).withEmoji(Emoji.fromUnicode("🔒"));
		Button claimed = Button.primary("ticket:claimed", lu.getLocalized(event.getGuildLocale(), "ticket.claimed").formatted(event.getUser().getName())).asDisabled();
		Button unclaim = Button.primary("ticket:unclaim", lu.getLocalized(event.getGuildLocale(), "ticket.unclaim"));
		event.getMessage().editMessageComponents(ActionRow.of(close, claimed, unclaim)).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
	}

	private void buttonTicketUnclaim(ButtonInteractionEvent event) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.HELPER)) {
			// User has no Helper's access or higher to approve role request
			sendError(event, "errors.interaction.no_access");
			return;
		}
		long channelId = event.getChannelIdLong();
		if (db.ticket.isClosed(channelId)) {
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
		event.getMessage().editMessageComponents(ActionRow.of(close, claim)).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
	}

	// Tag, create ticket
	private void buttonTagCreateTicket(ButtonInteractionEvent event) {
		long guildId = event.getGuild().getIdLong();
		int tagId = Integer.parseInt(event.getComponentId().split(":")[1]);

		Long openChannelId = db.ticket.getOpenedChannel(event.getMember().getIdLong(), guildId, tagId);
		if (openChannelId != null) {
			GuildChannel channel = event.getGuild().getGuildChannelById(openChannelId);
			if (channel != null) {
				event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_FAILURE)
					.setDescription(lu.getText(event, "bot.ticketing.listener.ticket_exists").replace("{channel}", channel.getAsMention()))
					.build()
				).setEphemeral(true).queue();
				return;
			}
			db.ticket.closeTicket(Instant.now(), openChannelId, "BOT: Channel deleted (not found)");
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
		
		int ticketId = 1 + db.ticket.lastIdByTag(guildId, tagId);
		String ticketName = (tag.getTicketName()+ticketId).replace("{username}", user.getName());
		if (tag.getTagType() == 1) {
			// Thread ticket
			event.getChannel().asTextChannel().createThreadChannel(ticketName, true).setInvitable(false).queue(channel -> {
				int time = bot.getDBUtil().getTicketSettings(event.getGuild()).getTimeToReply();
				db.ticket.addTicket(ticketId, user.getIdLong(), guildId, channel.getIdLong(), tagId, time);
				
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
			for (String roleId : supportRoles) action = action.addRolePermissionOverride(Long.parseLong(roleId), EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), null);
			action.addPermissionOverride(event.getGuild().getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
				.addMemberPermissionOverride(user.getIdLong(), EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), null)
				.queue(channel -> {
				int time = bot.getDBUtil().getTicketSettings(event.getGuild()).getTimeToReply();
				db.ticket.addTicket(ticketId, user.getIdLong(), guildId, channel.getIdLong(), tagId, time);

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
			bot.getAppLogger().warn("PermsCmd null pointer at role override remove");
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
			bot.getAppLogger().warn("PermsCmd null pointer at member override remove");
		}

		EmbedBuilder embedBuilder2 = embedBuilder;
		List<PermissionOverride> ovs = overrides;

		guild.retrieveMembersByIds(false, overrides.stream().map(ISnowflake::getId).toArray(String[]::new)).onSuccess(
			members -> {
				if (members.isEmpty()) {
					embedBuilder2.appendDescription(lu.getText(event, "bot.voice.listener.panel.perms.none") + "\n");
				} else {
					for (PermissionOverride ov : ovs) {
						String view2 = contains(ov, Permission.VIEW_CHANNEL);
						String join2 = contains(ov, Permission.VOICE_CONNECT);

						String name = members.stream()
								.filter(m -> m.getId().equals(ov.getId()))
								.findFirst()
								.map(Member::getEffectiveName)
								.orElse("Unknown");

						embedBuilder2.appendDescription("> %s | %s | `%s`\n".formatted(view2, join2, name));
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
			Map<Long, String> roles = bot.getDBUtil().role.getRolesWithInvites(guild.getIdLong());
			Map<String, String> invites = event.getMember().getRoles().stream()
				.filter(role -> roles.containsKey(role.getIdLong()))
				.collect(Collectors.toMap(Role::getName, role -> roles.get(role.getIdLong())));
			sendInvites(event, guild, invites);
		} else {
			Guild guild = event.getJDA().getGuildById(event.getComponentId().split(":")[1]);
			if (guild == null) {
				sendError(event, "bot.ticketing.listener.invites.no_guild");
				return;
			}
			guild.retrieveMember(event.getUser()).queue(member -> {
				Map<Long, String> roles = bot.getDBUtil().role.getRolesWithInvites(guild.getIdLong());
				Map<String, String> invites = member.getRoles().stream()
					.filter(role -> roles.containsKey(role.getIdLong()))
					.collect(Collectors.toMap(Role::getName, role -> roles.get(role.getIdLong())));
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

		if (Helper.getInstance() == null) {
			sendError(event, "errors.no_helper");
			return;
		}

		String userId = event.getComponentId().split(":")[1];
		CaseData caseData = db.cases.getMemberActive(Long.parseLong(userId), event.getGuild().getIdLong(), CaseType.BAN);
		if (caseData == null || !caseData.getDuration().isZero()) {
			sendError(event, "bot.moderation.blacklist.expired");
			return;
		}

		long guildId = event.getGuild().getIdLong();
		List<Integer> groupIds = new ArrayList<>();
		groupIds.addAll(bot.getDBUtil().group.getOwnedGroups(guildId));
		groupIds.addAll(bot.getDBUtil().group.getManagedGroups(guildId));
		if (groupIds.isEmpty()) {
			sendError(event, "bot.moderation.blacklist.no_groups");
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
			.setMaxValues(MAX_GROUP_SELECT)
			.build();

		event.getHook().sendMessageEmbeds(embed).setActionRow(menu).setEphemeral(true).queue(msg -> {
			waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> msg.getIdLong() == e.getMessageIdLong(),
				selectEvent -> {
					selectEvent.deferEdit().queue();
					List<Integer> selected = selectEvent.getValues().stream().map(Integer::parseInt).toList();

					event.getJDA().retrieveUserById(userId).queue(user -> {
						Long steam64 = db.verifyCache.getSteam64(user.getIdLong());
						selected.forEach(groupId -> {
							if (!db.blacklist.inGroupUser(groupId, caseData.getTargetId()))
								db.blacklist.add(selectEvent.getGuild().getIdLong(), groupId, user.getIdLong(), steam64, caseData.getReason(), selectEvent.getUser().getIdLong());
	
							Helper.getInstance().runBan(groupId, event.getGuild(), user, caseData.getReason());
						});

						// Log to master
						bot.getLogger().mod.onBlacklistAdded(event.getUser(), user, steam64, selected);
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
				30,
				TimeUnit.SECONDS,
				() -> msg.editMessageComponents(ActionRow.of(menu.asDisabled())).queue()
			);
		});
	}

	private void buttonSyncBan(ButtonInteractionEvent event) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.OPERATOR)) {
			sendError(event, "errors.interaction.no_access");
			return;
		}

		if (Helper.getInstance() == null) {
			sendError(event, "errors.no_helper");
			return;
		}

		String userId = event.getComponentId().split(":")[1];
		CaseData caseData = db.cases.getMemberActive(Long.parseLong(userId), event.getGuild().getIdLong(), CaseType.BAN);
		if (caseData == null || !caseData.getDuration().isZero()) {
			sendError(event, "bot.moderation.sync.expired");
			return;
		}

		long guildId = event.getGuild().getIdLong();
		List<Integer> groupIds = new ArrayList<>();
		groupIds.addAll(bot.getDBUtil().group.getOwnedGroups(guildId));
		groupIds.addAll(bot.getDBUtil().group.getManagedGroups(guildId));
		if (groupIds.isEmpty()) {
			sendError(event, "bot.moderation.sync.no_groups");
			return;
		}

		MessageEmbed embed = bot.getEmbedUtil().getEmbed()
			.setColor(Constants.COLOR_WARNING)
			.setDescription(lu.getText(event, "bot.moderation.sync.ban.title"))
			.build();
		StringSelectMenu menu = StringSelectMenu.create("groupId")
			.setPlaceholder(lu.getText(event, "bot.moderation.sync.select"))
			.addOptions(groupIds.stream().map(groupId ->
				SelectOption.of(bot.getDBUtil().group.getName(groupId), groupId.toString()).withDescription("ID: "+groupId)
			).collect(Collectors.toList()))
			.setMaxValues(MAX_GROUP_SELECT)
			.build();

		event.getHook().sendMessageEmbeds(embed).setActionRow(menu).setEphemeral(true).queue(msg -> {
			waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> msg.getIdLong() == e.getMessageIdLong(),
				selectEvent -> {
					selectEvent.deferEdit().queue();
					List<Integer> selected = selectEvent.getValues().stream().map(Integer::parseInt).toList();

					event.getJDA().retrieveUserById(userId).queue(user -> {
						selected.forEach(groupId -> Helper.getInstance().runBan(groupId, event.getGuild(), user, caseData.getReason()));
						// Reply
						selectEvent.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed()
							.setColor(Constants.COLOR_SUCCESS)
							.setDescription(lu.getText(event, "bot.moderation.sync.ban.done"))
							.build())
						.setComponents().queue();
					},
					failure -> {
						selectEvent.getHook().editOriginalEmbeds(
							bot.getEmbedUtil().getError(selectEvent, "bot.moderation.sync.no_user", failure.getMessage())
						).setComponents().queue();
					});
				},
				30,
				TimeUnit.SECONDS,
				() -> msg.editMessageComponents(ActionRow.of(menu.asDisabled())).queue()
			);
		});
	}

	private void buttonSyncUnban(ButtonInteractionEvent event) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.OPERATOR)) {
			sendError(event, "errors.interaction.no_access");
			return;
		}

		if (Helper.getInstance() == null) {
			sendError(event, "errors.no_helper");
			return;
		}

		long guildId = event.getGuild().getIdLong();
		List<Integer> groupIds = new ArrayList<>();
		groupIds.addAll(bot.getDBUtil().group.getOwnedGroups(guildId));
		groupIds.addAll(bot.getDBUtil().group.getManagedGroups(guildId));
		if (groupIds.isEmpty()) {
			sendError(event, "bot.moderation.sync.no_groups");
			return;
		}

		MessageEmbed embed = bot.getEmbedUtil().getEmbed()
			.setColor(Constants.COLOR_WARNING)
			.setDescription(lu.getText(event, "bot.moderation.sync.unban.title"))
			.build();
		StringSelectMenu menu = StringSelectMenu.create("groupId")
			.setPlaceholder(lu.getText(event, "bot.moderation.sync.select"))
			.addOptions(groupIds.stream().map(groupId ->
				SelectOption.of(bot.getDBUtil().group.getName(groupId), groupId.toString()).withDescription("ID: "+groupId)
			).collect(Collectors.toList()))
			.setMaxValues(MAX_GROUP_SELECT)
			.build();

		event.getHook().sendMessageEmbeds(embed).setActionRow(menu).setEphemeral(true).queue(msg -> {
			waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> msg.getIdLong() == e.getMessageIdLong(),
				selectEvent -> {
					selectEvent.deferEdit().queue();
					List<Integer> selected = selectEvent.getValues().stream().map(Integer::parseInt).toList();

					event.getJDA().retrieveUserById(event.getComponentId().split(":")[1]).queue(user -> {
						selected.forEach(groupId -> {
							if (db.blacklist.inGroupUser(groupId, user.getIdLong())) {
								db.blacklist.removeUser(groupId, user.getIdLong());
								bot.getLogger().mod.onBlacklistRemoved(event.getUser(), user, null, groupId);
							}
	
							Helper.getInstance().runUnban(groupId, event.getGuild(), user, "Sync group unban, by "+event.getUser().getName());
						});

						// Reply
						selectEvent.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed()
							.setColor(Constants.COLOR_SUCCESS)
							.setDescription(lu.getText(event, "bot.moderation.sync.unban.done"))
							.build())
						.setComponents().queue();
					},
					failure -> {
						selectEvent.getHook().editOriginalEmbeds(
							bot.getEmbedUtil().getError(selectEvent, "bot.moderation.sync.no_user", failure.getMessage())
						).setComponents().queue();
					});
				},
				30,
				TimeUnit.SECONDS,
				() -> msg.editMessageComponents(ActionRow.of(menu.asDisabled())).queue()
			);
		});
	}

	private void buttonSyncKick(ButtonInteractionEvent event) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.OPERATOR)) {
			sendError(event, "errors.interaction.no_access");
			return;
		}

		if (Helper.getInstance() == null) {
			sendError(event, "errors.no_helper");
			return;
		}

		String userId = event.getComponentId().split(":")[1];
		CaseData caseData = db.cases.getMemberActive(Long.parseLong(userId), event.getGuild().getIdLong(), CaseType.BAN);
		if (caseData == null || !caseData.getDuration().isZero()) {
			sendError(event, "bot.moderation.sync.expired");
			return;
		}

		long guildId = event.getGuild().getIdLong();
		List<Integer> groupIds = new ArrayList<>();
		groupIds.addAll(bot.getDBUtil().group.getOwnedGroups(guildId));
		groupIds.addAll(bot.getDBUtil().group.getManagedGroups(guildId));
		if (groupIds.isEmpty()) {
			sendError(event, "bot.moderation.sync.no_groups");
			return;
		}

		MessageEmbed embed = bot.getEmbedUtil().getEmbed()
			.setColor(Constants.COLOR_WARNING)
			.setDescription(lu.getText(event, "bot.moderation.sync.kick.title"))
			.build();
		StringSelectMenu menu = StringSelectMenu.create("groupId")
			.setPlaceholder(lu.getText(event, "bot.moderation.sync.select"))
			.addOptions(groupIds.stream().map(groupId ->
				SelectOption.of(bot.getDBUtil().group.getName(groupId), groupId.toString()).withDescription("ID: "+groupId)
			).collect(Collectors.toList()))
			.setMaxValues(MAX_GROUP_SELECT)
			.build();

		event.getHook().sendMessageEmbeds(embed).setActionRow(menu).setEphemeral(true).queue(msg -> {
			waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> msg.getIdLong() == e.getMessageIdLong(),
				selectEvent -> {
					selectEvent.deferEdit().queue();
					List<Integer> selected = selectEvent.getValues().stream().map(Integer::parseInt).toList();

					event.getJDA().retrieveUserById(userId).queue(user -> {
						selected.forEach(groupId -> Helper.getInstance().runKick(groupId, event.getGuild(), user, caseData.getReason()));
						// Reply
						selectEvent.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed()
							.setColor(Constants.COLOR_SUCCESS)
							.setDescription(lu.getText(event, "bot.moderation.sync.kick.done"))
							.build())
						.setComponents().queue();
					},
					failure -> {
						selectEvent.getHook().editOriginalEmbeds(
							bot.getEmbedUtil().getError(selectEvent, "bot.moderation.sync.no_user", failure.getMessage())
						).setComponents().queue();
					});
				},
				30,
				TimeUnit.SECONDS,
				() -> msg.editMessageComponents(ActionRow.of(menu.asDisabled())).queue()
			);
		});
	}

	// Strikes
	private void buttonShowStrikes(ButtonInteractionEvent event) {
		long guildId = Long.parseLong(event.getComponentId().split(":")[1]);
		Guild guild = event.getJDA().getGuildById(guildId);
		if (guild == null) {
			sendError(event, "errors.error", "Server not found.");
			return;
		}
		Pair<Integer, Integer> strikeData = bot.getDBUtil().strike.getDataCountAndDate(guildId, event.getUser().getIdLong());
		if (strikeData == null) {
			event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, "bot.moderation.no_strikes").formatted(guild.getName())).build()).queue();
			return;
		}
		
		Instant time = Instant.ofEpochSecond(strikeData.getRight());
		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed()
			.setDescription(lu.getText(event, "bot.moderation.strikes_embed").formatted(strikeData.getLeft(), TimeFormat.RELATIVE.atInstant(time)))
			.build()
		).setEphemeral(true).queue();
	}

	// Roles modify
	private void buttonModifyConfirm(ButtonInteractionEvent event) {
		long guildId = event.getGuild().getIdLong();
		long userId = event.getUser().getIdLong();
		long targetId = Long.parseLong(event.getComponentId().split(":")[2]);

		// If expired don't allow to modify embed
		if (db.modifyRole.isExpired(guildId, userId, targetId)) {
			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.roles.role.modify.expired"))
					.setComponents().queue();
			return;
		}

		event.getGuild().retrieveMemberById(targetId).queue(target -> {
			List<Long> addIds = new ArrayList<>();
			List<Long> removeIds = new ArrayList<>();
			// Retrieve selected roles
			for (String line : db.modifyRole.getRoles(guildId, userId, targetId).split(":")) {
				if (line.isBlank()) continue;
				String[] roleIds = line.split(";");
				for (String roleId : roleIds) {
					// Check if first char is '+' add or '-' remove
					if (roleId.charAt(0) == '+') addIds.add(Long.parseLong(roleId.substring(1)));
					else removeIds.add(Long.parseLong(roleId.substring(1)));
				}
			}
			if (addIds.isEmpty() && removeIds.isEmpty()) {
				sendError(event, "bot.roles.role.modify.no_change");
				return;
			}

			Guild guild = target.getGuild();
			List<Role> finalRoles = new ArrayList<>(target.getRoles());
			finalRoles.addAll(addIds.stream().map(guild::getRoleById).toList());
			finalRoles.removeAll(removeIds.stream().map(guild::getRoleById).toList());

			guild.modifyMemberRoles(target, finalRoles).reason("by "+event.getMember().getEffectiveName()).queue(done -> {
				// Remove from DB
				db.modifyRole.remove(guildId, userId, targetId);
				// text
				StringBuilder builder = new StringBuilder();
				if (!addIds.isEmpty()) builder.append("\n**Added**: ")
						.append(addIds.stream().map(String::valueOf).collect(Collectors.joining(">, <@&", "<@&", ">")));
				if (!removeIds.isEmpty()) builder.append("\n**Removed**: ")
						.append(removeIds.stream().map(String::valueOf).collect(Collectors.joining(">, <@&", "<@&", ">")));
				String rolesString = builder.toString();
				// Log
				bot.getLogger().role.onRolesModified(guild, event.getUser(), target.getUser(), rolesString);
				// Send reply
				event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, "bot.roles.role.modify.done").formatted(target.getAsMention(), rolesString))
						.build()
						).setComponents().queue();
			}, failure -> event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "errors.error", "Unable to modify roles, User ID: "+targetId))
					.setComponents().queue()
			);
		}, failure -> event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "errors.error", "Member not found, ID: "+targetId))
				.setComponents().queue()
		);
	}

	// Thread controls
	private void buttonThreadDelete(ButtonInteractionEvent event) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.HELPER)) {
			// User has no Helper access or higher
			sendError(event, "errors.interaction.no_access");
			return;
		}

		if (!event.getChannelType().equals(ChannelType.GUILD_PUBLIC_THREAD)) {
			sendError(event, "errors.error", "Channel is not a public thread. Will not delete this channel.");
			return;
		}

		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, "threads.locked"))
				.build()).queue(msg -> {
			event.getChannel().delete().queueAfter(5, TimeUnit.SECONDS);
		});
	}

	private void buttonThreadLock(ButtonInteractionEvent event) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.HELPER)) {
			// User has no Helper access or higher
			sendError(event, "errors.interaction.no_access");
			return;
		}

		if (!event.getChannelType().equals(ChannelType.GUILD_PUBLIC_THREAD)) {
			sendError(event, "errors.error", "Channel is not a public thread.");
			return;
		}

		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, "threads.locked"))
				.build()
		).queue(msg -> {
			event.getChannel().asThreadChannel().getManager().setLocked(true).setArchived(true)
					.reason("By "+event.getUser().getEffectiveName()).queueAfter(5, TimeUnit.SECONDS);
		});
	}

	// Comments
	private void buttonCommentsShow(ButtonInteractionEvent event) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.HELPER)) {
			// User has no Helper access or higher
			sendError(event, "errors.interaction.no_access");
			return;
		}

		long steam64 = Long.parseLong(event.getComponentId().split(":")[2]);
		List<Map<String, Object>> comments = db.comments.getComments(steam64);

		if (comments.isEmpty()) {
			event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed()
				.setDescription(lu.getText(event, "bot.verification.account.no_comments"))
				.build()
			).queue();
			return;
		}

		StringBuilder builder = new StringBuilder();
		comments.forEach(m -> {
			if (builder.length() > 4000) return;
			final Instant timestamp = Instant.ofEpochSecond(castLong(m.get("timestamp")));
			builder.append("\n> ").append(m.get("comment"))
				.append("\n\\- <@").append(m.get("authorId")).append("> ")
				.append(TimeUtil.formatTime(timestamp, false));
		});

		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed()
			.setDescription(builder.toString())
			.build()
		).setEphemeral(true).queue();
	}


	// MODALS
	@Override
	public void onModalInteraction(@NotNull ModalInteractionEvent event) {
		event.deferEdit().queue();
		String modalId = event.getModalId();

		if (modalId.equals("vfpanel")) {
			if (event.getValues().isEmpty()) {
				sendError(event, "errors.interaction.no_values");
				return;
			}

			String main = event.getValue("main").getAsString();
			db.verifySettings.setMainText(event.getGuild().getIdLong(), main.isBlank() ? "NULL" : main);

			event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, "bot.verification.vfpanel.text.done"))
				.build()
			).setEphemeral(true).queue();
		} else if (modalId.startsWith("ticket:role_temp")) {
			// Check if ticket is open
			long channelId = Long.parseLong(modalId.split(":")[2]);
			if (db.ticket.isClosed(channelId)) {
				// Ignore
				return;
			}
			Guild guild = event.getGuild();
			Long userId = db.ticket.getUserId(channelId);

			// Get roles and tempRoles
			List<Role> roles = new ArrayList<>();
			db.ticket.getRoleIds(channelId).forEach(v -> {
				long roleId = CastUtil.castLong(
					v.charAt(0) == 't' ? v.substring(1) : v
				);
				Role role = guild.getRoleById(roleId);
				if (role != null) roles.add(role);
			});
			if (roles.isEmpty()) {
				event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getText(event, "bot.ticketing.listener.role_none"))
					.setColor(Constants.COLOR_WARNING)
					.build()
				).setEphemeral(true).queue();
				return;
			}

			// Get member add set roles
			event.getGuild().retrieveMemberById(userId).queue(member -> {
				// Add role durations to list
				Map<Long, Duration> roleDurations = new HashMap<>();
				for (ModalMapping map : event.getValues()) {
					long roleId = Long.parseLong(map.getId());
					String value = map.getAsString();
					// Check duration
					final Duration duration;
					try {
						duration = TimeUtil.stringToDuration(value, false);
					} catch (FormatterException ex) {
						sendError(event, ex.getPath());
						return;
					}
					// Add to temp only if duration not zero and between 10 minutes and 150 days
					if (!duration.isZero()) {
						if (duration.toMinutes() < 10 || duration.toDays() > 150) {
							sendError(event, "bot.ticketing.listener.time_limit", "Received: "+duration);
							return;
						}
						roleDurations.put(roleId, duration);
					}
				}

				String ticketId = db.ticket.getTicketId(channelId);
				// Modify roles
				event.getGuild().modifyMemberRoles(member, roles, null)
					.reason("Request role-"+ticketId+" approved by "+event.getMember().getEffectiveName())
					.queue(done -> {
						// Set claimed
						db.ticket.setClaimed(channelId, event.getMember().getIdLong());
						// Add tempRoles to db and log them
						roleDurations.forEach((id, duration) -> {
							bot.getDBUtil().tempRole.add(guild.getIdLong(), id, userId, false, Instant.now().plus(duration));
							// Log
							bot.getLogger().role.onTempRoleAdded(guild, event.getUser(), member.getUser(), id, duration, false);
						});
						// Log approval
						bot.getLogger().role.onApproved(member, event.getMember(), guild, roles, ticketId);
						// Reply and send DM to the target member
						event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
							.setDescription(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.role_added"))
							.setColor(Constants.COLOR_SUCCESS)
							.build()
						).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_WEBHOOK));
						member.getUser().openPrivateChannel().queue(dm -> {
							Button showInvites = Button.secondary("invites:"+guild.getId(), lu.getLocalized(guild.getLocale(), "bot.ticketing.listener.invites.button"));
							dm.sendMessage(lu.getLocalized(guild.getLocale(), "bot.ticketing.listener.role_dm")
								.replace("{roles}", roles.stream().map(Role::getName).collect(Collectors.joining(" | ")))
								.replace("{server}", guild.getName())
								.replace("{id}", ticketId)
								.replace("{mod}", event.getMember().getEffectiveName())
							).addActionRow(showInvites).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
						});
				}, failure -> {
					sendError(event, "bot.ticketing.listener.role_failed", failure.getMessage());
				});
			}, failure -> {
				sendError(event, "bot.ticketing.listener.no_member", failure.getMessage());
			});

		}
	}

	@Override
	public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
		String menuId = event.getComponentId();

		if (menuId.startsWith("menu:role_row")) {
			event.deferEdit().queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));

			List<Field> fields = event.getMessage().getEmbeds().get(0).getFields();
			List<Long> roleIds = MessageUtil.getRoleIdsFromString(fields.isEmpty() ? "" : fields.get(0).getValue());
			event.getSelectedOptions().forEach(option -> {
				Long value = CastUtil.castLong(option.getValue());
				if (!roleIds.contains(value)) roleIds.add(value);
			});

			MessageEmbed embed = new EmbedBuilder(event.getMessage().getEmbeds().get(0))
				.clearFields()
				.addField(lu.getText(event, "bot.ticketing.listener.request_selected"), selectedRolesString(roleIds, event.getUserLocale()), false)
				.build();
			event.getHook().editOriginalEmbeds(embed).queue();
		} else if (menuId.startsWith("role:manage-select")) {
			listModifySelect(event);
		}
	}

	private final Pattern splitPattern = Pattern.compile(":");

	// Roles modify
	private void listModifySelect(StringSelectInteractionEvent event) {
		event.deferEdit().queue();
		try {
			long guildId = event.getGuild().getIdLong();
			long userId = event.getUser().getIdLong();
			long targetId = Long.parseLong(event.getComponentId().split(":")[3]);

			// If expired don't allow to modify
			if (db.modifyRole.isExpired(guildId, userId, targetId)) {
				event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.roles.role.modify.expired"))
						.setComponents().queue();
				return;
			}

			List<String> changes = new ArrayList<>();

			List<SelectOption> defaultOptions = event.getSelectMenu().getOptions().stream().filter(SelectOption::isDefault).toList();
			List<SelectOption> selectedOptions = event.getSelectedOptions();
			// if default is not in selected - role is removed
			for (SelectOption option : defaultOptions) {
				if (!selectedOptions.contains(option)) changes.add("-"+option.getValue());
			}
			// if selected is not in default - role is added
			for (SelectOption option : selectedOptions) {
				if (!defaultOptions.contains(option)) changes.add("+"+option.getValue());
			}

			String newValue = String.join(";", changes);

			// "1:2:3:4"
			// each section stores changes for each menu
			int menuId = Integer.parseInt(event.getComponentId().split(":")[2]);
			String[] data = splitPattern.split(db.modifyRole.getRoles(guildId, userId, targetId), 4);
			if (data.length != 4) data = new String[]{"", "", "", ""};
			data[menuId-1] = newValue;
			db.modifyRole.update(guildId, userId, targetId, String.join(":", data), Instant.now().plus(2, ChronoUnit.MINUTES));
		} catch(Throwable t) {
			// Log throwable and try to respond to the user with the error
			// Thrown errors are not user's error, but code's fault as such things should be caught earlier and replied properly
			bot.getAppLogger().error("Role modify Exception", t);
			bot.getEmbedUtil().sendUnknownError(event.getHook(), event.getUserLocale(), t.getMessage());
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
				String text;

				VoiceChannelManager manager = vc.getManager();
				
				if (action.equals("permit")) {
					for (Member member : members) {
						manager = manager.putPermissionOverride(member, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null);
						mentionStrings.add(member.getEffectiveName());
					}
		
					for (Role role : roles) {
						EnumSet<Permission> rolePerms = EnumSet.copyOf(role.getPermissions());
						rolePerms.retainAll(adminPerms);
						if (rolePerms.isEmpty()) {
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
						EnumSet<Permission> rolePerms = EnumSet.copyOf(role.getPermissions());
						rolePerms.retainAll(adminPerms);
						if (rolePerms.isEmpty()) {
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
	private String selectedRolesString(List<Long> roleIds, DiscordLocale locale) {
		if (roleIds.isEmpty()) return "None";
		return roleIds.stream()
			.map(id -> (id.equals(0L) ? "+"+lu.getLocalized(locale, "bot.ticketing.embeds.other") : "<@&%s>".formatted(id)))
			.collect(Collectors.joining(", "));
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
		BUTTON_SHOW_STRIKES(30, CooldownScope.USER),
		BUTTON_SYNC_ACTION(10, CooldownScope.CHANNEL),
		BUTTON_MODIFY_CONFIRM(10, CooldownScope.USER),
		BUTTON_THREAD_DELETE(10, CooldownScope.GUILD),
		BUTTON_THREAD_LOCK(10, CooldownScope.GUILD),
		BUTTON_COMMENTS_SHOW(10, CooldownScope.USER),;

		private final int time;
		private final CooldownScope scope;

		Cooldown(int time, @NotNull CooldownScope scope) {
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
		return switch (cooldown.getScope()) {
			case USER         -> cooldownScope.genKey(name,event.getUser().getIdLong());
			case USER_GUILD   -> Optional.of(event.getGuild()).map(g -> cooldownScope.genKey(name,event.getUser().getIdLong(),g.getIdLong()))
				.orElse(CooldownScope.USER_CHANNEL.genKey(name,event.getUser().getIdLong(), event.getChannel().getIdLong()));
			case USER_CHANNEL -> cooldownScope.genKey(name,event.getUser().getIdLong(),event.getChannel().getIdLong());
			case GUILD        -> Optional.of(event.getGuild()).map(g -> cooldownScope.genKey(name,g.getIdLong()))
				.orElse(CooldownScope.CHANNEL.genKey(name,event.getChannel().getIdLong()));
			case CHANNEL      -> cooldownScope.genKey(name,event.getChannel().getIdLong());
			case SHARD        -> cooldownScope.genKey(name, event.getJDA().getShardInfo().getShardId());
			case USER_SHARD   -> cooldownScope.genKey(name,event.getUser().getIdLong(),event.getJDA().getShardInfo().getShardId());
			case GLOBAL       -> cooldownScope.genKey(name, 0);
		};
	}

	private MessageCreateData getCooldownErrorString(Cooldown cooldown, GenericInteractionCreateEvent event, int remaining) {
		if (remaining <= 0)
			return null;
		
		StringBuilder front = new StringBuilder(lu.getLocalized(event.getUserLocale(), "errors.cooldown.cooldown_button")
			.replace("{time}", Integer.toString(remaining))
		);
		CooldownScope cooldownScope = cooldown.getScope();
		if (cooldownScope.equals(CooldownScope.USER_GUILD) && event.getGuild()==null)
			front.append(" ").append(lu.getLocalized(event.getUserLocale(), CooldownScope.USER_CHANNEL.getErrorPath()));
		else if (cooldownScope.equals(CooldownScope.GUILD) && event.getGuild()==null)
			front.append(" ").append(lu.getLocalized(event.getUserLocale(), CooldownScope.CHANNEL.getErrorPath()));
		else if (!cooldownScope.equals(CooldownScope.USER))
			front.append(" ").append(lu.getLocalized(event.getUserLocale(), cooldownScope.getErrorPath()));

		return MessageCreateData.fromContent(Objects.requireNonNull(front.append("!").toString()));
	}

}
