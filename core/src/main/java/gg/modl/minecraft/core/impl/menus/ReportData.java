package gg.modl.minecraft.core.impl.menus;

import lombok.Getter;
import lombok.Setter;

/**
 * Mutable state object passed between report menu steps.
 * Holds the accumulated data for a report as the player navigates through the GUI flow.
 */
@Getter
@Setter
public class ReportData {
    private String reason;
    private boolean chatReport;
    private String chatLog;
    private String details;

    public ReportData(String reason, boolean chatReport) {
        this.reason = reason;
        this.chatReport = chatReport;
    }
}
