import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ProxyMain {

    public static void main(String[] args) throws Exception {
        ScoreClient upstream = new CachedDecorator(new APIClient());
        ProxyService proxy = new ProxyService(upstream, 100);
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/proxy/score", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Método não permitido");
                return;
            }

            String cpf = extractCpfFromQuery(exchange);
            if (cpf == null || cpf.isBlank()) {
                sendText(exchange, 400, "Parâmetro 'cpf' é obrigatório");
                return;
            }

            CompletableFuture<Double> future = proxy.requestScore(cpf);

            try {
                double score = future.get(5, TimeUnit.SECONDS);
                String json = "{\"cpf\":\"" + cpf + "\",\"score\":" + score + "}";
                sendJson(exchange, 200, json);
            } catch (Exception e) {
                sendText(exchange, 503, "Falha ao obter score: " + e.getMessage());
            }
        });

        server.createContext("/metrics", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Método não permitido");
                return;
            }

            String json = "{"
                    + "\"queueSize\":" + proxy.getQueueSize() + ","
                    + "\"enqueued\":" + proxy.getEnqueued() + ","
                    + "\"dropped\":" + proxy.getDropped() + ","
                    + "\"upstreamCalls\":" + proxy.getUpstreamCalls()
                    + "}";

            sendJson(exchange, 200, json);
        });

        server.createContext("/health", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Método não permitido");
                return;
            }

            String json = "{"
                    + "\"status\":\"UP\","
                    + "\"queueSize\":" + proxy.getQueueSize()
                    + "}";

            sendJson(exchange, 200, json);
        });

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Proxy rodando em http://localhost:8080");
    }

    private static String extractCpfFromQuery(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return null;

        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals("cpf")) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name());
            }
        }
        return null;
    }

    private static void sendText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}