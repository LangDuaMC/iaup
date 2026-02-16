package net.langdua.iaup.service;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import net.langdua.iaup.config.PluginSettings;

import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class S3UploadService {
    private final PluginSettings.S3 settings;

    public S3UploadService(PluginSettings.S3 settings) {
        this.settings = settings;
    }

    public UploadResult upload(File file, String uid, PluginSettings.CacheBust cacheBust) {
        AmazonS3 client = null;
        try {
            client = buildClient();
            String objectKey = buildObjectKey(file, uid);

            PutObjectRequest put = new PutObjectRequest(settings.bucket(), objectKey, file);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("application/zip");
            put.setMetadata(metadata);

            CannedAccessControlList acl = parseAcl(settings.acl());
            if (acl != null) {
                put.setCannedAcl(acl);
            }

            client.putObject(put);
            String downloadUrl = buildDownloadUrl(client, objectKey, cacheBust);
            return new UploadResult(objectKey, downloadUrl);
        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
    }

    private AmazonS3 buildClient() {
        ClientConfiguration clientConfig = new ClientConfiguration();

        if (!settings.sessionToken().isEmpty()) {
            BasicSessionCredentials creds = new BasicSessionCredentials(
                    settings.accessKey(), settings.secretKey(), settings.sessionToken()
            );
            return buildClientWithCreds(clientConfig, creds);
        }

        BasicAWSCredentials creds = new BasicAWSCredentials(settings.accessKey(), settings.secretKey());
        return buildClientWithCreds(clientConfig, creds);
    }

    private AmazonS3 buildClientWithCreds(ClientConfiguration clientConfig, com.amazonaws.auth.AWSCredentials creds) {
        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard()
                .withClientConfiguration(clientConfig)
                .withCredentials(new AWSStaticCredentialsProvider(creds));

        if (!settings.endpoint().isEmpty()) {
            AwsClientBuilder.EndpointConfiguration endpointCfg =
                    new AwsClientBuilder.EndpointConfiguration(settings.endpoint(), settings.region());
            builder.withEndpointConfiguration(endpointCfg).withPathStyleAccessEnabled(settings.pathStyle());
        } else {
            builder.withRegion(settings.region());
        }
        return builder.build();
    }

    private String buildObjectKey(File file, String uid) {
        String name = file.getName();
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            base = name.substring(0, dot);
            ext = name.substring(dot + 1);
        }

        String date = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        String ts = String.valueOf(System.currentTimeMillis());

        String key = settings.pathScheme()
                .replace("{uid}", uid)
                .replace("{file}", name)
                .replace("{name}", base)
                .replace("{ext}", ext)
                .replace("{date}", date)
                .replace("{ts}", ts);

        while (key.startsWith("/")) {
            key = key.substring(1);
        }
        return key;
    }

    private String buildDownloadUrl(AmazonS3 client, String objectKey, PluginSettings.CacheBust cacheBust) {
        String cacheValue = cacheBust.value();
        if (settings.usePresignedUrl()) {
            GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(settings.bucket(), objectKey)
                    .withMethod(HttpMethod.GET);
            if (settings.presignedExpirySeconds() > 0) {
                Date expires = new Date(System.currentTimeMillis() + (settings.presignedExpirySeconds() * 1000L));
                req.withExpiration(expires);
            }
            if (cacheBust.enabled() && cacheValue != null) {
                req.addRequestParameter(cacheBust.param(), cacheValue);
            }
            URL presigned = client.generatePresignedUrl(req);
            return presigned.toString();
        }

        String base = settings.publicUrlBase().trim();
        String url;
        if (!base.isEmpty()) {
            url = joinUrl(base, objectKey);
        } else if (settings.pathStyle() && !settings.endpoint().isEmpty()) {
            url = joinUrl(settings.endpoint(), settings.bucket() + "/" + objectKey);
        } else {
            URL s3Url = client.getUrl(settings.bucket(), objectKey);
            url = s3Url != null ? s3Url.toString() : joinUrl(settings.endpoint(), settings.bucket() + "/" + objectKey);
        }

        if (cacheBust.enabled() && cacheValue != null) {
            url = appendQueryParam(url, cacheBust.param(), cacheValue);
        }
        return url;
    }

    private static String joinUrl(String base, String path) {
        if (base == null) {
            base = "";
        }
        String trimmedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String trimmedPath = path.startsWith("/") ? path.substring(1) : path;
        if (trimmedBase.isEmpty()) {
            return "/" + trimmedPath;
        }
        return trimmedBase + "/" + trimmedPath;
    }

    private static String appendQueryParam(String url, String key, String value) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + key + "=" + value;
    }

    private static CannedAccessControlList parseAcl(String acl) {
        if (acl == null) {
            return null;
        }
        String normalized = acl.trim().toLowerCase(Locale.US);
        if (normalized.isEmpty() || "none".equals(normalized)) {
            return null;
        }
        if ("public-read".equals(normalized)) {
            return CannedAccessControlList.PublicRead;
        }
        if ("private".equals(normalized)) {
            return CannedAccessControlList.Private;
        }
        if ("bucket-owner-full-control".equals(normalized)) {
            return CannedAccessControlList.BucketOwnerFullControl;
        }
        if ("bucket-owner-read".equals(normalized)) {
            return CannedAccessControlList.BucketOwnerRead;
        }
        if ("public-read-write".equals(normalized)) {
            return CannedAccessControlList.PublicReadWrite;
        }
        try {
            return CannedAccessControlList.valueOf(acl);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static final class UploadResult {
        private final String objectKey;
        private final String downloadUrl;

        public UploadResult(String objectKey, String downloadUrl) {
            this.objectKey = objectKey;
            this.downloadUrl = downloadUrl;
        }

        public String objectKey() {
            return objectKey;
        }

        public String downloadUrl() {
            return downloadUrl;
        }
    }
}
