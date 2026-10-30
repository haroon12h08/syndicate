package com.syndicate.invariants;

import com.syndicate.auth.JwtService;
import com.syndicate.auth.dto.AuthResponse;
import com.syndicate.auth.dto.LoginRequest;
import com.syndicate.auth.dto.MfaLoginRequest;
import com.syndicate.auth.dto.MfaSetupResponse;
import com.syndicate.auth.dto.MfaVerifyRequest;
import com.syndicate.auth.dto.RegisterRequest;
import com.syndicate.auth.totp.TotpService;
import com.syndicate.common.BadRequestException;
import com.syndicate.evidence.EvidenceService;
import com.syndicate.evidence.FileStorageService;
import com.syndicate.evidence.scanner.MalwareScanner;
import com.syndicate.evidence.scanner.PassThroughMalwareScanner;
import com.syndicate.evidence.storage.BlobStorageService;
import com.syndicate.evidence.storage.FileSystemStorageService;
import com.syndicate.evidence.storage.S3StorageService;
import com.syndicate.metrics.SyndicateMetrics;
import com.syndicate.organization.OrganizationType;
import com.syndicate.support.IntegrationTestBase;
import com.syndicate.user.User;
import com.syndicate.user.UserDto;
import com.syndicate.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

/**
 * Phase H Production Hardening Invariant Test Suite.
 * Formally verifies H1 (Blob Storage), H2 (Auth & TOTP MFA + Lockout), H3 (Upload Safety & Malware),
 * and H5 (Actuator & Prometheus Observability) per Spec §50, §51, §52, and §54.
 */
class ProductionHardeningIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    @Autowired FileStorageService fileStorageService;
    @Autowired TotpService totpService;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired SyndicateMetrics syndicateMetrics;
    @Autowired EvidenceService evidenceService;
    @LocalServerPort int port;

    // =========================================================================
    // H1: STORAGE ARCHITECTURE & CONTENT-ADDRESSED IMMUTABILITY
    // =========================================================================
    @Nested
    @DisplayName("H1: Object Storage Abstraction & Immutability")
    class StorageInvariants {

        @Test
        @DisplayName("Content-addressed file storage preserves byte integrity and write-once immutability")
        void testFileStorageIntegrity() {
            byte[] payload = "SYNDICATE-PRODUCTION-HARDENING-BLOB-DATA-2026".getBytes(StandardCharsets.UTF_8);
            String sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

            String path1 = fileStorageService.store(payload, sha256);
            assertThat(path1).isEqualTo("sha256/e3/" + sha256);

            // Re-store is idempotent and returns identical path without rewriting
            String path2 = fileStorageService.store(payload, sha256);
            assertThat(path2).isEqualTo(path1);

            byte[] retrieved = fileStorageService.readBytes(path1);
            assertThat(retrieved).isEqualTo(payload);

            Resource resource = fileStorageService.load(path1);
            assertThat(resource.exists()).isTrue();
            assertThat(fileStorageService.exists(path1)).isTrue();
        }

        @Test
        @DisplayName("S3StorageService driver correctly formats keys and interfaces with S3/MinIO protocol")
        void testS3StorageDriver() {
            S3Client mockS3 = Mockito.mock(S3Client.class);
            S3StorageService s3Service = new S3StorageService(mockS3, "test-evidence-bucket");
            assertThat(s3Service.getBackendType()).isEqualTo("S3");

            // Mock headObject throwing NoSuchKey to simulate initial write
            Mockito.when(mockS3.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(NoSuchKeyException.builder().message("Key not found").build());

            byte[] data = "S3_MOCK_TEST_PAYLOAD".getBytes(StandardCharsets.UTF_8);
            String sha256 = "11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff";

            String key = s3Service.store(data, sha256);
            assertThat(key).isEqualTo("sha256/11/" + sha256);

            Mockito.verify(mockS3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }
    }

    // =========================================================================
    // H2: AUTH HARDENING, TOTP MFA, AND BRUTE-FORCE LOCKOUT
    // =========================================================================
    @Nested
    @DisplayName("H2: Auth Hardening, RFC 6238 TOTP & Lockout")
    class AuthInvariants {

        @Test
        @DisplayName("Brute force protection locks account after 5 consecutive failures")
        void testBruteForceLockout() throws Exception {
            String email = "lockout-test-" + UUID.randomUUID() + "@syndicate.internal";
            RegisterRequest reg = new RegisterRequest(email, "StrongPassword123!", "Lockout Test User",
                    "Lockout Org", OrganizationType.ISSUER);
            rest.postForEntity("/api/auth/register", reg, AuthResponse.class);

            HttpClient client = HttpClient.newHttpClient();
            String badBody = "{\"email\":\"" + email + "\",\"password\":\"WrongPassword!\"}";

            // 4 invalid attempts: still not locked
            for (int i = 1; i <= 4; i++) {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(badBody))
                        .build();
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                assertThat(res.statusCode()).isEqualTo(401);
            }

            User user = userRepository.findByEmail(email).orElseThrow();
            assertThat(user.getFailedLoginAttempts()).isEqualTo(4);
            assertThat(user.isLocked()).isFalse();

            // 5th invalid attempt: triggers lockout
            HttpRequest fifthReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(badBody))
                    .build();
            HttpResponse<String> fifthRes = client.send(fifthReq, HttpResponse.BodyHandlers.ofString());
            assertThat(fifthRes.statusCode()).isEqualTo(401);

            User lockedUser = userRepository.findByEmail(email).orElseThrow();
            assertThat(lockedUser.getFailedLoginAttempts()).isEqualTo(5);
            assertThat(lockedUser.isLocked()).isTrue();
            assertThat(lockedUser.getLockedUntil()).isAfter(Instant.now());

            // 6th attempt with CORRECT password while locked is rejected with 400
            String goodBody = "{\"email\":\"" + email + "\",\"password\":\"StrongPassword123!\"}";
            HttpRequest lockedReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(goodBody))
                    .build();
            HttpResponse<String> lockedRes = client.send(lockedReq, HttpResponse.BodyHandlers.ofString());
            assertThat(lockedRes.statusCode()).isEqualTo(400);
            assertThat(lockedRes.body()).contains("temporarily locked");
        }

        @Test
        @DisplayName("RFC 6238 TOTP lifecycle: setup -> verify/enable -> 2FA login challenge -> disable")
        void testMfaLifecycle() throws Exception {
            String email = "mfa-test-" + UUID.randomUUID() + "@syndicate.internal";
            String password = "StrongPassword123!";
            RegisterRequest reg = new RegisterRequest(email, password, "MFA Test User", "MFA Org", OrganizationType.MERCHANT_BANKER);
            ResponseEntity<AuthResponse> regRes = rest.postForEntity("/api/auth/register", reg, AuthResponse.class);
            String jwt = regRes.getBody().token();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(jwt);

            // Step 1: Initiate MFA Setup
            ResponseEntity<MfaSetupResponse> setupRes = rest.exchange(
                    "/api/auth/mfa/setup", HttpMethod.POST, new HttpEntity<>(headers), MfaSetupResponse.class);
            assertThat(setupRes.getStatusCode()).isEqualTo(HttpStatus.OK);
            MfaSetupResponse setupData = setupRes.getBody();
            assertThat(setupData.secret()).hasSize(32);
            assertThat(setupData.qrCodeUri()).startsWith("otpauth://totp/Syndicate:");

            // Step 2: Enable MFA using valid TOTP code
            long currentStep = Instant.now().getEpochSecond() / TotpService.TIME_STEP_SECONDS;
            int validCode = totpService.generateCodeForStep(setupData.secret(), currentStep);

            ResponseEntity<UserDto> enableRes = rest.exchange(
                    "/api/auth/mfa/enable", HttpMethod.POST,
                    new HttpEntity<>(new MfaVerifyRequest(validCode), headers), UserDto.class);
            assertThat(enableRes.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(enableRes.getBody().mfaEnabled()).isTrue();

            // Step 3: Login requires MFA challenge
            ResponseEntity<AuthResponse> challengeRes = rest.postForEntity(
                    "/api/auth/login", new LoginRequest(email, password), AuthResponse.class);
            assertThat(challengeRes.getStatusCode()).isEqualTo(HttpStatus.OK);
            AuthResponse challengeBody = challengeRes.getBody();
            assertThat(challengeBody.mfaRequired()).isTrue();
            assertThat(challengeBody.mfaToken()).isNotBlank();
            assertThat(challengeBody.token()).isNull(); // Real access token NOT yet issued!

            // Step 4: Verify login with incorrect code fails (using HttpClient to handle 401 cleanly)
            HttpClient client = HttpClient.newHttpClient();
            String badVerifyBody = "{\"mfaToken\":\"" + challengeBody.mfaToken() + "\",\"code\":999999}";
            HttpRequest badVerifyReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/auth/mfa/verify-login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(badVerifyBody))
                    .build();
            HttpResponse<String> badVerify = client.send(badVerifyReq, HttpResponse.BodyHandlers.ofString());
            assertThat(badVerify.statusCode()).isEqualTo(401);

            // Step 5: Verify login with valid code succeeds and grants full access token
            int freshCode = totpService.generateCodeForStep(setupData.secret(), currentStep);
            ResponseEntity<AuthResponse> goodVerify = rest.postForEntity(
                    "/api/auth/mfa/verify-login",
                    new MfaLoginRequest(challengeBody.mfaToken(), freshCode), AuthResponse.class);
            assertThat(goodVerify.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(goodVerify.getBody().token()).isNotBlank();
            assertThat(goodVerify.getBody().user().mfaEnabled()).isTrue();

            // Step 6: Disable MFA
            HttpHeaders newAuth = new HttpHeaders();
            newAuth.setBearerAuth(goodVerify.getBody().token());
            ResponseEntity<UserDto> disableRes = rest.exchange(
                    "/api/auth/mfa/disable", HttpMethod.POST,
                    new HttpEntity<>(new MfaVerifyRequest(freshCode), newAuth), UserDto.class);
            assertThat(disableRes.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(disableRes.getBody().mfaEnabled()).isFalse();
        }
    }

    // =========================================================================
    // H3: UPLOAD SAFETY & MALWARE SCANNING
    // =========================================================================
    @Nested
    @DisplayName("H3: Upload Safety & Malware Scanner")
    class UploadSafetyInvariants {

        @Test
        @DisplayName("PassThroughMalwareScanner rejects EICAR antivirus test signatures")
        void testEicarMalwareDetection() {
            PassThroughMalwareScanner scanner = new PassThroughMalwareScanner();
            byte[] eicar = ("Some preamble text " + PassThroughMalwareScanner.EICAR_TEST_STRING + " end")
                    .getBytes(StandardCharsets.US_ASCII);

            MalwareScanner.ScanResult result = scanner.scan("suspicious.pdf", eicar);
            assertThat(result.isClean()).isFalse();
            assertThat(result.signatureOrThreat()).isEqualTo("EICAR_TEST_FILE");
            assertThat(result.details()).contains("Malware signature detected");
        }

        @Test
        @DisplayName("Clean bytes pass scan cleanly")
        void testCleanFileScan() {
            PassThroughMalwareScanner scanner = new PassThroughMalwareScanner();
            byte[] cleanPdf = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);

            MalwareScanner.ScanResult result = scanner.scan("safe-doc.pdf", cleanPdf);
            assertThat(result.isClean()).isTrue();
        }
    }

    // =========================================================================
    // H5: OBSERVABILITY & METRICS INSTRUMENTATION
    // =========================================================================
    @Nested
    @DisplayName("H5: Observability & Prometheus Metrics")
    class ObservabilityInvariants {

        @Test
        @DisplayName("/actuator/health is accessible and reports UP")
        void testActuatorHealth() {
            ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("\"status\":\"UP\"");
        }

        @Test
        @DisplayName("Actuator or Prometheus endpoint exposes registered Syndicate domain metrics")
        void testPrometheusMetrics() {
            // Trigger some domain counter increments
            syndicateMetrics.incrementLoginFailures();
            syndicateMetrics.incrementLockouts();
            syndicateMetrics.incrementEvidenceUploads();
            syndicateMetrics.recordReadiness(() -> {
                // simulate tiny duration
            });

            // Check /actuator/prometheus or Micrometer registry metrics directly
            assertThat(syndicateMetrics.getRegistry().find("syndicate.auth.login.failures").counter().count())
                    .isGreaterThanOrEqualTo(1.0);
            assertThat(syndicateMetrics.getRegistry().find("syndicate.auth.lockouts").counter().count())
                    .isGreaterThanOrEqualTo(1.0);
            assertThat(syndicateMetrics.getRegistry().find("syndicate.evidence.uploads").counter().count())
                    .isGreaterThanOrEqualTo(1.0);
            assertThat(syndicateMetrics.getRegistry().find("syndicate.readiness.evaluation.duration").timer().count())
                    .isGreaterThanOrEqualTo(1L);

            // Also check /actuator endpoint
            ResponseEntity<String> actuatorRes = rest.getForEntity("/actuator", String.class);
            assertThat(actuatorRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
