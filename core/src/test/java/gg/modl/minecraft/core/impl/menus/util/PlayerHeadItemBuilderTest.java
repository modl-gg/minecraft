package gg.modl.minecraft.core.impl.menus.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PlayerHeadItemBuilderTest {
    @Test
    void renderLoreLines_tolerates_missing_ip_geo_fields() {
        Map<String, String> vars = new HashMap<>();
        vars.put("region", null);
        vars.put("country", null);

        List<String> rendered = assertDoesNotThrow(() -> PlayerHeadItemBuilder.renderLoreLines(
            List.of("&7Region: &f{region} (Country: {country})"),
            vars
        ));

        assertEquals(List.of("&7Region: &fUnknown (Country: Unknown)"), rendered);
    }
}
