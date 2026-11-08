package de.tradeview.update;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** UTF-8 body that fails once it exceeds a size limit (BodyHandlers.limiting needs Java 25). */
final class LimitedBody implements HttpResponse.BodySubscriber<String> {
    private final int maxBytes;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final CompletableFuture<String> result = new CompletableFuture<>();
    private Flow.Subscription subscription;

    private LimitedBody(int maxBytes) { this.maxBytes = maxBytes; }

    static HttpResponse.BodyHandler<String> handler(int maxBytes) {
        return info -> new LimitedBody(maxBytes);
    }

    @Override public CompletionStage<String> getBody() { return result; }

    @Override public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(Long.MAX_VALUE);
    }

    @Override public void onNext(List<ByteBuffer> buffers) {
        if (result.isDone()) return;
        for (ByteBuffer buffer : buffers) {
            if (bytes.size() + buffer.remaining() > maxBytes) {
                subscription.cancel();
                result.completeExceptionally(new IOException("Response body exceeds " + maxBytes + " bytes"));
                return;
            }
            byte[] chunk = new byte[buffer.remaining()];
            buffer.get(chunk);
            bytes.write(chunk, 0, chunk.length);
        }
    }

    @Override public void onError(Throwable error) { result.completeExceptionally(error); }

    @Override public void onComplete() { result.complete(bytes.toString(StandardCharsets.UTF_8)); }
}
