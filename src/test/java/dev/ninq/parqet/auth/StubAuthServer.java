/*
 * Copyright 2026 the parqet4j authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ninq.parqet.auth;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A minimal stand-in for the Parqet token endpoint: answers every request with the same canned response and remembers the last form body it
 * was posted.
 */
final class StubAuthServer implements AutoCloseable {

    private final ServerSocket socket;
    private final AtomicReference<String> body = new AtomicReference<>("");
    private final AtomicReference<String> path = new AtomicReference<>("");
    private final AtomicInteger requests = new AtomicInteger();

    private volatile int status = 200;
    private volatile String response = "{}";

    StubAuthServer() {
        try {
            socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the stub auth server", e);
        }
        Thread.ofVirtual().name("stub-auth").start(this::acceptLoop);
    }

    void respondWith(int status, String response) {
        this.status = status;
        this.response = response;
    }

    String lastBody() {
        return body.get();
    }

    String lastPath() {
        return path.get();
    }

    int requestCount() {
        return requests.get();
    }

    URI baseUri() {
        return URI.create("http://127.0.0.1:" + socket.getLocalPort());
    }

    private void acceptLoop() {
        while (!socket.isClosed()) {
            try (var connection = socket.accept()) {
                serve(connection);
            } catch (IOException closed) {
                return;
            }
        }
    }

    private void serve(Socket connection) throws IOException {
        var in = new BufferedInputStream(connection.getInputStream());
        var requestLine = readLine(in);
        if (requestLine == null || requestLine.isEmpty()) {
            return;
        }
        path.set(requestLine.split(" ")[1]);

        var headers = new ArrayList<String>();
        String header;
        while ((header = readLine(in)) != null && !header.isEmpty()) {
            headers.add(header);
        }
        var length = headers.stream()
                .filter(h -> h.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:"))
                .map(h -> Integer.parseInt(h.substring(h.indexOf(':') + 1).trim()))
                .findFirst()
                .orElse(0);
        body.set(new String(in.readNBytes(length), StandardCharsets.UTF_8));
        requests.incrementAndGet();

        var payload = response.getBytes(StandardCharsets.UTF_8);
        var head = "HTTP/1.1 " + status + " X\r\nContent-Type: application/json\r\nContent-Length: " + payload.length
                + "\r\nConnection: close\r\n\r\n";
        connection.getOutputStream().write(head.getBytes(StandardCharsets.US_ASCII));
        connection.getOutputStream().write(payload);
        connection.getOutputStream().flush();
    }

    private static String readLine(InputStream in) throws IOException {
        var line = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                line.append((char) c);
            }
        }
        return c == -1 && line.isEmpty() ? null : line.toString();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // tearing down a fixture; nothing useful to do
        }
    }
}
