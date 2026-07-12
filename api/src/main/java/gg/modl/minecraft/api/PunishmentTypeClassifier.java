package gg.modl.minecraft.api;

public interface PunishmentTypeClassifier {
    int ORDINAL_KICK = 0;
    int ORDINAL_MUTE = 1;
    int ORDINAL_BAN = 2;
    int ORDINAL_SECURITY_BAN = 3;
    int ORDINAL_LINKED_BAN = 4;
    int ORDINAL_BLACKLIST = 5;

    boolean isPopulated();

    boolean isBan(int ordinal);

    boolean isMute(int ordinal);

    boolean isKick(int ordinal);
}
