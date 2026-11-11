package com.fretka46.fDailyRewards.Utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Výchozí texty zamítnutí brány proti altům: projdou MiniMessage, mají hover, žádná červená, placeholdery nahrazené. */
class AntiAltGateMessagesTest {
    static final String PREFIX = "<#383838>[<#ff002e><bold>Survival</bold><#383838>] <#ffc700><bold>»<reset><gray> <#F4EBDD>";

    static void walk(Component c, List<Component> out) {
        out.add(c);
        for (Component ch : c.children()) walk(ch, out);
    }

    static void check(String msg) {
        assertFalse(msg.contains("{"), "nenahrazený placeholder: " + msg);
        assertFalse(msg.toLowerCase().contains("#ff002e") || msg.contains("<red>"), "červená jen v prefixu: " + msg);
        Component c = MiniMessage.miniMessage().deserialize(PREFIX + " " + msg);
        List<Component> all = new ArrayList<>();
        walk(c, all);
        assertTrue(all.stream().anyMatch(x -> x.hoverEvent() != null && x.hoverEvent().action() == HoverEvent.Action.SHOW_TEXT), "chybí hover");
        assertTrue(all.stream().noneMatch(x -> TextColor.color(0xff002e).equals(x.color())
                && !PlainTextComponentSerializer.plainText().serialize(x).contains("Survival")), "červená mimo prefix");
        String plain = PlainTextComponentSerializer.plainText().serialize(c);
        assertFalse(plain.contains("<"), "neinterpretovaná značka: " + plain);
    }

    @Test
    void ipLimitDefault() {
        check(AntiAltGate.DEFAULT_IP_LIMIT.replace("{max}", "2"));
    }

    @Test
    void playtimeDefault() {
        check(AntiAltGate.DEFAULT_PLAYTIME.replace("{minutes}", "30m").replace("{left}", "12m"));
    }
}
