package union.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.TimeUtil;
import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class PurgeCmd extends CommandBase {

	public PurgeCmd() {
		this.name = "purge";
		this.path = "bot.moderation.purge";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help")),
			new OptionData(OptionType.INTEGER, "count", lu.getText(path+".count.help"))
				.setRequiredRange(1, 50)
		);
		this.botPermissions = new Permission[]{Permission.MESSAGE_MANAGE};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		this.cooldown = 20;
		this.cooldownScope = CooldownScope.USER;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();

		int toDelete = event.optInteger("count", 5);
		User target = event.optUser("user");

		if (target == null) {
			loadMessages(event.getChannel().getHistory(), toDelete, null, messages -> {
				if (messages.isEmpty()) {
					sendNoMessages(event, null);
					return;
				}

				deleteMessages(event.getChannel().asGuildMessageChannel(), messages).queue(avoid -> {
					// Log
					bot.getLogger().mod.onMessagePurge(event.getUser(), null, toDelete, event.getGuildChannel());
					// Reply
					event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, path+".done").formatted(toDelete))
						.build()
					).queue(msg -> msg.delete().queueAfter(4, TimeUnit.SECONDS, null, ignoreRest));
				}, ignoreRest);
			});
			return;
		}

		loadMessages(event.getChannel().getHistory(), toDelete, target.getIdLong(), messages -> {
			if (messages.isEmpty()) {
				sendNoMessages(event, target);
				return;
			}

			deleteMessages(event.getChannel().asGuildMessageChannel(), messages).queue(avoid -> {
				// Log
				bot.getLogger().mod.onMessagePurge(event.getUser(), target, toDelete, event.getGuildChannel());
				// Reply
				event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done_user").formatted(toDelete, target.getEffectiveName()))
					.build()
				).queue(msg -> msg.delete().queueAfter(4, TimeUnit.SECONDS, null, ignoreRest));
			}, ignoreRest);
		});
	}

	private void sendNoMessages(SlashCommandEvent event, User target) {
		String text = target==null ?
			lu.getText(event, path+".empty") :
			lu.getText(event, path+".empty_user").formatted(target.getEffectiveName());

		event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_WARNING)
			.setDescription(text)
			.build()
		).queue(msg -> msg.delete().queueAfter(4, TimeUnit.SECONDS, null, ignoreRest));
	}


	private void loadMessages(MessageHistory history, int toDelete, Long targetId, Consumer<List<Message>> consumer) {
		long maxMessageAge = TimeUtil.getDiscordTimestamp(Instant.now().minus(Duration.ofDays(7)).toEpochMilli());
		List<Message> messages = new ArrayList<>();

		history.retrievePast(toDelete).queue(historyMessage -> {
			if (historyMessage.isEmpty()) {
				consumer.accept(messages);
				return;
			}

			for (Message message : historyMessage) {
				if (message.isPinned() || message.getIdLong() < maxMessageAge) {
					continue;
				}

				if (targetId != null && !targetId.equals(message.getAuthor().getIdLong())) {
					continue;
				}

				if (messages.size() >= toDelete) {
					consumer.accept(messages);
					return;
				}

				messages.add(message);
			}

			consumer.accept(messages);
		});
	}

	private RestAction<Void> deleteMessages(GuildMessageChannel channel, List<Message> messages) {
		if (messages.size() == 1) {
			return messages.get(0).delete();
		}
		return channel.deleteMessages(messages);
	}

}
