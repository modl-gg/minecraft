package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.Click;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface MenuChrome {

    Map<Integer, CirrusItem> headerItems(boolean compact);

    void registerNavigation(BiConsumer<String, Consumer<Click>> registrar);
}
