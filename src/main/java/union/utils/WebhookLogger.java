package union.utils;

import java.util.function.Supplier;

import union.objects.LogChannels;
import union.objects.annotation.NotNull;
import union.utils.database.DBUtil;
import union.utils.database.managers.GuildLogsManager.WebhookData;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.internal.requests.IncomingWebhookClientImpl;

public class WebhookLogger {

	private final DBUtil db;

	public WebhookLogger(DBUtil dbUtil) {
		this.db = dbUtil;
	}

	public void sendMessageEmbed(JDA client, long guildId, LogChannels type, @NotNull MessageEmbed embed) {
		WebhookData data = db.logs.getLogWebhook(type, guildId);
		if (data != null)
			new IncomingWebhookClientImpl(data.getWebhookId(), data.getToken(), client)
				.sendMessageEmbeds(embed).queue();
	}

	public void sendMessageEmbed(JDA client, long guildId, LogChannels type, @NotNull Supplier<MessageEmbed> embedSupplier) {
		WebhookData data = db.logs.getLogWebhook(type, guildId);
		if (data != null)
			new IncomingWebhookClientImpl(data.getWebhookId(), data.getToken(), client)
				.sendMessageEmbeds(embedSupplier.get()).queue();
	}

	public void sendMessageEmbed(Guild guild, LogChannels type, @NotNull MessageEmbed embed) {
		sendMessageEmbed(guild.getJDA(), guild.getIdLong(), type, embed);
	}

	public void sendMessageEmbed(Guild guild, LogChannels type, @NotNull Supplier<MessageEmbed> embedSupplier) {
		sendMessageEmbed(guild.getJDA(), guild.getIdLong(), type, embedSupplier);
	}

	public IncomingWebhookClientImpl getWebhookClient(Guild guild, LogChannels type) {
		WebhookData data = db.logs.getLogWebhook(type, guild.getIdLong());
		if (data == null)
			return null;
		return new IncomingWebhookClientImpl(data.getWebhookId(), data.getToken(), guild.getJDA());
	}

	

}
