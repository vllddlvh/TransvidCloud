package com.transvidcloud.backend.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.transvidcloud.backend.auth.dto.AuthResponse;
import com.transvidcloud.backend.auth.dto.LoginRequest;
import com.transvidcloud.backend.auth.dto.RegisterRequest;
import com.transvidcloud.backend.auth.dto.UserSummary;
import com.transvidcloud.backend.auth.entity.UserItem;
import com.transvidcloud.backend.auth.service.AuthService;
import com.transvidcloud.backend.common.config.TvcProperties;
import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("UserAuthIT - Testcontainers LocalStack DynamoDB Integration Test")
class UserAuthIT {

    static LocalStackContainer localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(LocalStackContainer.Service.DYNAMODB);

    static {
        localStack.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("tvc.aws.endpoint", () -> localStack.getEndpoint().toString());
        registry.add("spring.cloud.aws.region.static", () -> localStack.getRegion());
        registry.add("spring.cloud.aws.credentials.access-key", () -> localStack.getAccessKey());
        registry.add("spring.cloud.aws.credentials.secret-key", () -> localStack.getSecretKey());
    }

    @Autowired
    private DynamoDbClient dynamoDbClient;

    @Autowired
    private TvcProperties tvcProperties;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void initDynamoDbTable() {
        String tableName = tvcProperties.getAws().getDynamodb().getTablePrefix() + "users";
        try {
            dynamoDbClient.createTable(CreateTableRequest.builder()
                    .tableName(tableName)
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("userId").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("email").attributeType(ScalarAttributeType.S).build()
                    )
                    .keySchema(
                            KeySchemaElement.builder().attributeName("userId").keyType(KeyType.HASH).build()
                    )
                    .globalSecondaryIndexes(
                            GlobalSecondaryIndex.builder()
                                    .indexName("email-index")
                                    .keySchema(KeySchemaElement.builder().attributeName("email").keyType(KeyType.HASH).build())
                                    .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                                    .build()
                    )
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
        } catch (ResourceInUseException ignored) {
            // Bảng đã tồn tại
        }
    }

    @Test
    @DisplayName("US1 & US2 & US3: Đăng ký người dùng mới, chặn trùng email, đăng nhập và xác thực token trên DynamoDB thực tế")
    void testEndToEndUserLifecycle() {
        String email = "it-user@transvidcloud.io";
        String password = "StrongPassword123!";
        String fullName = "Integration Test User";

        // 1. Đăng ký tài khoản mới thành công (US1)
        RegisterRequest registerReq = new RegisterRequest(email, password, fullName);
        AuthResponse registerResp = authService.register(registerReq);

        assertThat(registerResp).isNotNull();
        assertThat(registerResp.accessToken()).isNotBlank();
        assertThat(registerResp.tokenType()).isEqualTo("Bearer");
        assertThat(registerResp.user().email()).isEqualTo(email);
        assertThat(registerResp.user().userId()).startsWith("usr_");

        String userId = registerResp.user().userId();

        // Kiểm tra bản ghi chính và bản ghi khóa email tồn tại trong DynamoDB
        Optional<UserItem> userOpt = userRepository.findById(userId);
        assertThat(userOpt).isPresent();
        assertThat(userOpt.get().getEmail()).isEqualTo(email);
        assertThat(userOpt.get().getItemType()).isEqualTo(UserItem.TYPE_USER);

        Optional<UserItem> userByEmail = userRepository.findByEmail(email);
        assertThat(userByEmail).isPresent();
        assertThat(userByEmail.get().getUserId()).isEqualTo(userId);

        // 2. Thử đăng ký lại với cùng email (chữ hoa/thường khác nhau) -> phải bị chặn 409 (US1)
        RegisterRequest duplicateReq = new RegisterRequest("  IT-USER@transvidcloud.io  ", "AnotherPassword999!", "Duplicate");
        assertThatThrownBy(() -> authService.register(duplicateReq))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_ALREADY_EXISTS);

        // 3. Đăng nhập thành công với tài khoản vừa tạo (US2)
        LoginRequest loginReq = new LoginRequest(email, password);
        AuthResponse loginResp = authService.login(loginReq);

        assertThat(loginResp).isNotNull();
        assertThat(loginResp.accessToken()).isNotBlank();
        assertThat(loginResp.user().userId()).isEqualTo(userId);

        // 4. Đăng nhập sai mật khẩu -> từ chối 401 (US2)
        LoginRequest wrongPassReq = new LoginRequest(email, "WrongPassword!");
        assertThatThrownBy(() -> authService.login(wrongPassReq))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS);

        // 5. Xác thực Token phát hành qua JwtDecoder và trích xuất danh tính (US3)
        Jwt decodedJwt = jwtDecoder.decode(loginResp.accessToken());
        assertThat(decodedJwt.getSubject()).isEqualTo(userId);
        assertThat(decodedJwt.getClaimAsString("email")).isEqualTo(email);

        UserSummary currentSummary = authService.getCurrentUser(decodedJwt.getSubject());
        assertThat(currentSummary.userId()).isEqualTo(userId);
        assertThat(currentSummary.email()).isEqualTo(email);
        assertThat(currentSummary.fullName()).isEqualTo(fullName);
    }
}
