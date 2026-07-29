package gg.modl.minecraft.core.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class StubHttpConnection extends HttpURLConnection {
    private final int code;
    private final String body;
    private final IOException throwOnConnect;

    public StubHttpConnection(URL url, int code, String body, IOException throwOnConnect) {
        super(url);
        this.code = code;
        this.body = body;
        this.throwOnConnect = throwOnConnect;
    }

    private boolean isSuccessful() {
        return code >= 200 && code < 300;
    }

    @Override
    public void connect() throws IOException {
        if (throwOnConnect != null) throw throwOnConnect;
    }

    @Override
    public void disconnect() {
    }

    @Override
    public boolean usingProxy() {
        return false;
    }

    @Override
    public int getResponseCode() throws IOException {
        if (throwOnConnect != null) throw throwOnConnect;
        return code;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (throwOnConnect != null) throw throwOnConnect;
        if (!isSuccessful()) throw new IOException("HTTP " + code);
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public InputStream getErrorStream() {
        if (isSuccessful()) return null;
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }
}
