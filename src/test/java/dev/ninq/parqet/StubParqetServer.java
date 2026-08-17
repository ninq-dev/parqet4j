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
package dev.ninq.parqet;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A throwaway HTTP server that answers with whatever the test queued and records what it was asked.
 * <p>
 * Speaking HTTP over a real socket — rather than mocking {@code HttpClient} — keeps the tests honest about header names, query encoding and
 * status handling, the parts most likely to drift. It is deliberately built on {@code java.net} alone: {@code com.sun.net.httpserver} lives
 * in a module this one has no reason to read, and adding that edge just for tests complicates every build.
 * <p>
 * Only what these tests need is implemented: one request per connection, {@code Content-Length} bodies, and {@code Connection: close}.
 */
final class StubParqetServer implements AutoCloseable {

    private final ServerSocket socket;
    private final Thread acceptor;
    private final Deque<Response> responses = new ArrayDeque<>();
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();

    StubParqetServer() {
        try {
            socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the stub server", e);
        }
        acceptor = Thread.ofVirtual().name("stub-parqet").start(this::acceptLoop);
    }

    /** A canned answer. */
    record Response(int status, String body, Map<String, String> headers) {

        static Response ok(String body) {
            return new Response(200, body, Map.of());
        }

        static Response of(int status, String body) {
            return new Response(status, body, Map.of());
        }
    }

    /** What the client actually sent; path and query stay percent-encoded. */
    record Recorded(String method, String path, String query, String authorization, String body) {
    }

    void enqueue(Response response) {
        synchronized (responses) {
            responses.addLast(response);
        }
    }

    void enqueueOk(String body) {
        enqueue(Response.ok(body));
    }

    List<Recorded> requests() {
        return List.copyOf(requests);
    }

    Recorded lastRequest() {
        return requests.getLast();
    }

    URI baseUri() {
        return URI.create("http://127.0.0.1:" + socket.getLocalPort());
    }

    private void acceptLoop() {
        while (!socket.isClosed()) {
            try (var connection = socket.accept()) {
                serve(connection);
            } catch (IOException closed) {
                return; // the socket was closed, or the client hung up; either way we are done here
            }
        }
    }

    private void serve(Socket connection) throws IOException {
        var in = new BufferedInputStream(connection.getInputStream());
        var requestLine = readLine(in);
        if (requestLine == null || requestLine.isEmpty()) {
            return;
        }
        var parts = requestLine.split(" ");
        var target = parts[1];
        var split = target.indexOf('?');

        var headers = new ArrayList<String>();
        String header;
        while ((header = readLine(in)) != null && !header.isEmpty()) {
            headers.add(header);
        }
        var body = readBody(in, contentLength(headers));

        requests.add(new Recorded(
                parts[0],
                split < 0 ? target : target.substring(0, split),
                split < 0 ? null : target.substring(split + 1),
                headerValue(headers, "authorization"),
                body));

        Response response;
        synchronized (responses) {
            response = responses.isEmpty() ? Response.of(500, "{\"message\":\"stub ran out of responses\"}") : responses.removeFirst();
        }
        write(connection.getOutputStream(), response);
    }

    private static void write(OutputStream out, Response response) throws IOException {
        var body = response.body().getBytes(StandardCharsets.UTF_8);
        var head = new StringBuilder("HTTP/1.1 ").append(response.status()).append(" X\r\n")
                .append("Content-Type: application/json\r\n")
                .append("Content-Length: ").append(body.length).append("\r\n")
                .append("Connection: close\r\n");
        response.headers().forEach((k, v) -> head.append(k).append(": ").append(v).append("\r\n"));
        head.append("\r\n");
        out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
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

    private static String readBody(InputStream in, int length) throws IOException {
        if (length <= 0) {
            return "";
        }
        return new String(in.readNBytes(length), StandardCharsets.UTF_8);
    }

    private static int contentLength(List<String> headers) {
        var value = headerValue(headers, "content-length");
        return value == null ? 0 : Integer.parseInt(value.trim());
    }

    private static String headerValue(List<String> headers, String name) {
        return headers.stream()
                .filter(h -> h.regionMatches(true, 0, name + ":", 0, name.length() + 1))
                .map(h -> h.substring(name.length() + 1).trim())
                .findFirst()
                .orElse(null);
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // nothing useful to do while tearing down a test fixture
        }
        acceptor.interrupt();
    }
}
