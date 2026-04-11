import java.util.concurrent.CompletableFuture;

public class ProxyRequest {
    public final String cpf;
    public final CompletableFuture<Double> future;

    public ProxyRequest(String cpf) {
        this.cpf = cpf;
        this.future = new CompletableFuture<>();
    }
}