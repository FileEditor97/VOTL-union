package union.objects.logs;

import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MessageData {
	private final String content, authorName;
	private final Message.Attachment attachment;
	private final long authorId;

	public MessageData(Message message) {
		this.content = message.getContentRaw();
		if (message.getAttachments().isEmpty())
			this.attachment = null;
		else
			this.attachment = message.getAttachments().get(0);
		this.authorId = message.getAuthor().getIdLong();
		this.authorName = message.getAuthor().getName();
	}

	public String getContent() {
		return content;
	}

	public String getContentStripped() {
		return MarkdownSanitizer.sanitize(content);
	}

	public String getContentEscaped() {
		return MarkdownSanitizer.escape(content);
	}

	public Message.Attachment getAttachment() {
		return attachment;
	}

	public long getAuthorId() {
		return authorId;
	}

	public String getAuthorName() {
		return authorName;
	}

	public boolean isEmpty() {
		return content.isBlank() && attachment == null;
	}

	@Nullable
	public static DiffData getDiffContent(@NotNull String oldContent, @NotNull String newContent) {
		if (oldContent.equals(newContent)) return null;
		DiffRowGenerator generator = DiffRowGenerator.create()
			.showInlineDiffs(true)
			.inlineDiffByWord(true)
			.ignoreWhiteSpaces(true)
			.lineNormalizer(f -> f)
			.newTag(f -> "")
			.oldTag(f -> "")
			.build();
		List<DiffRow> rows = generator.generateDiffRows(
			List.of(oldContent.split("\\n")),
			List.of(newContent.split("\\n"))
		);

		StringBuilder diff = new StringBuilder();
		int linesChanged = 0;
		int charsChanged = 0;
		boolean skipped = false;
		final int size = rows.size();
		for (int i = 0; i<size; i++) {
			DiffRow row = rows.get(i);
			if (row.getTag().equals(DiffRow.Tag.EQUAL)) {
				if ((i+1 >= size || rows.get(i+1).getTag().equals(DiffRow.Tag.EQUAL))
					&& (i-1 < 0 || rows.get(i-1).getTag().equals(DiffRow.Tag.EQUAL)))
				{
					skipped = true;
					continue;
				}
			}
			if (skipped) {
				diff.append(" ...\n");
				skipped = false;
			}

			if (!row.getTag().equals(DiffRow.Tag.EQUAL)) {
				linesChanged++;
				charsChanged += Math.max(row.getOldLine().length(), row.getNewLine().length());
			}
			switch (row.getTag()) {
				case INSERT -> diff.append("+ ").append(row.getNewLine());
				case DELETE -> diff.append("- ").append(row.getOldLine());
				case CHANGE -> diff.append("- ").append(row.getOldLine())
					.append("\n")
					.append("+ ").append(row.getNewLine());
				default -> diff.append(" ").append(row.getOldLine());
			}
			diff.append("\n");
		}
		return new DiffData(diff.toString(), linesChanged, charsChanged);
	}

	public record DiffData(String content, int linesChanged, int charsChanged) {
		public boolean manyChanges() {
			return linesChanged > 3 || charsChanged > 600;
		}
	}
}


