import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class APIClient implements ScoreClient {

    private final HttpClient httpClient;

    public APIClient() {
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public double score(String cpf) throws Exception {
        // Equivalente a process.env.CLIENT_ID
        String clientId = System.getenv("CLIENT_ID");
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Variável de ambiente CLIENT_ID não configurada.");
        }

        String url = "https://score.hsborges.dev/api/score?cpf=" +
                URLEncoder.encode(cpf, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("client-id", clientId)   // mesmo header do TS
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Falha ao chamar a API de score: " + e.getMessage(), e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new RuntimeException("API request failed with status " + status);
        }

        String body = response.body();
        double score = extractScoreFromJson(body); // pega o campo score do JSON
        return score;
    }

    private double extractScoreFromJson(String json) {
        // Espera algo como {"score":123}
        Pattern pattern = Pattern.compile("\"score\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new RuntimeException("Resposta da API não contém campo 'score': " + json);
        }
        return Double.parseDouble(matcher.group(1));
    }
}