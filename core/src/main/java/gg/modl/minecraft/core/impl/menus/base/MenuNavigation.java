package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;

import java.util.function.Consumer;

final class MenuNavigation {
    private MenuNavigation() {}

    static void handleBack(Click click, Consumer<CirrusPlayerWrapper> backAction) {
        click.clickedMenu().close();
        if (backAction != null) backAction.accept(click.player());
    }
}
