package org.itzmr.moderatio.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing and sending messages using MiniMessage and legacy color codes.
 */
public final class MessageUtils {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    private MessageUtils() {
    }

    /**
     * Parses a string containing MiniMessage and/or legacy '&' color codes into a Component.
     *
     * @param input the input string
     * @return the parsed Component
     */
    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        String converted = convertLegacyToMiniMessage(input);
        return MINI_MESSAGE.deserialize(converted);
    }

    /**
     * Parses a string with placeholders into a Component.
     *
     * @param input        the input string
     * @param placeholders map of placeholders to replace
     * @return the parsed Component
     */
    public static Component parse(String input, Map<String, String> placeholders) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        String replaced = input;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                replaced = replaced.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
            }
        }
        return parse(replaced);
    }

    /**
     * Sends a parsed message to a CommandSender.
     *
     * @param sender  the receiver
     * @param message the message string
     */
    public static void send(CommandSender sender, String message) {
        if (sender != null && message != null && !message.isEmpty()) {
            sender.sendMessage(parse(message));
        }
    }

    /**
     * Sends a parsed message with placeholders to a CommandSender.
     *
     * @param sender       the receiver
     * @param message      the message string
     * @param placeholders map of placeholders
     */
    public static void send(CommandSender sender, String message, Map<String, String> placeholders) {
        if (sender != null && message != null && !message.isEmpty()) {
            sender.sendMessage(parse(message, placeholders));
        }
    }

    /**
     * Sends a Component directly to a CommandSender.
     *
     * @param sender    the receiver
     * @param component the component
     */
    public static void send(CommandSender sender, Component component) {
        if (sender != null && component != null) {
            sender.sendMessage(component);
        }
    }

    /**
     * Converts a Component to plain text.
     *
     * @param component the component
     * @return plain text string
     */
    public static String toPlainText(Component component) {
        if (component == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * Translates legacy color codes (e.g. &c, &l, &#123456) into MiniMessage tags.
     */
    private static String convertLegacyToMiniMessage(String text) {
        // Replace &#RRGGBB hex codes
        Matcher hexMatcher = HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (hexMatcher.find()) {
            hexMatcher.appendReplacement(sb, "<#" + hexMatcher.group(1) + ">");
        }
        hexMatcher.appendTail(sb);
        text = sb.toString();

        // Replace standard single-char codes
        Matcher legacyMatcher = LEGACY_PATTERN.matcher(text);
        sb = new StringBuilder();
        while (legacyMatcher.find()) {
            char code = Character.toLowerCase(legacyMatcher.group(1).charAt(0));
            String tag = switch (code) {
                case '0' -> "<black>";
                case '1' -> "<dark_blue>";
                case '2' -> "<dark_green>";
                case '3' -> "<dark_aqua>";
                case '4' -> "<dark_red>";
                case '5' -> "<dark_purple>";
                case '6' -> "<gold>";
                case '7' -> "<gray>";
                case '8' -> "<dark_gray>";
                case '9' -> "<blue>";
                case 'a' -> "<green>";
                case 'b' -> "<aqua>";
                case 'c' -> "<red>";
                case 'd' -> "<light_purple>";
                case 'e' -> "<yellow>";
                case 'f' -> "<white>";
                case 'k' -> "<obfuscated>";
                case 'l' -> "<bold>";
                case 'm' -> "<strikethrough>";
                case 'n' -> "<underlined>";
                case 'o' -> "<italic>";
                case 'r' -> "<reset>";
                default -> "";
            };
            legacyMatcher.appendReplacement(sb, Matcher.quoteReplacement(tag));
        }
        legacyMatcher.appendTail(sb);
        return sb.toString();
    }
}
