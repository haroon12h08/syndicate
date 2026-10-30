package com.syndicate.evidence.storage;

import com.syndicate.common.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;

@Service
@ConditionalOnProperty(name = "syndicate.storage.type", havingValue = "s3")
public class S3StorageService implements BlobStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3Client s3Client;
    private final String bucketName;

    public S3StorageService(
            @Value("${syndicate.storage.s3.bucket:syndicate-evidence}") String bucketName,
            @Value("${syndicate.storage.s3.region:us-east-1}") String region,
            @Value("${syndicate.storage.s3.endpoint:#{null}}") String endpoint,
            @Value("${syndicate.storage.s3.access-key:minioadmin}") String accessKey,
            @Value("${syndicate.storage.s3.secret-key:minioadmin}") String secretKey,
            @Value("${syndicate.storage.s3.path-style-access:true}") boolean pathStyleAccess) {
        this.bucketName = bucketName;

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(pathStyleAccess);

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        this.s3Client = builder.build();
        log.info("Initialized S3StorageService for bucket '{}' with endpoint '{}'", bucketName, endpoint);
    }

    // Constructor for testing / injection of pre-built client
    public S3StorageService(S3Client s3Client, String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    @Override
    public String store(byte[] content, String sha256) {
        if (content == null || content.length == 0) {
            throw new BadRequestException("Uploaded file is empty");
        }
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Not a SHA-256 hex digest");
        }
        String key = "sha256/" + sha256.substring(0, 2) + "/" + sha256;
        try {
            if (exists(key)) {
                return key;
            }
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentLength((long) content.length)
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(content));
            return key;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to store file in S3: " + key, e);
        }
    }

    @Override
    public byte[] readBytes(String storagePath) {
        try {
            return s3Client.getObject(b -> b.bucket(bucketName).key(storagePath),
                    ResponseTransformer.toBytes()).asByteArray();
        } catch (NoSuchKeyException e) {
            throw new BadRequestException("Stored file is missing: " + storagePath);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read stored file from S3: " + storagePath, e);
        }
    }

    @Override
    public Resource load(String storagePath) {
        byte[] bytes = readBytes(storagePath);
        return new ByteArrayResource(bytes, storagePath);
    }

    @Override
    public boolean exists(String storagePath) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(storagePath).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getBackendType() {
        return "S3";
    }

    public S3Client getS3Client() {
        return s3Client;
    }
}
