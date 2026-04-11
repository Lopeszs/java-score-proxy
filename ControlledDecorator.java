public class ControlledDecorator extends DecoratorClient {

    private long lastCallTimestamp = 0L;

    public ControlledDecorator(ScoreClient client) {
        super(client);
    }

    @Override
    public synchronized double score(String cpf) throws Exception {
        long now = System.currentTimeMillis();
        long diff = now - lastCallTimestamp;
        if (diff < 1000L) {
            Thread.sleep(1000L - diff);
        }
        double s = client.score(cpf);
        lastCallTimestamp = System.currentTimeMillis();
        return s;
    }
}