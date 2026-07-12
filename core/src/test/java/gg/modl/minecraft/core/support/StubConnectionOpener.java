package gg.modl.minecraft.core.support;

import gg.modl.minecraft.core.util.HttpConnectionOpener;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class StubConnectionOpener implements HttpConnectionOpener {
    private final AtomicInteger openCount = new AtomicInteger();
    private final AtomicReference<URL> lastUrl = new AtomicReference<>();
    private final AtomicReference<Thread> lastThread = new AtomicReference<>();
    private volatile int responseCode = HttpURLConnection.HTTP_OK;
    private volatile String responseBody = "{}";
    private volatile IOException throwOnConnect;

    public StubConnectionOpener respondWith(int code, String body) {
        this.responseCode = code;
        this.responseBody = body;
        this.throwOnConnect = null;
        return this;
    }

    public StubConnectionOpener throwOnConnect(IOException exception) {
        this.throwOnConnect = exception;
        return this;
    }

    @Override
    public HttpURLConnection open(URL url) {
        openCount.incrementAndGet();
        lastUrl.set(url);
        lastThread.set(Thread.currentThread());
        return new StubHttpConnection(url, responseCode, responseBody, throwOnConnect);
    }

    public int openCount() {
        return openCount.get();
    }

    public URL lastUrl() {
        return lastUrl.get();
    }

    public Thread lastConnectionThread() {
        return lastThread.get();
    }
}
