package gg.modl.minecraft.api.http.response;

public abstract class StatusResponse {
    public abstract int getStatus();

    public boolean isSuccess() {
        int status = getStatus();
        return status >= 200 && status < 300;
    }
}
