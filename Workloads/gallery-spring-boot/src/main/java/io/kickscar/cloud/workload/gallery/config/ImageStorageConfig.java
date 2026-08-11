package io.kickscar.cloud.workload.gallery.config;

import io.kickscar.cloud.workload.gallery.storage.AzureBlobImageStorage;
import io.kickscar.cloud.workload.gallery.storage.ImageStorage;
import io.kickscar.cloud.workload.gallery.storage.LocalImageStorage;
import io.kickscar.cloud.workload.gallery.storage.S3ImageStorage;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@EnableConfigurationProperties(ImageStorageConfig.ImageStorageProperties.class)
public class ImageStorageConfig implements WebMvcConfigurer {

    private final ImageStorageProperties imageStorageProperties;

    @Autowired
    public ImageStorageConfig(ImageStorageProperties imageStorageProperties) {
        this.imageStorageProperties = imageStorageProperties;
    }

    @Override
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "local")
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(imageStorageProperties.baseUrl() + "/**")
                .addResourceLocations(Path.of(imageStorageProperties.resolvedLocalPath()).toUri().toString());
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "local")
    public ImageStorage localImageStorage() {
        return new LocalImageStorage(imageStorageProperties);
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    public Region awsRegion() {
        return DefaultAwsRegionProviderChain.builder().build().getRegion();
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    public S3Client s3Client(Region region) {
        return S3Client.builder()
                .region(region)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    public ImageStorage s3ImageStorage(Region region, S3Client s3Client) {
        return new S3ImageStorage(region, s3Client, imageStorageProperties);
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "azure-blob")
    public BlobContainerClient blobContainerClient() {
        String endpoint = String.format("https://%s.blob.core.windows.net", imageStorageProperties.blob().account());
        return new BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient()
                .getBlobContainerClient(imageStorageProperties.blob().container());
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "azure-blob")
    public ImageStorage azureBlobImageStorage(BlobContainerClient blobContainerClient) {
        return new AzureBlobImageStorage(blobContainerClient, imageStorageProperties);
    }

    @ConfigurationProperties(prefix = "app.storage")
    public record ImageStorageProperties(String type, Local local, S3 s3, Blob blob) {
        public record Local(String path, Url url) {}
        public record Url(String prefix) {}
        public record S3(String bucket) {}
        public record Blob(String account, String container) {}

        public String baseUrl() {
            return local.url().prefix().replaceAll("/+$", "");
        }

        public String resolvedLocalPath() {
            if (local == null || local.path() == null) {
                throw new IllegalStateException("fs.path is required");
            }

            Path path = Paths.get(local.path());

            if (!path.isAbsolute()) {
                path = Paths.get(System.getProperty("user.dir")).resolve(path);
            }

            return path.normalize().toAbsolutePath().toString();
        }
    }
}
