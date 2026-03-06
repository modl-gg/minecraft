// Credit elytrium's java-commons, this code is AGPL code. https://github.com/Elytrium/java-commons/blob/master/LICENSE
// Would just shade their repo, however it is not available/offline for unknown reasons.

package gg.modl.minecraft.core.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Placeholders {

    private static final Pattern EXACTLY_MATCHES = Pattern.compile("^\\{(?!_)[A-Z\\d_]+(?<!_)}$");
    private static final Pattern LOWERCASE = Pattern.compile("^(?!-)[a-z\\d-]+(?<!-)$");
    private static final Pattern UPPERCASE = Pattern.compile("^(?!_)[A-Z\\d_]+(?<!_)$");

    static final Map<Integer, String[]> placeholders = new HashMap<>();

    public static int addPlaceholders(Object value, String... placeholders) {
        int hashCode = System.identityHashCode(value);
        Placeholders.placeholders.put(hashCode, Stream.of(placeholders).map(Placeholders::toPlaceholderName).toArray(String[]::new));
        return hashCode;
    }

    private static String toPlaceholderName(String name) {
        if (EXACTLY_MATCHES.matcher(name).matches()) return name;
        else if (LOWERCASE.matcher(name).matches()) return '{' + name.toUpperCase(Locale.ROOT).replace('-', '_') + '}';
        else if (UPPERCASE.matcher(name).matches()) return '{' + name + '}';
        else throw new IllegalStateException("Invalid placeholder: " + name);
    }

}
