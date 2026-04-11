public class Main {
    public static void main(String[] args) throws Exception {
        ScoreClient client =
            new CachedDecorator(
                new ControlledDecorator(
                    new APIClient()
                )
            );

        for (int i = 0; i < 5; i++) {
            double score = client.score("218.422.170-89");
            System.out.println("Score " + i + ": " + score);
        }
    }
}