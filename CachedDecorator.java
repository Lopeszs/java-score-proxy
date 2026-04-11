import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CachedDecorator extends DecoratorClient {

    private final Map<String, Double> cache = new ConcurrentHashMap<>();

    public CachedDecorator(ScoreClient client) {
        super(client);
    }

    @Override
    public double score(String cpf) throws Exception {
        if (cache.containsKey(cpf)) {
            return cache.get(cpf);
        }
        double s = client.score(cpf);
        cache.put(cpf, s);
        return s;
    }
}