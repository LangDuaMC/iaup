package net.langdua.iaup.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.Locale;
import java.util.UUID;

public final class PluginSettings {
    private final String uid;
    private final File outputFile;
    private final boolean updateItemsAdderConfig;
    private final boolean autoIareloadAfterManualUpload;
    private final String itemsAdderConfigPathOverride;
    private final boolean autoUploadOnPack;
    private final CacheBust cacheBust;
    private final S3 s3;

    private PluginSettings(String uid, File outputFile, boolean updateItemsAdderConfig,
                           boolean autoIareloadAfterManualUpload,
                           String itemsAdderConfigPathOverride, boolean autoUploadOnPack,
                           CacheBust cacheBust, S3 s3) {
        this.uid = uid;
        this.outputFile = outputFile;
        this.updateItemsAdderConfig = updateItemsAdderConfig;
        this.autoIareloadAfterManualUpload = autoIareloadAfterManualUpload;
        this.itemsAdderConfigPathOverride = itemsAdderConfigPathOverride;
        this.autoUploadOnPack = autoUploadOnPack;
        this.cacheBust = cacheBust;
        this.s3 = s3;
    }

    public static PluginSettings fromConfig(FileConfiguration cfg) {
        String uid = cfg.getString("global.uid", "").trim();
        if (uid.isEmpty()) {
            uid = UUID.randomUUID().toString();
            cfg.set("global.uid", uid);
        }

        File outputFile = new File(cfg.getString("global.output_file", "plugins/ItemsAdder/output/generated.zip"));
        boolean updateItemsAdderConfig = cfg.getBoolean("global.update_itemsadder_config", true);
        boolean autoIareloadAfterManualUpload = cfg.getBoolean("global.auto_iareload_after_manual_upload", false);
        String itemsAdderConfigPathOverride = cfg.getString("global.itemsadder_config_path", "").trim();
        boolean autoUploadOnPack = cfg.getBoolean("global.auto_upload_on_pack", true);

        CacheBust cacheBust = CacheBust.fromConfig(cfg);
        S3 s3 = S3.fromConfig(cfg);

        return new PluginSettings(uid, outputFile, updateItemsAdderConfig, autoIareloadAfterManualUpload, itemsAdderConfigPathOverride,
                autoUploadOnPack, cacheBust, s3);
    }

    public String uid() {
        return uid;
    }

    public File outputFile() {
        return outputFile;
    }

    public boolean updateItemsAdderConfig() {
        return updateItemsAdderConfig;
    }

    public boolean autoIareloadAfterManualUpload() {
        return autoIareloadAfterManualUpload;
    }

    public String itemsAdderConfigPathOverride() {
        return itemsAdderConfigPathOverride;
    }

    public boolean autoUploadOnPack() {
        return autoUploadOnPack;
    }

    public CacheBust cacheBust() {
        return cacheBust;
    }

    public S3 s3() {
        return s3;
    }

    public static final class CacheBust {
        private final boolean enabled;
        private final String param;
        private final String mode;

        private CacheBust(boolean enabled, String param, String mode) {
            this.enabled = enabled;
            this.param = param;
            this.mode = mode;
        }

        public static CacheBust fromConfig(FileConfiguration cfg) {
            boolean enabled = cfg.getBoolean("global.cache_bust.enabled", true);
            String param = cfg.getString("global.cache_bust.param", "v").trim();
            String mode = cfg.getString("global.cache_bust.mode", "timestamp").trim();
            if (param.isEmpty()) {
                param = "v";
            }
            if (mode.isEmpty()) {
                mode = "timestamp";
            }
            return new CacheBust(enabled, param, mode);
        }

        public String value() {
            if (!enabled) {
                return null;
            }
            if ("uuid".equalsIgnoreCase(mode)) {
                return UUID.randomUUID().toString().replace("-", "");
            }
            return String.valueOf(System.currentTimeMillis());
        }

        public boolean enabled() {
            return enabled;
        }

        public String param() {
            return param;
        }
    }

    public static final class S3 {
        private final String endpoint;
        private final String region;
        private final String bucket;
        private final String accessKey;
        private final String secretKey;
        private final String sessionToken;
        private final String pathScheme;
        private final boolean pathStyle;
        private final String acl;
        private final boolean usePresignedUrl;
        private final int presignedExpirySeconds;
        private final String publicUrlBase;

        private S3(String endpoint, String region, String bucket, String accessKey, String secretKey,
                   String sessionToken, String pathScheme, boolean pathStyle, String acl,
                   boolean usePresignedUrl, int presignedExpirySeconds, String publicUrlBase) {
            this.endpoint = endpoint;
            this.region = region;
            this.bucket = bucket;
            this.accessKey = accessKey;
            this.secretKey = secretKey;
            this.sessionToken = sessionToken;
            this.pathScheme = pathScheme;
            this.pathStyle = pathStyle;
            this.acl = acl;
            this.usePresignedUrl = usePresignedUrl;
            this.presignedExpirySeconds = presignedExpirySeconds;
            this.publicUrlBase = publicUrlBase;
        }

        public static S3 fromConfig(FileConfiguration cfg) {
            String endpoint = cfg.getString("s3.endpoint", "").trim();
            String region = cfg.getString("s3.region", "").trim();
            String bucket = cfg.getString("s3.bucket", "").trim();
            String accessKey = cfg.getString("s3.access_key", "").trim();
            String secretKey = cfg.getString("s3.secret_key", "").trim();
            String sessionToken = cfg.getString("s3.session_token", "").trim();
            String pathScheme = cfg.getString("s3.path_scheme", "itemsadder/{uid}/{file}").trim();
            boolean pathStyle = cfg.getBoolean("s3.path_style", false);
            String acl = cfg.getString("s3.acl", "public-read").trim().toLowerCase(Locale.US);
            boolean usePresignedUrl = cfg.getBoolean("s3.use_presigned_url", false);
            int presignedExpirySeconds = cfg.getInt("s3.presigned_expiry_seconds", 3600);
            String publicUrlBase = cfg.getString("s3.public_url_base", "").trim();

            if (bucket.isEmpty()) {
                throw new IllegalArgumentException("s3.bucket is required");
            }
            if (accessKey.isEmpty() || secretKey.isEmpty()) {
                throw new IllegalArgumentException("s3.access_key and s3.secret_key are required");
            }
            if (region.isEmpty() && endpoint.isEmpty()) {
                throw new IllegalArgumentException("s3.region or s3.endpoint is required");
            }
            if (pathScheme.isEmpty()) {
                pathScheme = "itemsadder/{uid}/{file}";
            }

            return new S3(endpoint, region, bucket, accessKey, secretKey, sessionToken,
                    pathScheme, pathStyle, acl, usePresignedUrl, presignedExpirySeconds, publicUrlBase);
        }

        public String endpoint() { return endpoint; }
        public String region() { return region; }
        public String bucket() { return bucket; }
        public String accessKey() { return accessKey; }
        public String secretKey() { return secretKey; }
        public String sessionToken() { return sessionToken; }
        public String pathScheme() { return pathScheme; }
        public boolean pathStyle() { return pathStyle; }
        public String acl() { return acl; }
        public boolean usePresignedUrl() { return usePresignedUrl; }
        public int presignedExpirySeconds() { return presignedExpirySeconds; }
        public String publicUrlBase() { return publicUrlBase; }
    }
}
