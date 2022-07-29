package io.fairyproject.gradle.util;

import com.google.gson.*;
import io.fairyproject.gradle.FairyPlugin;
import io.fairyproject.gradle.IDEDependencyLookup;
import io.fairyproject.shared.FairyVersion;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@UtilityClass
public class MavenUtil {

    private final String VERSION_URL = "https://repo.imanity.dev/api/maven/latest/version/imanity-libraries/";
    private final String ITEM_URL = "https://repo.imanity.dev/api/maven/details/imanity-libraries/";
    private final long MAX_CACHE_TIMESTAMP = TimeUnit.MINUTES.toMillis(30);
    private JsonObject CACHE;
    private Path CACHE_FILE;

    @SneakyThrows
    public void start(boolean force, File buildFile) {
        final Path buildDir = FairyPlugin.INSTANCE.getProject().getBuildDir().toPath();

        if (!Files.exists(buildDir)) {
            Files.createDirectories(buildDir);
        }

        final long timestamp = System.currentTimeMillis();
        final Path file = buildDir.resolve("fairy$cache.json");
        final Path hashFile = buildDir.resolve("fairy$cache.sha256");

        byte[] hash = createDigest().digest(Files.readAllBytes(buildFile.toPath()));
        byte[] cachedHash = null;
        boolean recreate = true;

        if (Files.exists(hashFile)) {
            cachedHash = Files.readAllBytes(hashFile);
        }

        CACHE_FILE = file;
        if (!force && Files.exists(file)) {
            try {
                CACHE = FairyPlugin.GSON.fromJson(new String(Files.readAllBytes(file)), JsonObject.class);

                if (CACHE.has("timestamp")
                        && timestamp - CACHE.get("timestamp").getAsLong() < MAX_CACHE_TIMESTAMP
                        && cachedHash != null
                        && Arrays.equals(cachedHash, hash)) {
                    recreate = false;
                    System.out.println("Loaded dependency cache from " + file + ".");
                }
            } catch (Throwable throwable) {
                CACHE = null;
                recreate = true;
                System.out.println("An error occurs while reading cache: " + throwable.getClass().getSimpleName() + " : " + throwable.getMessage());
            }
        }

        if (recreate) {
            CACHE = new JsonObject();
            CACHE.addProperty("timestamp", timestamp);

            Files.write(hashFile, hash, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        }
    }

    @SneakyThrows
    public void end() {
        if (!Files.exists(CACHE_FILE.getParent())) {
            Files.createDirectories(CACHE_FILE.getParent());
        }
        Files.write(CACHE_FILE, FairyPlugin.GSON.toJson(CACHE).getBytes(), StandardOpenOption.CREATE);
    }

    public String getLatest(String module) {
        if (FairyPlugin.IS_IN_IDE) {
            final String identityPath = IDEDependencyLookup.getIdentityPath(module);

            return (String) FairyPlugin.INSTANCE.getProject().project(identityPath).getVersion();
        }
        String latest = getLatest("io.fairyproject", module);
        if (FairyVersion.SNAPSHOT_INDIVIDUAL_PATTERN.matcher(latest).find()) {
            latest = latest.split("-")[0] + "-SNAPSHOT";
        }
        return latest;
    }

    public String getLatest(String group, String artifact) {
        return cacheable("getLatest", group + "|" + artifact, String.class, () -> {
            final URL url = new URL(VERSION_URL + group.replace(".", "/") + "/" + artifact);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.setRequestMethod("GET");
            connection.addRequestProperty("User-Agent", userAgent());
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            final int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                try (InputStream in = connection.getInputStream()) {
                    JsonObject jsonObject = new Gson().fromJson(new InputStreamReader(in), JsonObject.class);
                    return jsonObject.get("version").getAsString();
                }
            }

            throw new IllegalStateException("Unable to find latest version of " + group + ":" + artifact);
        });
    }

    public boolean isExistingModule(String module, String version) {
        if (FairyPlugin.IS_IN_IDE) {
            return IDEDependencyLookup.getIdentityPath(module) != null;
        }
        return cacheable("isExistingModule", module + "|" + version, Boolean.class, () -> {
            try {
                final URL url = new URL(ITEM_URL + "io/fairyproject/" + module + "/" + version);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.setRequestMethod("GET");
                connection.addRequestProperty("User-Agent", userAgent());

                if (connection.getResponseCode() == 200) {
                    final JsonArray jsonObject = new Gson().fromJson(new InputStreamReader(connection.getInputStream()), JsonArray.class);
                    return jsonObject.size() > 0;
                }
                return false;
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                throw throwable;
            }
        });
    }

    private String userAgent() {
        String fairyVersion = FairyPlugin.INSTANCE.getExtension()
                .getFairyVersion()
                .get();

        return String.format("Fairy/%s", fairyVersion);
    }

    private <R> R cacheable(String type, String key, Class<R> classType, ThrowingSupplier<R> valueSupplier) {
        return new Cacheable<R>(type, key, valueSupplier) {
            @Override
            public R toValue(JsonElement jsonElement) {
                if (classType == String.class) {
                    return classType.cast(jsonElement.getAsString());
                } else if (classType == Boolean.class || classType == boolean.class) {
                    return classType.cast(jsonElement.getAsBoolean());
                }
                return null;
            }

            @Override
            public JsonElement toJson(R o) {
                if (classType == String.class) {
                    return new JsonPrimitive((String) o);
                } else if (classType == Boolean.class || classType == boolean.class) {
                    return new JsonPrimitive((Boolean) o);
                }
                return null;
            }
        }.get();
    }

    @AllArgsConstructor
    private static abstract class Cacheable<R> {

        private String type;
        private String key;
        private ThrowingSupplier<R> valueSupplier;

        public R get() {
            JsonObject jsonObject;
            if (CACHE.has(type)) {
                jsonObject = CACHE.getAsJsonObject(type);

                if (jsonObject.has(key)) {
                    return this.toValue(jsonObject.get(key));
                }
            } else {
                jsonObject = new JsonObject();
                CACHE.add(type, jsonObject);
            }

            final R r;
            try {
                r = valueSupplier.accept();
            } catch (Throwable throwable) {
                SneakyThrow.sneaky(throwable);
                return null;
            }
            jsonObject.add(key, this.toJson(r));
            return r;
        }

        public abstract R toValue(JsonElement jsonElement);

        public abstract JsonElement toJson(R r);

    }

    private interface ThrowingSupplier<R> {
        R accept() throws Throwable;
    }

    public static MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            SneakyThrow.sneaky(e);
            return null;
        }
    }

}
