package com.ourtoday.api.image;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;

import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.OptionalLong;

public class S3ImageStorage implements ImageStorage {

    private final StorageProperties props;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3ImageStorage(StorageProperties props) {
        this.props = props;
        var credentials = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
        var endpoint = URI.create(props.endpoint());
        var region = Region.of(props.region());
        // R2 는 path-style 권장
        var s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build();

        this.client = S3Client.builder()
            .endpointOverride(endpoint).region(region).credentialsProvider(credentials)
            .serviceConfiguration(s3Config)
            .build();
        this.presigner = S3Presigner.builder()
            .endpointOverride(endpoint).region(region).credentialsProvider(credentials)
            .serviceConfiguration(s3Config)
            .build();
    }

    @Override
    public String presignUpload(String key, String contentType, long contentLength) {
        PutObjectRequest put = PutObjectRequest.builder()
            .bucket(props.bucket()).key(key)
            .contentType(contentType).contentLength(contentLength)
            .build();
        return presigner.presignPutObject(r -> r.signatureDuration(props.uploadUrlTtl()).putObjectRequest(put))
            .url().toString();
    }

    @Override
    public String presignDownload(String key) {
        GetObjectRequest get = GetObjectRequest.builder().bucket(props.bucket()).key(key).build();
        return presigner.presignGetObject(r -> r.signatureDuration(props.downloadUrlTtl()).getObjectRequest(get))
            .url().toString();
    }

    @Override
    public OptionalLong size(String key) {
        try {
            return OptionalLong.of(client.headObject(
                HeadObjectRequest.builder().bucket(props.bucket()).key(key).build()).contentLength());
        } catch (NoSuchKeyException e) {
            return OptionalLong.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return OptionalLong.empty();
            }
            throw e;
        }
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(props.bucket()).key(key).build());
    }

    @Override
    public void put(String key, Path file, String contentType) {
        client.putObject(PutObjectRequest.builder().bucket(props.bucket()).key(key).contentType(contentType).build(),
            RequestBody.fromFile(file));
    }

    @Override
    public void copyTo(String key, OutputStream out) {
        client.getObject(GetObjectRequest.builder().bucket(props.bucket()).key(key).build(),
            ResponseTransformer.toOutputStream(out));
    }

    @Override
    public String presignAttachment(String key, String fileName) {
        GetObjectRequest get = GetObjectRequest.builder().bucket(props.bucket()).key(key)
            .responseContentDisposition("attachment; filename=\"" + fileName + "\"")
            .build();
        return presigner.presignGetObject(r -> r.signatureDuration(props.downloadUrlTtl()).getObjectRequest(get))
            .url().toString();
    }
}
