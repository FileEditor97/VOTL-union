package union.commands.owner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import union.App;
import union.base.command.Category;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.utils.FileUpload;

import org.json.JSONObject;

public class GenerateListCmd extends CommandBase {
	
	public GenerateListCmd(App bot) {
		super(bot);
		this.name = "generate";
		this.path = "bot.owner.generate";
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
		this.guildOnly = false;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		List<SlashCommand> commands = event.getClient().getSlashCommands();
		if (commands.size() == 0) {
			createReply(event, "Commands not found");
			return;
		}

		event.deferReply(true).queue();

		JSONObject result = new JSONObject();
		for (Integer i = 0; i < commands.size(); i++) {
			SlashCommand cmd = commands.get(i);

			JSONObject jsonObject = new JSONObject();
			jsonObject.put("name", cmd.getName())
				.put("description", getText(cmd.getHelpPath()))
				.put("category", getCategoryMap(cmd.getCategory()))
				.put("guildOnly", cmd.isGuildOnly())
				.put("access", cmd.getAccessLevel().getLevel());

			if (cmd.getModule() == null) {
				jsonObject.put("module", Map.of("en-GB", "", "ru", ""));
			} else {
				jsonObject.put("module", getText(cmd.getModule().getPath()));
			}
			
			if (cmd.getChildren().length > 0) {
				List<Map<String, Object>> values = new ArrayList<>();
				for (SlashCommand child : cmd.getChildren()) {
					values.add(Map.of("description", getText(child.getHelpPath()), "usage", getText(child.getUsagePath())));
				}
				jsonObject.put("child", values);
				jsonObject.put("usage", Map.of("en-GB", "", "ru", ""));
			} else {
				jsonObject.put("child", Collections.emptyList());
				jsonObject.put("usage", getText(cmd.getUsagePath()));
			}
			
			result.put(i.toString(), jsonObject);
		}

		File file = new File(Constants.DATA_PATH + "commands.json");
		try {
			file.createNewFile();
			FileWriter writer = new FileWriter(file, Charset.forName("utf-8"));
			writer.write(result.toString());
			writer.flush();
			writer.close();
			event.getHook().editOriginalAttachments(FileUpload.fromData(file, "commands.json")).queue(hook -> file.delete());
		} catch (IOException | UncheckedIOException ex) {
			editError(event, path+".error", ex.getMessage());
		}
	}

	private Map<String, Object> getCategoryMap(Category category) {
		if (category == null) {
			return Map.of("name", "", "en-GB", "", "ru", "");
		}
		Map<String, Object> map = new HashMap<>();
		map.put("name", category.getName());
		map.putAll(getText("bot.help.command_menu.categories."+category.getName()));
		return map;
	}

	private Map<String, Object> getText(String path) {
		Map<String, Object> map = new HashMap<>();
		for (DiscordLocale locale : bot.getFileManager().getLanguages()) {
			map.put(locale.getLocale(), lu.getLocalized(locale, path));
		}
		return map;
	}

}
