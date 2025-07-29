package gg.modl.minecraft.api.http;

/**
 * Exception thrown when the panel returns a 502 Bad Gateway error,
 * indicating it's likely restarting or temporarily unavailable.
 */
public class PanelUnavailableException extends RuntimeException {
    private final int statusCode;
    private final String endpoint;
    
    public PanelUnavailableException(int statusCode, String endpoint, String message) {
        super(message);
        this.statusCode = statusCode;
        this.endpoint = endpoint;
    }
    
    public PanelUnavailableException(int statusCode, String endpoint, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.endpoint = endpoint;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public String getEndpoint() {
        return endpoint;
    }
    
    /**
     * Check if this is a 502 Bad Gateway error (panel restart)
     */
    public boolean isPanelRestarting() {
        return statusCode == 502;
    }
}