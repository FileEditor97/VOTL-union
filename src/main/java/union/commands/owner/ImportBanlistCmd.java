package union.commands.owner;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.AttachmentProxy;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.constants.CmdCategory;
import union.utils.CastUtil;
import union.utils.SteamUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ImportBanlistCmd extends CommandBase {

	public ImportBanlistCmd() {
		this.name = "banlist";
		this.path = "bot.owner.banlist";
		this.options = List.of(
			new OptionData(OptionType.STRING, "table", lu.getText(path + ".table.help"), true),
			new OptionData(OptionType.ATTACHMENT, "file", lu.getText(path + ".file.help"), true)
		);
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();

		String table = event.optString("table");
		// Get file
		try (final Message.Attachment attachment = event.optAttachment("file")) {
			if (attachment == null) {
				editErrorOther(event, "Attachment is null!");
				return;
			}

			// Check if size is allowed
			if (attachment.getSize() > 2*1_048_576) {
				editErrorOther(event, "Bad size!\nReceived: %.2fMiB / 2MiB".formatted(attachment.getSize()/1_048_576.0));
				return;
			}

			String contentType = attachment.getContentType();
			if (contentType.startsWith("text/plain")) {
				withInputStream(attachment.getProxy(), inputStream -> {
					int added = 0;
					try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
						String firstLine = reader.readLine();
						if (isSteamID(firstLine)) {
							// SteamID
							String line;
							while ((line = reader.readLine()) != null) {
								long steam64 = SteamUtil.convertSteamIDtoSteam64(line);
								if (bot.getDBUtil().banlist.add(table, steam64)) added++;
							}
						} else {
							// Steam64
							String line;
							while ((line = reader.readLine()) != null) {
								long steam64 = Long.parseLong(line);
								if (bot.getDBUtil().banlist.add(table, steam64)) added++;
							}
						}
					} catch (IOException e) {
						bot.getAppLogger().warn(e.getMessage(), e);
						editErrorOther(event, e.getMessage());
						return;
					}
					editMsg(event, "Added %s entries to `%s` table.".formatted(added, table));
				});
			} else if (contentType.startsWith("application/json")) {
				withInputStream(attachment.getProxy(), inputStream -> {
					int added = 0;
					List<Map<String, String>> list = JsonPath.using(Configuration.defaultConfiguration()
							.addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL))
						.parse(inputStream).read("$[*]");
					if (!list.isEmpty()) {
						int keySize = list.get(0).keySet().size();
						if (keySize == 3) {
							// Octo table
							for (Map<String, String> map : list) {
								long steam64 = CastUtil.castLong(map.get("steamID"));
								String reason = map.get("reason");
								String details = map.get("details");
								if (bot.getDBUtil().banlist.add(table, steam64, reason, details)) added++;
							}
						} else if (keySize == 2) {
							// Custom with reason
							for (Map<String, String> map : list) {
								long steam64 = CastUtil.castLong(map.get("steam64"));
								String reason = map.get("reason");
								if (bot.getDBUtil().banlist.add(table, steam64, reason)) added++;
							}
						} else if (keySize == 1) {
							// Custom without reason
							for (Map<String, String> map : list) {
								long steam64 = CastUtil.castLong(map.get("steam64"));
								if (bot.getDBUtil().banlist.add(table, steam64)) added++;
							}
						}
					}
					editMsg(event, "Added %s entries to `%s` table.".formatted(added, table));
				});
			} else {
				editErrorOther(event, "Only txt and json allowed!\nProvided: "+contentType);
			}
		} catch (Throwable t) {
			bot.getAppLogger().warn(t.getMessage(), t);
			editErrorOther(event, t.getMessage());
		}
	}

	private static boolean isSteamID(String line) {
		return line != null && line.startsWith("STEAM_");
	}

	private void withInputStream(AttachmentProxy proxy, Consumer<InputStream> callback) {
		proxy.download().whenCompleteAsync((inputStream, throwable) -> {
			if (throwable != null) {
				bot.getAppLogger().warn("Failed to download", throwable);
			} else {
				callback.accept(inputStream);
			}
		});
	}

}
