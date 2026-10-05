import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Calls the Python Flask ML microservice to check doubt text for inappropriate content
 * before it's saved. Uses Java's built-in HttpClient (no external library needed).
 */
public class MLClient {
    private static final String ML_SERVICE_URL = System.getProperty("classpulse.mlUrl", "http://127.0.0.1:5000/check");
    private final HttpClient client;

    public MLClient() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public static class ModerationResult {
        public final boolean flagged;
        public final double confidence;
        public final boolean serviceAvailable;

        public ModerationResult(boolean flagged, double confidence, boolean serviceAvailable) {
            this.flagged = flagged;
            this.confidence = confidence;
            this.serviceAvailable = serviceAvailable;
        }
    }

    /**
     * Sends text to the ML service. If the service is unreachable (e.g. not running),
     * fails open (does not block the doubt) rather than making the whole app unusable -
     * but flags serviceAvailable=false so the caller can log/warn about it.
     */
    private static final java.util.regex.Pattern BLOCKLIST = java.util.regex.Pattern.compile("(?i)\\b(idiot|stupid|moron|shut\\s+up|worthless|dumbass|fuck|shit|bitch|asshole|bastard|retard)\\b");
    public ModerationResult check(String text) {
        if (BLOCKLIST.matcher(text).find()) return new ModerationResult(true, 1.0, false);
        try {
            String body = "{" + JsonUtil.field("text", text) + "}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ML_SERVICE_URL))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.out.println("ML service returned status " + response.statusCode());
                return new ModerationResult(false, 0.0, false);
            }

            String json = response.body();
            java.util.Map<String,String> fields = SimpleJsonParser.parseFlatObject(json);
            if (!"true".equals(fields.get("flagged")) && !"false".equals(fields.get("flagged"))) throw new IllegalArgumentException("Invalid moderation response");
            boolean flagged = Boolean.parseBoolean(fields.get("flagged"));
            double confidence = Double.parseDouble(fields.get("confidence"));
            if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) throw new IllegalArgumentException("Invalid confidence");
            return new ModerationResult(flagged, confidence, true);

        } catch (Exception e) {
            System.out.println("ML service unreachable: " + e.getMessage());
            return new ModerationResult(false, 0.0, false);
        }
    }

}
