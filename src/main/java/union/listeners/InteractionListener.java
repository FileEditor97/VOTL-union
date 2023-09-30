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

import jakarta.annotation.Nonnull;

import union.App;
import union.objects.CmdAccessLevel;
import union.objects.Emotes;
import union.objects.constants.Constants;
import union.objects.constants.Links;
import union.utils.database.DBUtil;
import union.utils.message.LocaleUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
public class InteractionListener extends ListenerAdapter {

	private final App bot;
	private final LocaleUtil lu;
	private final DBUtil db;
	private final EventWaiter waiter;

	public InteractionListener(App bot, EventWaiter waiter) {
		// TODO: add timeout - 5 sec
		this.bot = bot;
		this.lu = bot.getLocaleUtil();
		this.db = bot.getDBUtil();
		this.waiter = waiter;
	}

	public void replyError(IReplyCallback event, String... text) {
		if (text.length > 1) {
			event.replyEmbeds(bot.getEmbedUtil().getError(event, text[0], text[1])).setEphemeral(true).queue();
		} else {
			event.replyEmbeds(bot.getEmbedUtil().getError(event, text[0])).setEphemeral(true).queue();
		}
	}

	public void replySuccess(ButtonInteractionEvent event, String path) {
		event.replyEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path)).build()).setEphemeral(true).queue();
	}

	public void timedOut(ComponentInteraction event) {
		event.editMessageEmbeds(bot.getEmbedUtil().getError(event, "errors.timed_out")).setComponents().queue();
	}
	
	
	@Override
	public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
		String buttonId = event.getComponentId();

		if (buttonId.startsWith("verify")) {
			buttonVerify(event);
		} else if (buttonId.startsWith("role")) {
			String action = buttonId.split(":")[1];
			switch (action) {
				case "start_request":
					buttonRoleShowSelection(event);
					break;
				case "other":
					buttonRoleSelectionOther(event);
					break;
				case "clear":
					buttonRoleSelectionClear(event);
					break;
				case "remove":
					buttonRoleRemove(event);
					break;
				case "toggle":
					buttonRoleToggle(event);
					break;
				default:
					break;
			}
		} else if (buttonId.startsWith("ticket")) {
			String action = buttonId.split(":")[1];
			switch (action) {
				case "role_create":
					buttonRoleTicketCreate(event);
					break;
				case "role_approve":
					buttonRoleTicketApprove(event);
					break;
				case "close":
					buttonTicketClose(event);
					break;
				case "cancel":
					buttonTicketCloseCancel(event);
					break;
				case "claim":
					buttonTicketClaim(event);
					break;
				case "unclaim":
					buttonTicketUnclaim(event);
					break;
				default:
					break;
			}
		} else if (buttonId.startsWith("tag")) {
			buttonTagCreateTicket(event);
		} else if (buttonId.startsWith("delete")) {
			buttonReportDelete(event);
		} else if (buttonId.startsWith("voice")) {
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
			String action = buttonId.split(":")[1];
			switch (action) {
				case "lock":
					buttonVoiceLock(event, vc);
					break;
				case "unlock":
					buttonVoiceUnlock(event, vc);
					break;
				case "ghost":
					buttonVoiceGhost(event, vc);
					break;
				case "unghost":
					buttonVoiceUnghost(event, vc);
					break;
				case "name":
					buttonVoiceName(event);
					break;
				case "limit":
					buttonVoiceLimit(event);
					break;
				case "permit":
					buttonVoicePermit(event);
					break;
				case "reject":
					buttonVoiceReject(event);
					break;
				case "perms":
					buttonVoicePerms(event, vc);
					break;
				case "delete":
					buttonVoiceDelete(event, vc);
					break;
				default:
					break;
			}
		}
	}

	// Verify
	private void buttonVerify(ButtonInteractionEvent event) {
		Member member = event.getMember();
		Guild guild = event.getGuild();

		String roleId = db.verify.getVerifyRole(guild.getId());
		if (roleId == null) {
			replyError(event, "bot.verification.failed_role", "The verification role is not configured");
			return;
		}
		Role role = guild.getRoleById(roleId);
		if (role == null) {
			replyError(event, "bot.verification.failed_role", "Verification role not found");
			return;
		}
		if (member.getRoles().contains(role)) {
			replyError(event, "bot.verification.you_verified");
			return;
		}

		String steam64 = db.verifyRequest.getSteam64(member.getId());
		if (steam64 != null) {
			// Give verify role to user
			guild.addRoleToMember(member, role).reason("Verification completed - "+steam64).queue(
				success -> {
					event.deferEdit().queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));
					bot.getLogListener().onVerified(member.getUser(), steam64, guild);
					if (!bot.getDBUtil().verifyCache.isVerified(member.getId())) {
						bot.getDBUtil().verifyCache.addUser(member.getId(), steam64);
					}
				},
				failure -> {
					replyError(event, "bot.verification.failed_role");
					bot.getLogger().warn("Was unable to add verify role to user in "+guild.getName()+"("+guild.getId()+")", failure);
				});
		} else {
			event.deferReply(true).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));
			String guildId = guild.getId();
			Button refresh = Button.of(ButtonStyle.DANGER, "verify-refresh", lu.getText(event, "bot.verification.listener.refresh"), Emoji.fromUnicode("🔁"));
			// Check if user pressed refresh button
			if (event.getButton().getId().endsWith("refresh")) {
				// Ask user to wait for 30 seconds each time
				event.getHook().editOriginalEmbeds(new EmbedBuilder().setColor(Constants.COLOR_FAILURE).setTitle(lu.getText(event, "bot.verification.listener.wait_title"))
					.setDescription(lu.getText(event, "bot.verification.listener.wait_value")).build()).queue();
				event.editButton(refresh.asDisabled()).queue(success -> event.editButton(refresh).queueAfter(30, TimeUnit.SECONDS));
				return;
			}
			// Reply with instruction on how to verify, buttons - link and refresh
			Button verify = Button.link(Links.UNIONTEAMS, lu.getText(event, "bot.verification.listener.connect"));
			EmbedBuilder builder = new EmbedBuilder().setColor(bot.getDBUtil().guild.getColor(guildId)).setTitle(lu.getText(event, "bot.verification.embed.title"))
				.setDescription(bot.getDBUtil().verify.getInstructionText(guildId))
				.addField(lu.getText(event, "bot.verification.embed.howto"), lu.getText(event, "bot.verification.embed.guide"), false);

			event.getHook().editOriginalEmbeds(builder.build()).setActionRow(verify, refresh).queue();
		}
	}

	// Role selection
	private void buttonRoleShowSelection(ButtonInteractionEvent event) {
		event.deferReply(true).queue();
		Guild guild = event.getGuild();
		String guildId = guild.getId();

		String channelId = db.ticket.getOpenedChannel(event.getMember().getId(), guildId, 0);
		if (channelId != null) {
			ThreadChannel channel = guild.getThreadChannelById(channelId);
			if (channel != null) {
				event.getHook().editOriginalEmbeds(new EmbedBuilder().setColor(Constants.COLOR_FAILURE)
					.setDescription(lu.getText(event, "bot.ticketing.listener.ticket_exists").replace("{channel}", channel.getAsMention()))
					.build()
				).queue();
				return;
			}
			db.ticket.closeTicket(Instant.now(), channelId, "BOT: Channel deleted (not found)");
		}

		List<ActionRow> actionRows = new ArrayList<ActionRow>();
		// String select menu IDs "menu:role_row:1/2/3"
		for (Integer row = 1; row <= 3; row++) {
			ActionRow actionRow = createRoleRow(guild, row);
			if (actionRow != null) {
				actionRows.add(actionRow);
			}
		}
		actionRows.add(ActionRow.of(Button.secondary("role:other", lu.getText(event, "bot.ticketing.listener.request_other"))));
		actionRows.add(ActionRow.of(Button.danger("role:clear", lu.getText(event, "bot.ticketing.listener.request_clear")),
			Button.success("ticket:role_create", lu.getText(event, "bot.ticketing.listener.request_continue"))));

		MessageEmbed embed = new EmbedBuilder()
			.setColor(Constants.COLOR_DEFAULT)
			.setDescription(lu.getText(event, "bot.ticketing.listener.request_title"))
			.build();

		event.getHook().editOriginalEmbeds(embed).setComponents(actionRows).queue();
	}

	private ActionRow createRoleRow(final Guild guild, Integer row) {
		List<Map<String, Object>> assignRoles = bot.getDBUtil().role.getAssignableByRow(guild.getId(), row);
		if (assignRoles.isEmpty()) return null;
		List<SelectOption> options = new ArrayList<SelectOption>();
		assignRoles.forEach(data -> {
			if (options.size() >= 25) return;
			String roleId = data.get("roleId").toString();
			Role role = guild.getRoleById(roleId);
			if (role == null) return;
			String description = Objects.requireNonNullElse(data.get("description").toString(), "");
			options.add(SelectOption.of(role.getName(), roleId).withDescription(description));
		});
		StringSelectMenu menu = StringSelectMenu.create("menu:role_row:"+row)
			.setPlaceholder(db.ticketSettings.getRowText(guild.getId(), row))
			.setMaxValues(25)
			.addOptions(options)
			.build();
		return ActionRow.of(menu);
	}

	private void buttonRoleSelectionOther(ButtonInteractionEvent event) {
		event.deferEdit().queue();

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
		event.editMessageEmbeds(embed).queue();
	}

	private void buttonRoleRemove(ButtonInteractionEvent event) {
		event.deferReply(true).queue();
		Guild guild = event.getGuild();
		List<Role> currentRoles = event.getMember().getRoles();

		List<Role> allRoles = new ArrayList<Role>();
		db.role.getAssignable(guild.getId()).forEach(data -> allRoles.add(guild.getRoleById(data.get("roleId").toString())));
		db.role.getCustom(guild.getId()).forEach(data -> allRoles.add(guild.getRoleById(data.get("roleId").toString())));
		List<Role> roles = allRoles.stream().filter(role -> currentRoles.contains(role)).collect(Collectors.toList());
		if (roles.isEmpty()) {
			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.no_assigned")).queue();
			return;
		}

		List<SelectOption> options = roles.stream().map(role -> SelectOption.of(role.getName(), role.getId())).toList();	
		StringSelectMenu menu = StringSelectMenu.create("menu:role_remove")
			.setPlaceholder(lu.getLocalized(event.getUserLocale(), "bot.ticketing.listener.request_template"))
			.setMaxValues(options.size())
			.addOptions(options)
			.build();
		event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, "bot.ticketing.listener.remove_title")).build()).setActionRow(menu).queue(msg ->
			waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> e.getComponentId().equals("menu:role_remove"),
				actionEvent -> {
					List<Role> remove = actionEvent.getSelectedOptions().stream().map(option -> guild.getRoleById(option.getValue())).toList();
					guild.modifyMemberRoles(event.getMember(), null, remove).reason("User request").queue(done -> {
						event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(event)
							.setDescription(lu.getText(event, "bot.ticketing.listener.remove_done").replace("{roles}", remove.stream().map(role -> role.getAsMention()).collect(Collectors.joining(", "))))
							.setColor(Constants.COLOR_SUCCESS)
							.build()
						).setComponents().queue();
					}, failure -> {
						event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.remove_failed", failure.getMessage())).setComponents().queue();
					});
				},
				40,
				TimeUnit.SECONDS,
				() -> event.getHook().editOriginalComponents(ActionRow.of(
					menu.createCopy().setPlaceholder(lu.getText(event, "errors.timed_out")).setDisabled(true).build())
				).queue()
			)
		);
	}

	private void buttonRoleToggle(ButtonInteractionEvent event) {
		String roleId = event.getButton().getId().split(":")[2];
		Role role = event.getGuild().getRoleById(roleId);
		if (role == null || !db.role.isToggleable(roleId)) {
			replyError(event, "bot.ticketing.listener.toggle_failed", "Role not found or can't be toggled");
			return;
		}
		if (event.getMember().getRoles().contains(role)) {
			event.getGuild().removeRoleFromMember(event.getMember(), role).queue(done -> {
				event.replyEmbeds(bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getText(event, "bot.ticketing.listener.toggle_removed").replace("{role}", role.getAsMention()))
					.setColor(Constants.COLOR_SUCCESS)
					.build()
				).setEphemeral(true).queue();
			}, failure -> {
				replyError(event, "bot.ticketing.listener.toggle_failed", failure.getMessage());
			});
		} else {
			event.getGuild().addRoleToMember(event.getMember(), role).queue(done -> {
				event.replyEmbeds(bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getText(event, "bot.ticketing.listener.toggle_added").replace("{role}", role.getAsMention()))
					.setColor(Constants.COLOR_SUCCESS)
					.build()
				).setEphemeral(true).queue();
			}, failure -> {
				replyError(event, "bot.ticketing.listener.toggle_failed", failure.getMessage());
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
			replyError(event, "bot.ticketing.listener.request_none");
			return;
		}
		// Check if bot is able to give selected roles
		boolean otherRole = roleIds.contains("0");
		List<Role> memberRoles = event.getMember().getRoles();
		List<Role> add = roleIds.stream().filter(option -> !option.equals("0")).map(option -> guild.getRoleById(option))
			.filter(role -> role != null && !memberRoles.contains(role)).toList();
		if (!otherRole && add.isEmpty()) {
			replyError(event, "bot.ticketing.listener.request_empty");
			return;
		}

		event.deferEdit().queue();

		Integer ticketId = 1 + db.ticket.lastIdByTag(guildId, 0);
		event.getChannel().asTextChannel().createThreadChannel(lu.getLocalized(event.getGuildLocale(), "ticket.role")+"-"+ticketId.toString(), true).queue(
			channel -> {
				db.ticket.addRoleTicket(ticketId, event.getMember().getId(), guildId, channel.getId(), String.join(";", roleIds));
				
				StringBuffer mentions = new StringBuffer(event.getMember().getAsMention());
				db.access.getRoles(guildId, CmdAccessLevel.MOD).forEach(roleId -> mentions.append(" <@&"+roleId+">"));
				channel.sendMessage(mentions.toString()).queue(msg -> msg.delete().queueAfter(5, TimeUnit.SECONDS, null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_CHANNEL)));
				
				String steam64 = db.verifyCache.getSteam64(event.getMember().getId());
				String rolesString = String.join(" ", add.stream().map(role -> role.getAsMention()).collect(Collectors.joining(" ")), (otherRole ? lu.getLocalized(event.getGuildLocale(), "bot.ticketing.embeds.other") : ""));
				String proofString = add.stream().map(role -> db.role.getDescription(role.getId())).filter(str -> str != null).distinct().collect(Collectors.joining("\n- ", "- ", ""));
				MessageEmbed embed = new EmbedBuilder().setColor(db.guild.getColor(guildId))
					.setDescription(String.format("SteamID\n> %s\n%s\n> %s\n\n%s, %s\n%s\n\n%s",
						(steam64 == null ? "None" : bot.getSteamUtil().convertSteam64toSteamID(steam64) + "\n> [UnionTeams](https://unionteams.ru/player/"+steam64+")"),
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

				event.getHook().editOriginalEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, "bot.ticketing.listener.created").replace("{channel}", channel.getAsMention()))
					.build()
				).setComponents().queue();
			}, failure -> {
				event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.cant_create")).setComponents().queue();
			}
		);
	}

	private void buttonRoleTicketApprove(ButtonInteractionEvent event) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.MOD)) {
			// User has no Mod access (OR admin, server owner, dev) to approve role request
			replyError(event, "errors.interaction.no_access");
			return;
		}
		String channelId = event.getChannel().getId();
		if (!db.ticket.isOpened(channelId)) {
			replyError(event, "bot.ticketing.listener.is_closed");
			return;
		}
		Guild guild = event.getGuild();
		String userId = db.ticket.getUserId(channelId);
		guild.retrieveMemberById(userId).queue(member -> {
			List<Role> roles = db.ticket.getRoleIds(channelId).stream().map(id -> guild.getRoleById(id)).filter(role -> role != null).toList();
			String ticketId = db.ticket.getTicketId(channelId);
			if (roles.isEmpty()) {
				event.replyEmbeds(bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getText(event, "bot.ticketing.listener.role_none"))
					.setColor(Constants.COLOR_WARNING)
					.build()
				).setEphemeral(true).queue();
			} else {
				guild.modifyMemberRoles(member, roles, null).reason("Request role-"+ticketId+" approved by "+event.getMember().getEffectiveName()).queue(done -> {
					bot.getLogListener().onRolesApproved(member, event.getMember(), guild, roles, ticketId);
					db.ticket.setClaimed(channelId, event.getMember().getId());
					event.replyEmbeds(bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.role_added"))
						.setColor(Constants.COLOR_SUCCESS)
						.build()
					).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));
					member.getUser().openPrivateChannel().queue(
						dm -> dm.sendMessage(bot.getLocaleUtil().getLocalized(guild.getLocale(), "bot.ticketing.listener.role_dm")
							.replace("{roles}", roles.stream().map(role -> role.getName()).collect(Collectors.joining(" | ")))
							.replace("{server}", guild.getName())
							.replace("{id}", ticketId)
							.replace("{mod}", event.getMember().getEffectiveName()))
							.queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER))
					);
				}, failure -> {
					replyError(event, "bot.ticketing.listener.role_failed", failure.getMessage());
				});
			}
		}, failure -> {
			replyError(event, "bot.ticketing.listener.no_member", failure.getMessage());
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
		event.getMessage().replyEmbeds(bot.getEmbedUtil().getEmbed(event).setDescription(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.delete_countdown")).build()).queue(msg -> {
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
			.setColor(db.guild.getColor(guild.getId()))
			.setDescription(bot.getLocaleUtil().getLocalized(guild.getLocale(), "ticket.autoclose_cancel"))
			.build();
		event.editMessageEmbeds(embed).setComponents().queue();
	}
	
	// Ticket management
	private void buttonTicketClaim(ButtonInteractionEvent event) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.HELPER)) {
			// User has no Helper access (OR admin, server owner, dev) to approve role request
			replyError(event, "errors.interaction.no_access");
			return;
		}
		String channelId = event.getChannel().getId();
		if (!db.ticket.isOpened(channelId)) {
			replyError(event, "bot.ticketing.listener.is_closed");
			return;
		}

		db.ticket.setClaimed(channelId, event.getUser().getId());
		event.replyEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
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
			// User has no Helper access (OR admin, server owner, dev) to approve role request
			replyError(event, "errors.interaction.no_access");
			return;
		}
		String channelId = event.getChannel().getId();
		if (!db.ticket.isOpened(channelId)) {
			replyError(event, "bot.ticketing.listener.is_closed");
			return;
		}

		db.ticket.setUnclaimed(channelId);
		event.replyEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
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
				event.replyEmbeds(new EmbedBuilder().setColor(Constants.COLOR_FAILURE)
					.setDescription(lu.getText(event, "bot.ticketing.listener.ticket_exists").replace("{channel}", channel.getAsMention()))
					.build()
				).setEphemeral(true).queue();
				return;
			}
			db.ticket.closeTicket(Instant.now(), channelId, "BOT: Channel deleted (not found)");
		}

		event.deferReply(true).queue();

		Map<String, Object> tagInfo = db.tags.getTagInfo(tagId);
		Integer type = (Integer) tagInfo.get("tagType");
		if (type == null || type.equals(0)) {
			replyTicketError(event, "Unknown tag with ID: "+tagId);
			return;
		}

		User user = event.getUser();

		StringBuffer mentions = new StringBuffer(user.getAsMention());
		List<String> supportRoles = Optional.ofNullable((String) tagInfo.get("supportRoles")).map(text -> Arrays.asList(text.split(";"))).orElse(Collections.emptyList());
		supportRoles.forEach(roleId -> mentions.append(" <@&%s>".formatted(roleId)));

		String message = Optional.ofNullable((String) tagInfo.get("message"))
			.map(text -> setNewline(text).replace("{username}", user.getName()).replace("{tag_username}", user.getAsMention()))
			.orElse("Ticket's controls");
		
		Integer ticketId = 1 + db.ticket.lastIdByTag(guildId, tagId);
		String ticketName = (((String) tagInfo.get("ticketName"))+ticketId).replace("{username}", user.getName());
		if (type.equals(1)) {
			// Thread ticket
			event.getChannel().asTextChannel().createThreadChannel(ticketName, true).queue(channel -> {
				db.ticket.addTicket(ticketId, user.getId(), guildId, channel.getId(), tagId);
				
				bot.getTicketUtil().createTicket(event, channel, mentions.toString(), message);
			},
			failure -> {
				replyTicketError(event, "Unable to create new thread in this channel");
			});
		} else {
			// Channel ticket
			String categoryId = (String) tagInfo.get("location");
			Category category = Optional.ofNullable(categoryId).map(id -> event.getGuild().getCategoryById(id)).orElse(event.getChannel().asTextChannel().getParentCategory());
			if (category == null) {
				replyTicketError(event, "Target category not found, with ID: "+categoryId);
				return;
			}

			ChannelAction<TextChannel> action = category.createTextChannel(ticketName);
			for (String roleId : supportRoles) action = action.addRolePermissionOverride(Long.valueOf(roleId), EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), null);
			action.addPermissionOverride(event.getGuild().getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
				.addMemberPermissionOverride(user.getIdLong(), EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), null)
				.queue(channel -> {
				db.ticket.addTicket(ticketId, user.getId(), guildId, channel.getId(), tagId);

				bot.getTicketUtil().createTicket(event, channel, mentions.toString(), message);
			}, 
			failure -> {
				replyTicketError(event, "Unable to create new channel in target category, with ID: "+categoryId);
			});
		}
	}

	private String setNewline(String text) {
		return text.replaceAll("<br>", "\n");
	}

	private void replyTicketError(ButtonInteractionEvent event, String reason) {
		event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.cant_create", reason)).queue();
	}

	// Report
	private void buttonReportDelete(ButtonInteractionEvent event) {
		event.editComponents().queue();

		String channelId = event.getComponentId().split(":")[1];
		String messageId = event.getComponentId().split(":")[2];
		
		MessageChannel channel = event.getGuild().getChannelById(MessageChannel.class, channelId);
		if (channel == null) {
			event.getMessage().reply(Constants.FAILURE).queue();
			return;
		}
		channel.deleteMessageById(messageId).reason("Deleted by %s".formatted(event.getMember().getEffectiveName())).queue(success ->
			event.getMessage().replyEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getLocalized(event.getGuildLocale(), "menus.report.deleted").replace("{name}", event.getMember().getAsMention()))
				.build()
			).queue(),
		failure -> event.getMessage().replyEmbeds(bot.getEmbedUtil().getError(event, "misc.unknown", failure.getMessage())).queue()
		);
	}

	// Voice
	private void buttonVoiceLock(ButtonInteractionEvent event, VoiceChannel vc) {
		try {
			vc.upsertPermissionOverride(event.getGuild().getPublicRole()).deny(Permission.VOICE_CONNECT).queue();
		} catch (InsufficientPermissionException ex) {
			event.reply(bot.getEmbedUtil().createPermError(event, ex.getPermission(), true)).setEphemeral(true).queue();
			return;
		}
		replySuccess(event, "bot.voice.listener.panel.lock");
	}

	private void buttonVoiceUnlock(ButtonInteractionEvent event, VoiceChannel vc) {
		try {
			vc.upsertPermissionOverride(event.getGuild().getPublicRole()).clear(Permission.VOICE_CONNECT).queue();
		} catch (InsufficientPermissionException ex) {
			event.reply(bot.getEmbedUtil().createPermError(event, ex.getPermission(), true)).setEphemeral(true).queue();
			return;
		}
		replySuccess(event, "bot.voice.listener.panel.unlock");
	}

	private void buttonVoiceGhost(ButtonInteractionEvent event, VoiceChannel vc) {
		try {
			vc.upsertPermissionOverride(event.getGuild().getPublicRole()).deny(Permission.VIEW_CHANNEL).queue();
		} catch (InsufficientPermissionException ex) {
			event.reply(bot.getEmbedUtil().createPermError(event, ex.getPermission(), true)).setEphemeral(true).queue();
			return;
		}
		replySuccess(event, "bot.voice.listener.panel.ghost");
	}

	private void buttonVoiceUnghost(ButtonInteractionEvent event, VoiceChannel vc) {
		try {
			vc.upsertPermissionOverride(event.getGuild().getPublicRole()).clear(Permission.VIEW_CHANNEL).queue();
		} catch (InsufficientPermissionException ex) {
			event.reply(bot.getEmbedUtil().createPermError(event, ex.getPermission(), true)).setEphemeral(true).queue();
			return;
		}
		replySuccess(event, "bot.voice.listener.panel.unghost");
	}

	private void buttonVoiceName(ButtonInteractionEvent event) {
		TextInput textInput = TextInput.create("name", lu.getText(event, "bot.voice.listener.panel.name_label"), TextInputStyle.SHORT)
			.setPlaceholder("{user}'s channel")
			.setMaxLength(100)
			.build();
		event.replyModal(Modal.create("voice:name", lu.getText(event, "bot.voice.listener.panel.modal")).addActionRow(textInput).build()).queue();
	}

	private void buttonVoiceLimit(ButtonInteractionEvent event) {
		TextInput textInput = TextInput.create("limit", lu.getText(event, "bot.voice.listener.panel.limit_label"), TextInputStyle.SHORT)
			.setPlaceholder("0 / 99")
			.setRequiredRange(1, 2)
			.build();
		event.replyModal(Modal.create("voice:limit", lu.getText(event, "bot.voice.listener.panel.modal")).addActionRow(textInput).build()).queue();
	}

	private void buttonVoicePermit(ButtonInteractionEvent event) {
		String text = lu.getText(event, "bot.voice.listener.panel.permit_label");
		event.reply(text).addActionRow(EntitySelectMenu.create("voice:permit", SelectTarget.USER, SelectTarget.ROLE).setMaxValues(10).build()).setEphemeral(true).queue();
	}

	private void buttonVoiceReject(ButtonInteractionEvent event) {
		String text = lu.getText(event, "bot.voice.listener.panel.reject_label");
		event.reply(text).addActionRow(EntitySelectMenu.create("voice:reject", SelectTarget.USER, SelectTarget.ROLE).setMaxValues(10).build()).setEphemeral(true).queue();
	}

	private void buttonVoicePerms(ButtonInteractionEvent event, VoiceChannel vc) {
		event.deferReply(true).queue();
		Guild guild = event.getGuild();
		EmbedBuilder embedBuilder = bot.getEmbedUtil().getEmbed(event)
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

				event.getHook().sendMessageEmbeds(embedBuilder2.build()).queue();
			}
		);
	}

	private void buttonVoiceDelete(ButtonInteractionEvent event, VoiceChannel vc) {
		bot.getDBUtil().voice.remove(vc.getId());
		vc.delete().reason("Channel owner request").queue();
		replySuccess(event, "bot.voice.listener.panel.delete");
	}

	@Override
	public void onModalInteraction(@Nonnull ModalInteractionEvent event) {
		String modalId = event.getModalId();

		if (modalId.equals("vfpanel")) {
			if (event.getValues().size() == 0) {
				replyError(event, "errors.interaction.no_values");
				return;
			}
			String guildId = event.getGuild().getId();

			String main = event.getValue("main").getAsString();
			db.verify.setMainText(guildId, main.isBlank() ? "NULL" : main);
			String description = event.getValue("description").getAsString();
			db.verify.setInstructionText(guildId, description.isBlank() ? "NULL" : description);

			event.replyEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, "bot.verification.vfpanel.text.done"))
				.build()
			).setEphemeral(true).queue();
		} else if (modalId.startsWith("voice")) {
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
		}
	}

	@Override
	public void onStringSelectInteraction(@Nonnull StringSelectInteractionEvent event) {
		String menuId = event.getComponentId();

		if (menuId.startsWith("menu:role_row")) {
			event.deferEdit().queue();

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
	public void onEntitySelectInteraction(@Nonnull EntitySelectInteractionEvent event) {
		String menuId = event.getComponentId();
		if (menuId.startsWith("voice")) {
			//event.deferReply(true).queue();

			Member author = event.getMember();
			if (!author.getVoiceState().inAudioChannel()) {
				replyError(event, "bot.voice.listener.not_in_voice");
				return;
			}
			String channelId = db.voice.getChannel(author.getId());
			if (channelId == null) {
				replyError(event, "errors.no_channel");
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
				if (members.isEmpty() & roles.isEmpty()) {
					event.deferEdit().queue();
					return;
				}
				if (members.contains(author) || members.contains(guild.getSelfMember())) {
					event.replyEmbeds(bot.getEmbedUtil().getError(event, "bot.voice.listener.panel.not_self"));
					return;
				}

				List<String> mentionStrings = new ArrayList<>();
				String text = "";
				
				if (action.equals("permit")) {
					for (Member member : members) {
						try {
							vc.getManager().putPermissionOverride(member, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null).queue();
							mentionStrings.add(member.getEffectiveName());
						} catch (InsufficientPermissionException ex) {
							event.replyEmbeds(bot.getEmbedUtil().getError(event, "errors.missing_perms.self"));
							return;
						}
					}
			
					for (Role role : roles) {
						if (!role.hasPermission(new Permission[]{Permission.ADMINISTRATOR, Permission.MANAGE_SERVER, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_ROLES}))
							try {
								vc.getManager().putPermissionOverride(role, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null).queue();
								mentionStrings.add(role.getName());
							} catch (InsufficientPermissionException ex) {
								event.replyEmbeds(bot.getEmbedUtil().getError(event, "errors.missing_perms.self"));
								return;
							}
					}
	
					text = lu.getUserText(event, "bot.voice.listener.panel.permit_done", mentionStrings);
				} else {
					for (Member member : members) {
						try {
							vc.getManager().putPermissionOverride(member, null, EnumSet.of(Permission.VOICE_CONNECT)).queue();
							mentionStrings.add(member.getEffectiveName());
						} catch (InsufficientPermissionException ex) {
							event.replyEmbeds(bot.getEmbedUtil().getError(event, "errors.missing_perms.self"));
							return;
						}
						if (vc.getMembers().contains(member)) {
							guild.kickVoiceMember(member).queue();
						}
					}
			
					for (Role role : roles) {
						if (!role.hasPermission(new Permission[]{Permission.ADMINISTRATOR, Permission.MANAGE_SERVER, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_ROLES}))
							try {
								vc.getManager().putPermissionOverride(role, null, EnumSet.of(Permission.VOICE_CONNECT)).queue();
								mentionStrings.add(role.getName());
							} catch (InsufficientPermissionException ex) {
								event.replyEmbeds(bot.getEmbedUtil().getError(event, "errors.missing_perms.self"));
								return;
							}
					}

					text = lu.getUserText(event, "bot.voice.listener.panel.reject_done", mentionStrings);
				}

				event.editMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
						.setDescription(text)
						.build()
					).setContent("").setComponents().queue();
				
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

}
