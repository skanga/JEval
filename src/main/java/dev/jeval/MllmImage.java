package dev.jeval;

import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class MllmImage {
    private static final Map<String, MllmImage> REGISTRY = new ConcurrentHashMap<>();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\[DEEPEVAL:(?:IMAGE|PDF):(.*?)]");
    private final String id;
    private final String dataBase64;
    private final String mimeType;
    private final String url;
    private final boolean local;
    private final String filename;

    public MllmImage(String dataBase64, String mimeType) {
        if (dataBase64 == null || dataBase64.isBlank() || mimeType == null || mimeType.isBlank()) {
            throw new IllegalArgumentException("You must provide both dataBase64 and mimeType to create an MLLMImage.");
        }
        this.id = randomId();
        this.url = null;
        this.local = false;
        this.filename = null;
        this.mimeType = mimeType;
        this.dataBase64 = dataBase64;
        REGISTRY.put(id, this);
    }

    public MllmImage(String url) {
        this(url, randomId(), true);
    }

    private MllmImage(String url, String id, boolean register) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("You must provide either a url or both dataBase64 and mimeType to create an MLLMImage.");
        }
        this.id = id;
        this.url = url;
        var path = localPath(url);
        if (path != null) {
            local = true;
            filename = path.getFileName().toString();
            mimeType = mimeType(path);
            dataBase64 = loadBase64(path);
        } else {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw new IllegalArgumentException("Invalid remote URL format: " + url);
            }
            local = false;
            filename = remoteFilename(url);
            mimeType = remoteMimeType(filename);
            dataBase64 = null;
        }
        if (register) {
            REGISTRY.put(id, this);
        }
    }

    public String dataBase64() {
        return dataBase64;
    }

    public String mimeType() {
        return mimeType;
    }

    public String url() {
        return url;
    }

    public boolean local() {
        return local;
    }

    public String filename() {
        return filename;
    }

    public String asDataUri() {
        if (dataBase64 == null || dataBase64.isBlank() || mimeType == null || mimeType.isBlank()) {
            return null;
        }
        return "data:" + mimeType + ";base64," + dataBase64;
    }

    public static List<Object> parseMultimodalString(String value) {
        var matcher = PLACEHOLDER.matcher(value);
        var parts = new ArrayList<>();
        var lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                parts.add(value.substring(lastEnd, matcher.start()));
            }
            var imageId = matcher.group(1);
            var image = REGISTRY.get(imageId);
            if (image == null) {
                image = new MllmImage(imageId, imageId, false);
                REGISTRY.put(imageId, image);
            }
            parts.add(image);
            lastEnd = matcher.end();
        }
        if (lastEnd < value.length()) {
            parts.add(value.substring(lastEnd));
        }
        return parts;
    }

    static MllmImage registeredImage(String id) {
        return REGISTRY.get(id);
    }

    @Override
    public String toString() {
        return "[DEEPEVAL:" + ("application/pdf".equals(mimeType) ? "PDF" : "IMAGE") + ":" + id + "]";
    }

    private static Path localPath(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return null;
        }
        if (url.startsWith("file:")) {
            var filePath = Path.of(URI.create(url));
            return Files.exists(filePath) ? filePath : null;
        }
        try {
            var path = Path.of(url);
            return Files.exists(path) ? path : null;
        } catch (InvalidPathException ignored) {
            return null;
        }
    }

    private static String loadBase64(Path path) {
        try {
            return Base64.getEncoder().encodeToString(Files.readAllBytes(path));
        } catch (IOException error) {
            throw new IllegalArgumentException("Image file could not be read: " + path, error);
        }
    }

    private static String mimeType(Path path) {
        try {
            var detected = Files.probeContentType(path);
            return detected == null ? "image/jpeg" : detected;
        } catch (IOException ignored) {
            return "image/jpeg";
        }
    }

    private static String mimeType(String filename) {
        if (filename.endsWith(".png")) {
            return "image/png";
        }
        if (filename.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (filename.endsWith(".gif")) {
            return "image/gif";
        }
        if (filename.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private static String remoteMimeType(String filename) {
        return URLConnection.guessContentTypeFromName(filename);
    }

    private static String remoteFilename(String url) {
        var path = URI.create(url).getRawPath();
        var slash = path.lastIndexOf('/');
        return slash == -1 ? path : path.substring(slash + 1);
    }

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
