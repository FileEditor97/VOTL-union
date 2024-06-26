package union.utils.transcripts;

import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Ryzeon
 * Project: discord-html-transcripts
 * Date: 2/12/21 @ 00:32
 * Twitter: @Ryzeon_ 😎
 * Github: github.ryzeon.me
 */
public class Formatter {

    private Formatter() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    // All Message related patterns
    private static final Pattern STRONG = Pattern.compile("\\*\\*(.+?)\\*\\*"); // Bold
    private static final Pattern EM = Pattern.compile("\\*(.+?)\\*|_(.+?)_"); // Italics
    private static final Pattern S = Pattern.compile("~~(.+?)~~"); // Strikethrough
    private static final Pattern U = Pattern.compile("__(.+?)__"); // Underline
    private static final Pattern CODE = Pattern.compile("```(.+?)```"); // Multi-line code block
    private static final Pattern CODE_1 = Pattern.compile("`(.+?)`"); // Code block
    private static final Pattern QUOTE = Pattern.compile("^>{1,3} (.*)$"); // Quote (one line or multiple)
    private static final Pattern MASKED_LINK = Pattern.compile("\\[([^\\[]+)](\\((www|http:|https:)+[^\\s]+[\\w]\\))"); // Masked links
    private static final Pattern LINK = Pattern.compile("^(?!.*\\[[^]]*]\\([^)]*\\))((www|http:|https:)[^\\s]+[\\w])$"); // Link
    private static final Pattern EMOJI = Pattern.compile("<a?:([a-zA-Z0-9_]+):([0-9]+)>"); // Emoji

    // Pattern to detect new lines
    private static final Pattern NEW_LINE = Pattern.compile("\\r\\n|\\r|\\n|\\u2028|\\u2029"); // New line (and it's variants)

    public static String formatBytes(long bytes) {
        int unit = 1024;
        if (bytes < unit)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = String.valueOf("KMGTPE".charAt(exp - 1));
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static String format(String originalText) {
        Matcher matcher = STRONG.matcher(originalText);
        String newText = originalText;
        while (matcher.find()) {
            String group = matcher.group();
            newText = newText.replace(group,
                    "<strong>" + group.replace("**", "") + "</strong>");
        }
        matcher = EM.matcher(newText);
        while (matcher.find()) {
            String group = matcher.group();
            newText = newText.replace(group,
                    "<em>" + group.replace("*", "").replace("_", "") + "</em>");
        }
        matcher = S.matcher(newText);
        while (matcher.find()) {
            String group = matcher.group();
            newText = newText.replace(group,
                    "<s>" + group.replace("~~", "") + "</s>");
        }
        matcher = U.matcher(newText);
        while (matcher.find()) {
            String group = matcher.group();
            newText = newText.replace(group,
                    "<u>" + group.replace("__", "") + "</u>");
        }
        matcher = QUOTE.matcher(newText);
        while (matcher.find()) {
            String group = matcher.group();
            newText = newText.replace(group,
                    "<span class=\"quote\">" + group.replaceFirst(">>>", "").replaceFirst(">", "") + "</span>");
        }
        matcher = MASKED_LINK.matcher(newText);
        while (matcher.find()) {
            String group = matcher.group(1);
            String link = matcher.group(2);
            String raw = "[" + group + "]" + link;

            newText = newText.replace(raw, "<a href=\"" + link.replace("(", "").replace(")", "") + "\">" + group + "</a>");
        }

        matcher = LINK.matcher(newText);
        while (matcher.find()) {
            String link = matcher.group();
            newText = "<a href=\"" + link + "\">" + link + "</a>";
        }

        matcher = CODE.matcher(newText);
        while (matcher.find()) {
            String group = matcher.group();
            newText = newText.replace(group,
                    "<div class=\"pre pre--multiline nohighlight\">"
                            + group.replace("```", "") + "</div>");
        }
        matcher = CODE_1.matcher(newText);
        while (matcher.find()) {
            String group = matcher.group();
            newText = newText.replace(group,
                    "<span class=\"pre pre--inline\">" + group.replace("`", "") + "</span>");
        }
        matcher = EMOJI.matcher(newText);
        while(matcher.find()) {
            String group = matcher.group();
            Emoji emoji = Emoji.fromFormatted(group);
            if (emoji.getType() == Emoji.Type.CUSTOM) {
                CustomEmoji customEmoji = (CustomEmoji)emoji;
                newText = newText.replace(group,
                        "<img class=\"emoji\" src=\"" + customEmoji.getImageUrl() + "\">");
            }
        }

        matcher = NEW_LINE.matcher(newText);
        while (matcher.find()) {
            newText = newText.replace(matcher.group(), "<br />");
        }
        return newText;
    }

    public static String toHex(Color color) {
        StringBuilder hex = new StringBuilder(Integer.toHexString(color.getRGB() & 0xffffff));
        while (hex.length() < 6) {
            hex.insert(0, "0");
        }
        return hex.toString();
    }
}
