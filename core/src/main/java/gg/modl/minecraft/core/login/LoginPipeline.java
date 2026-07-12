package gg.modl.minecraft.core.login;

import gg.modl.minecraft.core.cache.LoginCache;
import gg.modl.minecraft.core.session.PlayerSessionService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class LoginPipeline {
    private final LoginService loginService;
    private final LoginCache loginCache;
    private final PlayerSessionService playerSessionService;
}
