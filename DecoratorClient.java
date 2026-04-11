public abstract class DecoratorClient implements ScoreClient {
    protected final ScoreClient client;

    protected DecoratorClient(ScoreClient client) {
        this.client = client;
    }

    @Override
    public abstract double score(String cpf) throws Exception;
}