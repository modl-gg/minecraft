package gg.modl.minecraft.core.impl.menus;

/**
 * Mutable state object passed between report menu steps.
 * Holds the accumulated data for a report as the player navigates through the GUI flow.
 */
public class ReportData {
    private String reason;
    private boolean chatReport;
    private String chatLog;
    private String details;

    public ReportData(String reason, boolean chatReport) {
        this.reason = reason;
        this.chatReport = chatReport;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean isChatReport() {
        return chatReport;
    }

    public void setChatReport(boolean chatReport) {
        this.chatReport = chatReport;
    }

    public String getChatLog() {
        return chatLog;
    }

    public void setChatLog(String chatLog) {
        this.chatLog = chatLog;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
