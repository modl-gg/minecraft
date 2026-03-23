package gg.modl.minecraft.core.impl.menus;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ReportData {
    private String reason, chatLog, details;
    private boolean chatReport;
    private boolean replayCapture;
    private boolean attachReplay;

    public ReportData(String reason, boolean chatReport) {
        this.reason = reason;
        this.chatReport = chatReport;
    }
}
