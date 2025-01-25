package union.utils.transcripts;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Component;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.utils.FileUpload;

import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import union.utils.encoding.EncodingUtil;

/**
 * Created by Ryzeon
 * Contributors: Inkception
 * Project: discord-html-transcripts
 * Date: 1/3/2023 @ 10:50
 */
public class DiscordHtmlTranscripts {

    private static DiscordHtmlTranscripts instance;
    private final List<String>
            imageFormats = Arrays.asList("png", "jpg", "jpeg", "gif"),
            videoFormats = Arrays.asList("mp4", "webm", "mkv", "avi", "mov", "flv", "wmv", "mpg", "mpeg"),
            audioFormats = Arrays.asList("mp3", "wav", "ogg", "flac");

    public static DiscordHtmlTranscripts getInstance() {
        if (instance == null) {
            instance = new DiscordHtmlTranscripts();
        }
        return instance;
    }

    public void queueCreateTranscript(GuildMessageChannel channel, @NotNull Consumer<FileUpload> action, @NotNull Consumer<? super Throwable> failure) {
        channel.getIterableHistory()
            .deadline(System.currentTimeMillis() + 3000)
            .takeAsync(200)
            .thenAcceptAsync(list -> {
                if (list.size() < 2) action.accept(null); // Probably one message is from bot and to be ignored.
                if (list.size() <= 6) {
                    // Check if history has repeated authors - then it's bot
                    Set<Long> ids = new HashSet<>();
                    list.forEach(msg -> ids.add(msg.getAuthor().getIdLong()));
                    if (ids.size() <= 1) action.accept(null);
                }
                try {
                    final String fileName = EncodingUtil.encodeTranscript(channel.getGuild().getIdLong(), channel.getIdLong());
                    action.accept(FileUpload.fromData(generateFromMessages(list), fileName));
                } catch(Exception ex) {
                    failure.accept(ex);
                }
            });
    }

    private InputStream findFile() {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("template.html");
        if (inputStream == null) {
            throw new IllegalArgumentException("Could not find template file");
        }
        return inputStream;
    }

    /**
     * Generates a transcript from provided messages.<br><br>
     *
     * <b>Please make sure that the current logged in account enabled the
     * {@link net.dv8tion.jda.api.requests.GatewayIntent#MESSAGE_CONTENT MESSAGE_CONTENT}
     * intent and is not missing following permissions before creating a transcript:</b>
     * <ul>
     *     <li>{@link net.dv8tion.jda.api.Permission#VIEW_CHANNEL VIEW_CHANNEL}</li>
     *     <li>{@link net.dv8tion.jda.api.Permission#MESSAGE_HISTORY MESSAGE_HISTORY}</li>
     * </ul>
     *
     * @param messages A collection of messages to generate the transcript from.
     * @return An InputStream containing the transcript. Can be used to e.g. create a {@link FileUpload#fromData FileUpload.fromData}.
     * @throws IOException If the template file can not be found
     * @throws net.dv8tion.jda.api.exceptions.InsufficientPermissionException If the current logged in account
     *         does not have the permission {@link net.dv8tion.jda.api.Permission#MESSAGE_HISTORY MESSAGE_HISTORY}
     */
    public InputStream generateFromMessages(Collection<Message> messages) throws IOException, InsufficientPermissionException {
        InputStream htmlTemplate = findFile();
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("No messages to generate a transcript from");
        }

        GuildChannel channel = messages.iterator().next().getChannel().asGuildMessageChannel();

        Document document = Jsoup.parse(htmlTemplate, "UTF-8", "template.html");
        document.outputSettings().indentAmount(0).prettyPrint(true);
        document.getElementsByClass("preamble__guild-icon").first().attr("src", channel.getGuild().getIconUrl()); // set guild icon

        document.getElementById("transcriptTitle").text("#" + channel.getName() + " | " + messages.size() + " messages"); // set title
        document.getElementById("guildname").text(channel.getGuild().getName()); // set guild name
        document.getElementById("ticketname").text("#" + channel.getName()); // set channel name

        Element chatLog = document.getElementById("chatlog"); // chat log
        if (chatLog == null) {
            throw new NullPointerException("Element 'chatlog' can not be null!");
        }

        for (Message message : messages.stream()
                .sorted(Comparator.comparing(ISnowflake::getTimeCreated))
                .toList()) {
            // create message group
            Element messageGroup = document.createElement("div");
            messageGroup.addClass("chatlog__message-group");
            messageGroup.id("message-" + message.getId());

            // Handle pins
            if (message.getType() == MessageType.CHANNEL_PINNED_ADD) {
                handlePinnedMessages(document, message, messageGroup);
                chatLog.appendChild(messageGroup);
                continue;
            }

            // Handle and display slash commands
            if (message.getType() == MessageType.SLASH_COMMAND) {
                handleSlashCommands(document, message, messageGroup);
            }

            // Handle inline replies
            if (message.getReferencedMessage() != null) {
                handleMessageReferences(document, message, messageGroup);
            }

            var author = message.getAuthor();

            Element authorElement = document.createElement("div");
            authorElement.addClass("chatlog__author-avatar-container");

            Element authorAvatar = document.createElement("img");
            authorAvatar.addClass("chatlog__author-avatar");
            authorAvatar.attr("src", author.getEffectiveAvatarUrl());
            authorAvatar.attr("alt", "Avatar");
            authorAvatar.attr("loading", "lazy");

            authorElement.appendChild(authorAvatar);
            messageGroup.appendChild(authorElement);

            // message content
            Element content = document.createElement("div");
            content.addClass("chatlog__messages");

            // message author name
            Element authorName = document.createElement("span");
            authorName.addClass("chatlog__author-name");
            authorName.attr("title", author.getName());
            authorName.text(author.getName());
            authorName.attr("data-user-id", author.getId());
            content.appendChild(authorName);

            if (author.isBot()) {
                Element botTag = document.createElement("span");
                botTag.addClass("chatlog__bot-tag").text("BOT");
                content.appendChild(botTag);
            }

            // timestamp
            Element timestamp = document.createElement("span");
            timestamp.addClass("chatlog__timestamp");
            timestamp.text(message.getTimeCreated()
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));

            content.appendChild(timestamp);

            Element messageContent = document.createElement("div");
            messageContent.addClass("chatlog__message");
            messageContent.attr("data-message-id", message.getId());
            messageContent.attr("title", "Message sent: " + message.getTimeCreated()
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));

            if (!message.getContentDisplay().isEmpty()) {
                Element messageContentContent = document.createElement("div");
                messageContentContent.addClass("chatlog__content");

                Element messageContentContentMarkdown = document.createElement("div");
                messageContentContentMarkdown.addClass("markdown");

                Element messageContentContentMarkdownSpan = document.createElement("span");
                messageContentContentMarkdownSpan.addClass("preserve-whitespace");
                messageContentContentMarkdownSpan.html(Formatter.format(message.getContentDisplay()));

                messageContentContentMarkdown.appendChild(messageContentContentMarkdownSpan);
                messageContentContent.appendChild(messageContentContentMarkdown);
                messageContent.appendChild(messageContentContent);
            }

            // messsage attachments
            if (!message.getAttachments().isEmpty()) {
                for (Message.Attachment attach : message.getAttachments()) {
                    Element attachmentsDiv = document.createElement("div");
                    attachmentsDiv.addClass("chatlog__attachment");

                    var attachmentType = attach.getFileExtension();
                    if (imageFormats.contains(attachmentType)) {
                        handleImages(document, attach, attachmentsDiv);
                    } else if (videoFormats.contains(attachmentType)) {
                        handleVideos(document, attach, attachmentsDiv);
                    } else if (audioFormats.contains(attachmentType)) {
                        handleAudios(document, attach, attachmentsDiv);
                    } else {
                        handleUnknownAttachmentTypes(document, attach, attachmentsDiv);
                    }

                    messageContent.appendChild(attachmentsDiv);
                }
            }

            content.appendChild(messageContent);

            if (!message.getEmbeds().isEmpty()) {
                for (MessageEmbed embed : message.getEmbeds()) {
                    if (embed == null) {
                        continue;
                    }
                    Element embedDiv = document.createElement("div");
                    embedDiv.addClass("chatlog__embed");

                    // embed color
                    Element embedColorPill = document.createElement("div");

                    if (embed.getColor() == null) {
                        embedColorPill.addClass("chatlog__embed-color-pill chatlog__embed-color-pill--default");
                    } else {
                        embedColorPill.addClass("chatlog__embed-color-pill");
                        embedColorPill.attr("style",
                                "background-color: #" + Formatter.toHex(embed.getColor()));
                    }
                    embedDiv.appendChild(embedColorPill);

                    Element embedContentContainer = document.createElement("div");
                    embedContentContainer.addClass("chatlog__embed-content-container");

                    Element embedContent = document.createElement("div");
                    embedContent.addClass("chatlog__embed-content");

                    Element embedText = document.createElement("div");
                    embedText.addClass("chatlog__embed-text");

                    // embed author
                    if (embed.getAuthor() != null && embed.getAuthor().getName() != null) {
                        handleEmbedAuthor(document, embed, embedText);
                    }

                    // embed title
                    if (embed.getTitle() != null) {
                        handleEmbedTitle(document, embed, embedText);
                    }

                    // embed description
                    if (embed.getDescription() != null) {
                        handleEmbedDescription(document, embed, embedText);
                    }

                    // embed fields
                    if (!embed.getFields().isEmpty()) {
                        handleEmbedFields(document, embed, embedText);
                    }

                    embedContent.appendChild(embedText);

                    // embed thumbnail
                    if (embed.getThumbnail() != null) {
                        handleEmbedThumbnail(document, embed, embedContent);
                    }

                    embedContentContainer.appendChild(embedContent);

                    // embed image
                    if (embed.getImage() != null) {
                        handleEmbedImage(document, embed, embedContentContainer);
                    }

                    // embed footer
                    if (embed.getFooter() != null) {
                        handleFooter(document, embed, embedContentContainer);
                    }

                    embedDiv.appendChild(embedContentContainer);
                    content.appendChild(embedDiv);
                }
            }

            if (!message.getComponents().isEmpty()) {
                handleInteractionComponents(document, message, content);
            }

            messageGroup.appendChild(content);
            chatLog.appendChild(messageGroup);
        }
        return new ByteArrayInputStream(document.outerHtml().getBytes(StandardCharsets.UTF_8));
    }

    private static void handleFooter(Document document, MessageEmbed embed, Element embedContentContainer) {
        Element embedFooter = document.createElement("div");
        embedFooter.addClass("chatlog__embed-footer");

        if (embed.getFooter().getIconUrl() != null) {
            Element embedFooterIcon = document.createElement("img");
            embedFooterIcon.addClass("chatlog__embed-footer-icon");
            embedFooterIcon.attr("src", embed.getFooter().getIconUrl());
            embedFooterIcon.attr("alt", "Footer icon");
            embedFooterIcon.attr("loading", "lazy");

            embedFooter.appendChild(embedFooterIcon);
        }

        Element embedFooterText = document.createElement("span");
        embedFooterText.addClass("chatlog__embed-footer-text");
        embedFooterText.text(embed.getTimestamp() != null
                ? embed.getFooter().getText() + " • " + embed.getTimestamp()
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))
                : embed.getFooter().getText());

        embedFooter.appendChild(embedFooterText);

        embedContentContainer.appendChild(embedFooter);
    }

    private static void handleEmbedImage(Document document, MessageEmbed embed, Element embedContentContainer) {
        Element embedImage = document.createElement("div");
        embedImage.addClass("chatlog__embed-image-container");

        Element embedImageLink = document.createElement("a");
        embedImageLink.addClass("chatlog__embed-image-link");
        embedImageLink.attr("href", embed.getImage().getUrl());

        Element embedImageImage = document.createElement("img");
        embedImageImage.addClass("chatlog__embed-image");
        embedImageImage.attr("src", embed.getImage().getUrl());
        embedImageImage.attr("alt", "Image");
        embedImageImage.attr("loading", "lazy");

        embedImageLink.appendChild(embedImageImage);
        embedImage.appendChild(embedImageLink);

        embedContentContainer.appendChild(embedImage);
    }

    private static void handleEmbedThumbnail(Document document, MessageEmbed embed, Element embedContent) {
        Element embedThumbnail = document.createElement("div");
        embedThumbnail.addClass("chatlog__embed-thumbnail-container");

        Element embedThumbnailLink = document.createElement("a");
        embedThumbnailLink.addClass("chatlog__embed-thumbnail-link");
        embedThumbnailLink.attr("href", embed.getThumbnail().getUrl());

        Element embedThumbnailImage = document.createElement("img");
        embedThumbnailImage.addClass("chatlog__embed-thumbnail");
        embedThumbnailImage.attr("src", embed.getThumbnail().getUrl());
        embedThumbnailImage.attr("alt", "Thumbnail");
        embedThumbnailImage.attr("loading", "lazy");

        embedThumbnailLink.appendChild(embedThumbnailImage);
        embedThumbnail.appendChild(embedThumbnailLink);

        embedContent.appendChild(embedThumbnail);
    }

    private static void handleEmbedFields(Document document, MessageEmbed embed, Element embedText) {
        Element embedFields = document.createElement("div");
        embedFields.addClass("chatlog__embed-fields");

        for (MessageEmbed.Field field : embed.getFields()) {
            Element embedField = document.createElement("div");
            embedField.addClass("chatlog__embed-field");

            // Field name
            Element embedFieldName = document.createElement("div");
            embedFieldName.addClass("chatlog__embed-field-name");

            Element embedFieldNameMarkdown = document.createElement("div");
            embedFieldNameMarkdown.addClass("markdown preserve-whitespace");
            embedFieldNameMarkdown.html(field.getName());

            embedFieldName.appendChild(embedFieldNameMarkdown);
            embedField.appendChild(embedFieldName);


            // Field value
            Element embedFieldValue = document.createElement("div");
            embedFieldValue.addClass("chatlog__embed-field-value");

            Element embedFieldValueMarkdown = document.createElement("div");
            embedFieldValueMarkdown.addClass("markdown preserve-whitespace");
            embedFieldValueMarkdown
                    .html(Formatter.format(field.getValue()));

            embedFieldValue.appendChild(embedFieldValueMarkdown);
            embedField.appendChild(embedFieldValue);

            embedFields.appendChild(embedField);
        }

        embedText.appendChild(embedFields);
    }

    private static void handleEmbedDescription(Document document, MessageEmbed embed, Element embedText) {
        Element embedDescription = document.createElement("div");
        embedDescription.addClass("chatlog__embed-description");

        Element embedDescriptionMarkdown = document.createElement("div");
        embedDescriptionMarkdown.addClass("markdown preserve-whitespace");
        embedDescriptionMarkdown
                .html(Formatter.format(embed.getDescription()));

        embedDescription.appendChild(embedDescriptionMarkdown);
        embedText.appendChild(embedDescription);
    }

    private static void handleEmbedTitle(Document document, MessageEmbed embed, Element embedText) {
        Element embedTitle = document.createElement("div");
        embedTitle.addClass("chatlog__embed-title");

        if (embed.getUrl() != null) {
            Element embedTitleLink = document.createElement("a");
            embedTitleLink.addClass("chatlog__embed-title-link");
            embedTitleLink.attr("href", embed.getUrl());

            Element embedTitleMarkdown = document.createElement("div");
            embedTitleMarkdown.addClass("markdown preserve-whitespace")
                    .html(Formatter.format(embed.getTitle()));

            embedTitleLink.appendChild(embedTitleMarkdown);
            embedTitle.appendChild(embedTitleLink);
        } else {
            Element embedTitleMarkdown = document.createElement("div");
            embedTitleMarkdown.addClass("markdown preserve-whitespace")
                    .html(Formatter.format(embed.getTitle()));

            embedTitle.appendChild(embedTitleMarkdown);
        }
        embedText.appendChild(embedTitle);
    }

    private static void handleEmbedAuthor(Document document, MessageEmbed embed, Element embedText) {
        Element embedAuthor = document.createElement("div");
        embedAuthor.addClass("chatlog__embed-author");

        if (embed.getAuthor().getIconUrl() != null) {
            Element embedAuthorIcon = document.createElement("img");
            embedAuthorIcon.addClass("chatlog__embed-author-icon");
            embedAuthorIcon.attr("src", embed.getAuthor().getIconUrl());
            embedAuthorIcon.attr("alt", "Author icon");
            embedAuthorIcon.attr("loading", "lazy");

            embedAuthor.appendChild(embedAuthorIcon);
        }

        Element embedAuthorName = document.createElement("span");
        embedAuthorName.addClass("chatlog__embed-author-name");

        if (embed.getAuthor().getUrl() != null) {
            Element embedAuthorNameLink = document.createElement("a");
            embedAuthorNameLink.addClass("chatlog__embed-author-name-link");
            embedAuthorNameLink.attr("href", embed.getAuthor().getUrl());
            embedAuthorNameLink.text(embed.getAuthor().getName());

            embedAuthorName.appendChild(embedAuthorNameLink);
        } else {
            embedAuthorName.text(embed.getAuthor().getName());
        }

        embedAuthor.appendChild(embedAuthorName);
        embedText.appendChild(embedAuthor);
    }

    private static void handleUnknownAttachmentTypes(Document document, Message.Attachment attach, Element attachmentsDiv) {
        Element attachmentGeneric = document.createElement("div");
        attachmentGeneric.addClass("chatlog__attachment-generic");

        Element attachmentGenericIcon = document.createElement("svg");
        attachmentGenericIcon.addClass("chatlog__attachment-generic-icon");

        Element attachmentGenericIconUse = document.createElement("use");
        attachmentGenericIconUse.attr("xlink:href", "#icon-attachment");

        attachmentGenericIcon.appendChild(attachmentGenericIconUse);
        attachmentGeneric.appendChild(attachmentGenericIcon);

        Element attachmentGenericName = document.createElement("div");
        attachmentGenericName.addClass("chatlog__attachment-generic-name");

        Element attachmentGenericNameLink = document.createElement("a");
        attachmentGenericNameLink.attr("href", attach.getUrl());
        attachmentGenericNameLink.text(attach.getFileName());

        attachmentGenericName.appendChild(attachmentGenericNameLink);
        attachmentGeneric.appendChild(attachmentGenericName);

        Element attachmentGenericSize = document.createElement("div");
        attachmentGenericSize.addClass("chatlog__attachment-generic-size");

        attachmentGenericSize.text(Formatter.formatBytes(attach.getSize()));
        attachmentGeneric.appendChild(attachmentGenericSize);

        attachmentsDiv.appendChild(attachmentGeneric);
    }

    private static void handleAudios(Document document, Message.Attachment attach, Element attachmentsDiv) {
        Element attachmentAudio = document.createElement("audio");
        attachmentAudio.addClass("chatlog__attachment-media");
        attachmentAudio.attr("src", attach.getUrl());
        attachmentAudio.attr("alt", "Audio attachment");
        attachmentAudio.attr("controls", true);
        attachmentAudio.attr("title",
                "Audio: " + attach.getFileName() + " " + Formatter.formatBytes(attach.getSize()));

        attachmentsDiv.appendChild(attachmentAudio);
    }

    private static void handleVideos(Document document, Message.Attachment attach, Element attachmentsDiv) {
        Element attachmentVideo = document.createElement("video");
        attachmentVideo.addClass("chatlog__attachment-media");
        attachmentVideo.attr("src", attach.getUrl());
        attachmentVideo.attr("alt", "Video attachment");
        attachmentVideo.attr("controls", true);
        attachmentVideo.attr("title",
                "Video: " + attach.getFileName() + " " + Formatter.formatBytes(attach.getSize()));

        attachmentsDiv.appendChild(attachmentVideo);
    }

    private static void handleImages(Document document, Message.Attachment attach, Element attachmentsDiv) {
        Element attachmentLink = document.createElement("a");

        Element attachmentImage = document.createElement("img");
        attachmentImage.addClass("chatlog__attachment-media");
        attachmentImage.attr("src", attach.getUrl());
        attachmentImage.attr("alt", "Image attachment");
        attachmentImage.attr("loading", "lazy");
        attachmentImage.attr("title",
                "Image: " + attach.getFileName() + " " + Formatter.formatBytes(attach.getSize()));

        attachmentLink.appendChild(attachmentImage);
        attachmentsDiv.appendChild(attachmentLink);
    }

    private static void handleMessageReferences(Document document, Message message, Element messageGroup) {
        // Referenced message
        var referenceMessage = message.getReferencedMessage();

        // create symbol
        Element referenceSymbol = document.createElement("div");
        referenceSymbol.addClass("chatlog__reference-symbol");

        // create reference
        Element reference = document.createElement("div");
        reference.addClass("chatlog__reference");
        reference.attr("style", "cursor: pointer;");
        reference.attr("onclick", "scrollToMessage(event, '" + referenceMessage.getId() + "')");

        User author = referenceMessage.getAuthor();

        Element avatar = document.createElement("img");
        avatar.addClass("chatlog__reference-avatar");
        avatar.attr("src", author.getEffectiveAvatarUrl());
        avatar.attr("alt", "Avatar");
        avatar.attr("loading", "lazy");

        Element name = document.createElement("span");
        name.addClass("chatlog__reference-name");
        name.html(author.getName());

        reference.appendChild(avatar);
        reference.appendChild(name);

        if (!referenceMessage.getContentDisplay().isEmpty()) {
            Element referenceContent = document.createElement("div");
            referenceContent.addClass("chatlog__reference-content");
            referenceContent.html(referenceMessage.getContentDisplay().length() > 42
                    ? referenceMessage.getContentDisplay().substring(0, 42) + "..."
                    : referenceMessage.getContentDisplay());

            reference.appendChild(referenceContent);
        }

        if (referenceMessage.getContentDisplay().isBlank() && !referenceMessage.getAttachments().isEmpty()) {
            Element attachment = document.createElement("em");
            attachment.html("Click to see attachment");

            reference.appendChild(attachment);
        }

        messageGroup.appendChild(referenceSymbol);
        messageGroup.appendChild(reference);
    }

    private static void handleSlashCommands(Document document, Message message, Element messageGroup) {
        // Referenced message
        var interaction = message.getInteraction();

        // create symbol
        Element referenceSymbol = document.createElement("div");
        referenceSymbol.addClass("chatlog__reference-symbol");

        // create reference
        Element reference = document.createElement("div");
        reference.addClass("chatlog__reference");

        User author = interaction.getUser();

        Element avatar = document.createElement("img");
        avatar.addClass("chatlog__reference-avatar");
        avatar.attr("src", author.getEffectiveAvatarUrl());
        avatar.attr("alt", "Avatar");
        avatar.attr("loading", "lazy");

        Element name = document.createElement("span");
        name.addClass("chatlog__reference-name");
        name.html(author.getName());

        reference.appendChild(avatar);
        reference.appendChild(name);

        reference.append("<span>used <b>/" + interaction.getName() + "</b></span>");

        messageGroup.appendChild(referenceSymbol);
        messageGroup.appendChild(reference);
    }

    private static void handleInteractionComponents(Document document, Message message, Element messageGroup) {
        for (ActionRow row : message.getActionRows()) {
            Element components = document.createElement("div");
            components.attr("style", "flex-direction: row; display: flex; margin-top: .5em");

            for (Component component : row.getComponents()) {

                // Buttons
                if (component instanceof Button button) {
                    Element buttonElement = document.createElement("div");
                    buttonElement.addClass("chatlog__interaction-button");
                    buttonElement.addClass("chatlog__interaction-button--style-" +
                            button.getStyle().name().toLowerCase());

                    if (button.getEmoji() != null) {
                        if (button.getEmoji().getType() == Emoji.Type.CUSTOM) {
                            Element customEmoji = document.createElement("img");
                            customEmoji.addClass("chatlog__interaction-button--emoji");
                            customEmoji.attr("src", button.getEmoji().asCustom().getImageUrl());

                            buttonElement.appendChild(customEmoji);
                        } else {
                            Element unicodeEmoji = document.createElement("span");
                            unicodeEmoji.addClass("chatlog__interaction-button--emoji");
                            unicodeEmoji.html(button.getEmoji().asUnicode().getName());

                            buttonElement.appendChild(unicodeEmoji);
                        }
                    }
                    if (!button.getLabel().isBlank()) {
                        Element label = document.createElement("span");
                        label.html(button.getLabel());

                        buttonElement.appendChild(label);
                    }
                    components.appendChild(buttonElement);
                }

                // SelectMenus
                if (component instanceof SelectMenu selectMenu) {
                    Element menuContainer = document.createElement("div");
                    menuContainer.addClass("chatlog__interaction-menu-container");

                    Element menuElement = document.createElement("div");
                    menuElement.addClass("chatlog__interaction-menu");

                    Element placeholder = document.createElement("span");
                    placeholder.html(selectMenu.getPlaceholder() == null ?
                            "Select an option" : selectMenu.getPlaceholder());

                    menuElement.appendChild(placeholder);

                    Element icon = document.createElement("div");
                    icon.addClass("chatlog__interaction-menu-icon");

                    Element iconElement = document.createElement("svg");
                    iconElement.attr("width", "24");
                    iconElement.attr("height", "24");
                    iconElement.attr("viewBox", "0 0 24 24");
                    iconElement.html(
                            "<path fill=\"currentColor\" d=\"M16.59 8.59003L12 13.17L7.41 8.59003L6 10L12 16L18 10L16.59 8.59003Z\"></path>");
                    icon.appendChild(iconElement);

                    menuElement.appendChild(icon);
                    menuContainer.appendChild(menuElement);
                    components.appendChild(menuContainer);
                }

            }
            messageGroup.appendChild(components);
        }
    }

    private static void handlePinnedMessages(Document document, Message message, Element messageGroup) {
        Element pinContainer = document.createElement("div");
        pinContainer.attr("style", "display: flex; place-items: center; justify-content: center;");

        Element pinSymbol = document.createElement("div");
        pinSymbol.attr("style", "background-image: url('https://svgshare.com/i/vci.svg'); margin-right: 1.6em; margin-left: 14px");
        pinSymbol.addClass("chatlog__reference-avatar");

        pinContainer.appendChild(pinSymbol);

        Element pinned = document.createElement("div");
        pinned.addClass("chatlog__messages");
        pinned.html("<b style='color:white'>" + message.getAuthor().getName() + "</b> pinned a message to this channel.");

        messageGroup.appendChild(pinContainer);
        messageGroup.appendChild(pinned);
    }
}
