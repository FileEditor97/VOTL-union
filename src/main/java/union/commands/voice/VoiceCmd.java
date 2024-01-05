package union.commands.voice;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.Nonnull;

import union.App;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdModule;
import union.objects.Emotes;
import union.objects.constants.CmdCategory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.managers.channel.concrete.VoiceChannelManager;

public class VoiceCmd extends CommandBase {
	
	public VoiceCmd(App bot) {
		super(bot);
		this.name = "voice";
		this.path = "bot.voice.voice";
		this.children = new SlashCommand[]{new Lock(bot), new Unlock(bot), new Ghost(bot), new Unghost(bot),
			new NameSet(bot), new NameReset(bot), new LimitSet(bot), new LimitReset(bot),
			new Claim(bot), new Permit(bot), new Reject(bot), new PermsView(bot), new PermsReset(bot)
		};
		this.category = CmdCategory.VOICE;
		this.module = CmdModule.VOICE;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Lock extends SlashCommand {

		public Lock(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "lock";
			this.path = "bot.voice.voice.lock";
			this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VOICE_CONNECT};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String channelId = bot.getDBUtil().voice.getChannel(event.getMember().getId());
			if (channelId == null) {
				createError(event, "errors.no_channel");
				return;
			}

			// Verify role
			String verifyRoleId = bot.getDBUtil().verify.getVerifyRole(event.getGuild().getId());

			VoiceChannel vc = event.getGuild().getVoiceChannelById(channelId);
			try {
				//vc.upsertPermissionOverride(event.getGuild().getPublicRole()).deny(Permission.VOICE_CONNECT).queue();
				if (verifyRoleId != null) {
					Role verifyRole = event.getGuild().getRoleById(verifyRoleId);
					if (verifyRole != null) {
						vc.upsertPermissionOverride(verifyRole).deny(Permission.VOICE_CONNECT).queue();
					}
				}
			} catch (InsufficientPermissionException ex) {
				createPermError(event, ex.getPermission(), true);
				return;
			}

			createReplyEmbed(event,
				bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getText(event, path+".done"))
					.build()
			);
		}
	}

	private class Unlock extends SlashCommand {

		public Unlock(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "unlock";
			this.path = "bot.voice.voice.unlock";
			this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VOICE_CONNECT};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String channelId = bot.getDBUtil().voice.getChannel(event.getMember().getId());
			if (channelId == null) {
				createError(event, "errors.no_channel");
				return;
			}

			// Verify role
			String verifyRoleId = bot.getDBUtil().verify.getVerifyRole(event.getGuild().getId());

			VoiceChannel vc = event.getGuild().getVoiceChannelById(channelId);
			try {
				//vc.upsertPermissionOverride(event.getGuild().getPublicRole()).clear(Permission.VOICE_CONNECT).queue();
				if (verifyRoleId != null) {
					Role verifyRole = event.getGuild().getRoleById(verifyRoleId);
					if (verifyRole != null) {
						vc.upsertPermissionOverride(verifyRole).setAllowed(Permission.VOICE_CONNECT).queue();
					}
				}
			} catch (InsufficientPermissionException ex) {
				createPermError(event, ex.getPermission(), true);
				return;
			}

			createReplyEmbed(event,
				bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getText(event, path+".done"))
					.build()
			);
		}
	}

	private class Ghost extends SlashCommand {

		public Ghost(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "ghost";
			this.path = "bot.voice.voice.ghost";
			this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String channelId = bot.getDBUtil().voice.getChannel(event.getMember().getId());
			if (channelId == null) {
				createError(event, "errors.no_channel");
				return;
			}

			// Verify role
			String verifyRoleId = bot.getDBUtil().verify.getVerifyRole(event.getGuild().getId());

			VoiceChannel vc = event.getGuild().getVoiceChannelById(channelId);
			try {
				//vc.upsertPermissionOverride(event.getGuild().getPublicRole()).deny(Permission.VIEW_CHANNEL).queue();
				if (verifyRoleId != null) {
					Role verifyRole = event.getGuild().getRoleById(verifyRoleId);
					if (verifyRole != null) {
						vc.upsertPermissionOverride(verifyRole).deny(Permission.VIEW_CHANNEL).queue();
					}
				}
			} catch (InsufficientPermissionException ex) {
				createPermError(event, ex.getPermission(), true);
				return;
			}

			createReplyEmbed(event,
				bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getText(event, path+".done"))
					.build()
			);
		}
	}

	private class Unghost extends SlashCommand {

		public Unghost(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "unghost";
			this.path = "bot.voice.voice.unghost";
			this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String channelId = bot.getDBUtil().voice.getChannel(event.getMember().getId());
			if (channelId == null) {
				createError(event, "errors.no_channel");
				return;
			}

			// Verify role
			String verifyRoleId = bot.getDBUtil().verify.getVerifyRole(event.getGuild().getId());

			VoiceChannel vc = event.getGuild().getVoiceChannelById(channelId);
			try {
				//vc.upsertPermissionOverride(event.getGuild().getPublicRole()).clear(Permission.VIEW_CHANNEL).queue();
				if (verifyRoleId != null) {
					Role verifyRole = event.getGuild().getRoleById(verifyRoleId);
					if (verifyRole != null) {
						vc.upsertPermissionOverride(verifyRole).setAllowed(Permission.VIEW_CHANNEL).queue();
					}
				}
			} catch (InsufficientPermissionException ex) {
				createPermError(event, ex.getPermission(), true);
				return;
			}

			createReplyEmbed(event,
				bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getText(event, path+".done"))
					.build()
			);
		}
	}

	private class NameSet extends SlashCommand {

		public NameSet(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "set";
			this.path = "bot.voice.voice.name.set";
			this.options = List.of(
				new OptionData(OptionType.STRING, "name", lu.getText(path+".name.help"), true)
					.setMaxLength(100)
			);
			this.subcommandGroup = new SubcommandGroupData("name", lu.getText("bot.voice.voice.name.help"));
			this.botPermissions = new Permission[]{Permission.MANAGE_CHANNEL};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String name = event.optString("name");
			sendNameReply(event, name);
		}
	}

	private class NameReset extends SlashCommand {

		public NameReset(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "reset";
			this.path = "bot.voice.voice.name.reset";
			this.subcommandGroup = new SubcommandGroupData("name", lu.getText("bot.voice.voice.name.help"));
			this.botPermissions = new Permission[]{Permission.MANAGE_CHANNEL};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String name = Optional.ofNullable(
				bot.getDBUtil().guildVoice.getName(event.getGuild().getId())	
			).orElse(
				lu.getLocalized(event.getGuildLocale(), "bot.voice.listener.default_name")
			);
			sendNameReply(event, name);
		}
	}

	private void sendNameReply(SlashCommandEvent event, String name) {
		String userId = event.getMember().getId();
		String channelId = bot.getDBUtil().voice.getChannel(userId);
		if (channelId == null) {
			createError(event, "errors.no_channel");
			return;
		}

		name = name.replace("{user}", event.getMember().getEffectiveName());
		event.getGuild().getVoiceChannelById(channelId).getManager().setName(name.substring(0, Math.min(100, name.length()))).queue();

		bot.getDBUtil().user.setName(userId, name);

		createReplyEmbed(event, 
			bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, "bot.voice.name.done").replace("{value}", name))
				.build()
		);
	}

	private class LimitSet extends SlashCommand {

		public LimitSet(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "set";
			this.path = "bot.voice.voice.limit.set";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "limit", lu.getText(path+".limit.help"), true)
					.setRequiredRange(0, 99)
			);
			this.subcommandGroup = new SubcommandGroupData("limit", lu.getText("bot.voice.voice.limit.help"));
			this.botPermissions = new Permission[]{Permission.MANAGE_CHANNEL};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer limit = event.optInteger("limit");
			sendLimitReply(event, limit);
		}
	}

	private class LimitReset extends SlashCommand {

		public LimitReset(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "reset";
			this.path = "bot.voice.voice.limit.reset";
			this.subcommandGroup = new SubcommandGroupData("limit", lu.getText("bot.voice.voice.limit.help"));
			this.botPermissions = new Permission[]{Permission.MANAGE_CHANNEL};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer limit = Optional.ofNullable(bot.getDBUtil().guildVoice.getLimit(event.getGuild().getId())).orElse(0);
			sendLimitReply(event, limit);			
		}
	}

	private void sendLimitReply(SlashCommandEvent event, Integer limit) {
		String userId = event.getMember().getId();
		String channelId = bot.getDBUtil().voice.getChannel(userId);
		if (channelId == null) {
			createError(event, "errors.no_channel");
			return;
		}

		event.getGuild().getVoiceChannelById(channelId).getManager().setUserLimit(limit).queue();

		bot.getDBUtil().user.setLimit(userId, limit);

		createReplyEmbed(event, 
			bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, "bot.voice.limit.done").replace("{value}", limit.toString()))
				.build()
		);
	}

	private class Claim extends SlashCommand {

		public Claim(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "claim";
			this.path = "bot.voice.voice.claim";
			this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS};
		}

		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Member author = event.getMember();

			if (!author.getVoiceState().inAudioChannel()) {
				editError(event, "bot.voice.listener.not_in_voice");
				return;
			}
			if (bot.getDBUtil().voice.existsUser(author.getId())) {
				editError(event, path+".has_channel");
				return;
			}

			VoiceChannel vc = author.getVoiceState().getChannel().asVoiceChannel();
			String ownerId = bot.getDBUtil().voice.getUser(vc.getId());
			if (ownerId == null) {
				editError(event, path+".not_custom");
				return;
			}
			
			event.getGuild().retrieveMemberById(ownerId).queue(
				owner -> {
					for (Member vcMember : vc.getMembers()) {
						if (vcMember == owner) {
							editHook(event, lu.getText(event, path+".has_owner"));
							return;
						}
					}

					try {
						vc.getManager().removePermissionOverride(owner).queue();
						vc.getManager().putPermissionOverride(author, EnumSet.of(Permission.MANAGE_CHANNEL), null).queue();
					} catch (InsufficientPermissionException ex) {
						editPermError(event, ex.getPermission(), true);
						return;
					}
					bot.getDBUtil().voice.setUser(vc.getId(), author.getId());
					
					editHookEmbed(event, 
						bot.getEmbedUtil().getEmbed(event)
							.setDescription(lu.getText(event, path+".done").replace("{channel}", vc.getAsMention()))
							.build()
					);
				}, failure -> {
					editError(event, "errors.unknown", failure.getMessage());
				}
			);
		}
	}

	private class Permit extends SlashCommand {

		private final List<Permission> AdminPerms = List.of(Permission.ADMINISTRATOR, Permission.MANAGE_SERVER, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_ROLES);

		public Permit(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "permit";
			this.path = "bot.voice.voice.permit";
			this.options = List.of(
				new OptionData(OptionType.STRING, "mentions", lu.getText(path+".mentions.help"), true)
			);
			this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Member author = event.getMember();
			String authorId = author.getId();

			String channelId = bot.getDBUtil().voice.getChannel(authorId);
			if (channelId == null) {
				editError(event, "errors.no_channel");
				return;
			}

			Mentions mentions = event.optMentions("mentions");
			if (mentions == null) {
				editError(event, path+".invalid_args");
				return;
			}

			List<Member> members = mentions.getMembers();
			List<Role> roles = mentions.getRoles();
			if (members.isEmpty() & roles.isEmpty()) {
				editError(event, path+".invalid_args");
				return;
			}
			if (members.contains(author) || members.contains(event.getGuild().getSelfMember())) {
				editError(event, path+".not_self");
				return;
			}

			List<String> mentionStrings = new ArrayList<>();
			VoiceChannel vc = event.getGuild().getVoiceChannelById(channelId);
			VoiceChannelManager vcManager = vc.getManager();

			for (Member member : members) {
				vcManager = vcManager.putPermissionOverride(member, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null);
				mentionStrings.add(member.getEffectiveName());
			}

			for (Role role : roles) {
				if (!role.hasPermission(AdminPerms)) {
					vcManager = vcManager.putPermissionOverride(role, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null);
					mentionStrings.add(role.getName());
				}
			}

			vcManager.queue(done -> {
				editHookEmbed(event, bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getUserText(event, path+".done", mentionStrings))
					.build()
				);
			}, failure -> {
				editPermError(event, Permission.MANAGE_PERMISSIONS, true);
			});
		}

	}

	private class Reject extends SlashCommand {

		private final List<Permission> AdminPerms = List.of(Permission.ADMINISTRATOR, Permission.MANAGE_SERVER, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_ROLES);

		public Reject(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "reject";
			this.path = "bot.voice.voice.reject";
			this.options = List.of(
				new OptionData(OptionType.STRING, "mentions", lu.getText(path+".mentions.help"), true)
			);
			this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Member author = event.getMember();
			String authorId = author.getId();

			String channelId = bot.getDBUtil().voice.getChannel(authorId);
			if (channelId == null) {
				editError(event, "errors.no_channel");
				return;
			}

			Mentions mentions = event.optMentions("mentions");
			if (mentions == null) {
				editError(event, path+".invalid_args");
				return;
			}

			List<Member> members = mentions.getMembers();
			List<Role> roles = mentions.getRoles();
			if (members.isEmpty() & roles.isEmpty()) {
				editError(event, path+".invalid_args");
				return;
			}
			if (members.contains(author) || members.contains(event.getGuild().getSelfMember())) {
				editError(event, path+".not_self");
				return;
			}

			List<String> mentionStrings = new ArrayList<>();
			VoiceChannel vc = event.getGuild().getVoiceChannelById(channelId);
			VoiceChannelManager vcManager = vc.getManager();

			for (Member member : members) {
				vcManager = vcManager.putPermissionOverride(member, null, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL));
				if (vc.getMembers().contains(member)) {
					event.getGuild().kickVoiceMember(member).queue();
				}
				mentionStrings.add(member.getEffectiveName());
			}

			for (Role role : roles) {
				if (!role.hasPermission(AdminPerms)) {
					vcManager = vcManager.putPermissionOverride(role, null, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL));
					mentionStrings.add(role.getName());
				}
			}

			vcManager.queue(done -> {
				editHookEmbed(event, bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getUserText(event, path+".done", mentionStrings))
					.build()
				);
			}, failure -> {
				editPermError(event, Permission.MANAGE_PERMISSIONS, true);
			});
		}
		
	}

	private class PermsView extends SlashCommand {

		public PermsView(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "view";
			this.path = "bot.voice.voice.perms.view";
			this.subcommandGroup = new SubcommandGroupData("perms", lu.getText("bot.voice.voice.perms.help"));
			this.botPermissions = new Permission[]{Permission.MANAGE_PERMISSIONS};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Member author = event.getMember();
			String authorId = author.getId();

			String channelId = bot.getDBUtil().voice.getChannel(authorId);
			if (channelId == null) {
				editError(event, "errors.no_channel");
				return;
			}

			Guild guild = event.getGuild();
			VoiceChannel vc = guild.getVoiceChannelById(channelId);

			EmbedBuilder embedBuilder = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getText(event, path+".embed.title").replace("{channel}", vc.getName()))
				.setDescription(lu.getText(event, path+".embed.field")+"\n\n");

			//@Everyone
			PermissionOverride publicOverride = vc.getPermissionOverride(guild.getPublicRole());

			String view = contains(publicOverride, Permission.VIEW_CHANNEL);
			String join = contains(publicOverride, Permission.VOICE_CONNECT);
			
			embedBuilder = embedBuilder.appendDescription(formatHolder(lu.getText(event, path+".embed.everyone"), view, join))
				.appendDescription("\n\n" + lu.getText(event, path+".embed.roles") + "\n");

			//Roles
			List<PermissionOverride> overrides = new ArrayList<>(vc.getRolePermissionOverrides()); // cause given override list is immutable
			try {
				overrides.remove(vc.getPermissionOverride(guild.getBotRole())); // removes bot's role
				overrides.remove(vc.getPermissionOverride(guild.getPublicRole())); // removes @everyone role
			} catch (NullPointerException ex) {
				bot.getLogger().warn("PermsCmd null pointer at role override remove");
			}
			
			if (overrides.isEmpty()) {
				embedBuilder.appendDescription(lu.getText(event, path+".embed.none") + "\n");
			} else {
				for (PermissionOverride ov : overrides) {
					view = contains(ov, Permission.VIEW_CHANNEL);
					join = contains(ov, Permission.VOICE_CONNECT);

					embedBuilder.appendDescription(formatHolder(ov.getRole().getName(), view, join) + "\n");
				}
			}
			embedBuilder.appendDescription("\n" + lu.getText(event, path+".embed.members") + "\n");

			//Members
			overrides = new ArrayList<>(vc.getMemberPermissionOverrides());
			try {
				overrides.remove(vc.getPermissionOverride(author)); // removes user
				overrides.remove(vc.getPermissionOverride(guild.getSelfMember())); // removes bot
			} catch (NullPointerException ex) {
				bot.getLogger().warn("PermsCmd null pointer at member override remove");
			}

			EmbedBuilder embedBuilder2 = embedBuilder;
			List<PermissionOverride> ovs = overrides;

			guild.retrieveMembersByIds(false, overrides.stream().map(ov -> ov.getId()).toArray(String[]::new)).onSuccess(
				members -> {
					if (members.isEmpty()) {
						embedBuilder2.appendDescription(lu.getText(event, path+".embed.none") + "\n");
					} else {
						for (PermissionOverride ov : ovs) {
							String view2 = contains(ov, Permission.VIEW_CHANNEL);
							String join2 = contains(ov, Permission.VOICE_CONNECT);

							Member find = members.stream().filter(m -> m.getId().equals(ov.getId())).findFirst().get(); 
							embedBuilder2.appendDescription(formatHolder(find.getEffectiveName(), view2, join2) + "\n");
						}
					}

					editHookEmbed(event, embedBuilder2.build());
				}
			);
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

		@Nonnull
		private String formatHolder(String holder, String view, String join) {
			return "> " + view + " | " + join + " | `" + holder + "`";
		}

	}

	private class PermsReset extends SlashCommand {

		public PermsReset(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "reset";
			this.path = "bot.voice.voice.perms.reset";
			this.subcommandGroup = new SubcommandGroupData("perms", lu.getText("bot.voice.voice.perms.help"));
			this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Member author = event.getMember();

			String channelId = bot.getDBUtil().voice.getChannel(author.getId());
			if (channelId == null) {
				createError(event, "errors.no_channel");
				return;
			}

			VoiceChannel vc = event.getGuild().getVoiceChannelById(channelId);
			try {
				vc.getManager().sync().queue();
				vc.upsertPermissionOverride(author).setAllowed(Permission.MANAGE_CHANNEL).queue();
			} catch (InsufficientPermissionException ex) {
				createPermError(event, ex.getPermission(), true);
				return;
			}

			createReplyEmbed(event,
				bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getText(event, path+".done"))
					.build()
			);
		}
		
	}

}
