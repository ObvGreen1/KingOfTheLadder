package me.obvgreen.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * The single place a MiniMessage string becomes a component.
 *
 * <p>Every user-facing string in KotL — chat lines, item names, titles, broadcasts, dialog
 * labels — is authored as MiniMessage, in {@code config.yml} or in code. Keeping the
 * deserialiser here means the string dialect is decided once instead of being re-invented by
 * whichever package happens to own a manager. Dialog authors normally go through
 * {@code DialogView.text}, which delegates here.</p>
 */
public final class Text {

    private Text() {
    }

    /**
     * Deserialises a MiniMessage string.
     *
     * @param miniMessage the MiniMessage source, e.g. {@code <gold><bold>YOU ARE KING}
     * @return the parsed component
     */
    public static Component of(String miniMessage) {
        return MiniMessage.miniMessage().deserialize(miniMessage);
    }
}
