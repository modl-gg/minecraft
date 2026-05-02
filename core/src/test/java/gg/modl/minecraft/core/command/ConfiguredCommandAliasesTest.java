package gg.modl.minecraft.core.command;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredCommandAliasesTest {

    @Test
    void resolveCommandValuesUsesConfiguredAliasesAndDropsDisabledRoots() {
        Map<String, String> rawAliases = new LinkedHashMap<>();
        rawAliases.put("staffmode", "staffmode|modmode|sm|mm");
        rawAliases.put("pardon", "pardon|forgive");
        rawAliases.put("unban", "");
        rawAliases.put("unmute", "unmute");

        ConfiguredCommandAliases aliases = new ConfiguredCommandAliases(rawAliases);

        assertArrayEquals(
            new String[] { "staffmode", "modmode", "sm", "mm" },
            aliases.resolveCommandValues("staffmode")
        );
        assertArrayEquals(
            new String[] { "pardon", "forgive", "unmute" },
            aliases.resolveCommandValues("pardon", "unban", "unmute")
        );
    }

    @Test
    void reportsEnabledStateAndPrimaryAlias() {
        Map<String, String> rawAliases = new LinkedHashMap<>();
        rawAliases.put("punishment_action", "modl:punishment-action");
        rawAliases.put("notes", "");

        ConfiguredCommandAliases aliases = new ConfiguredCommandAliases(rawAliases);

        assertEquals("modl:punishment-action", aliases.primaryAlias("punishment_action"));
        assertTrue(aliases.isEnabled("punishment_action"));
        assertFalse(aliases.isEnabled("notes"));
        assertTrue(aliases.anyEnabled("notes", "punishment_action"));
        assertFalse(aliases.anyEnabled("notes"));
    }
}
