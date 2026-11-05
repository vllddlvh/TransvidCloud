package com.transvidcloud.backend.auth.repository;

import com.transvidcloud.backend.auth.entity.UserItem;
import com.transvidcloud.backend.common.config.TvcProperties;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * Tầng Repository tương tác với bảng DynamoDB tvc-{env}-users.
 * Quản lý cả bản ghi người dùng chính và bản ghi khóa email duy nhất.
 */
@Slf4j
@Repository
public class UserRepository {

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<UserItem> userTable;

    public UserRepository(DynamoDbEnhancedClient enhancedClient, TvcProperties tvcProperties) {
        this.enhancedClient = enhancedClient;
        String tableName = tvcProperties.getAws().getDynamodb().getTablePrefix() + "users";
        this.userTable = enhancedClient.table(tableName, TableSchema.fromBean(UserItem.class));
    }

    /**
     * Đăng ký người dùng mới trong một giao dịch TransactWriteItems nguyên tử.
     * Ghi đồng thời User Item và Email Lock Item với điều kiện attribute_not_exists(userId).
     *
     * @param userItem Bản ghi tài khoản chính
     * @param lockItem Bản ghi khóa email (EMAIL#<email>)
     * @throws TransactionCanceledException Nếu email hoặc userId đã tồn tại
     */
    public void registerUserInTransaction(UserItem userItem, UserItem lockItem) {
        Expression notExistsExpr = Expression.builder()
                .expression("attribute_not_exists(userId)")
                .build();

        enhancedClient.transactWriteItems(builder -> builder
                .addPutItem(userTable, TransactPutItemEnhancedRequest.builder(UserItem.class)
                        .item(userItem)
                        .conditionExpression(notExistsExpr)
                        .build())
                .addPutItem(userTable, TransactPutItemEnhancedRequest.builder(UserItem.class)
                        .item(lockItem)
                        .conditionExpression(notExistsExpr)
                        .build())
        );
    }

    /**
     * Tra cứu tài khoản người dùng theo email thông qua GSI email-index.
     *
     * @param email Địa chỉ email đã chuẩn hóa
     * @return UserItem nếu tìm thấy, rỗng nếu không
     */
    public Optional<UserItem> findByEmail(String email) {
        DynamoDbIndex<UserItem> emailIndex = userTable.index("email-index");
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(email).build()
        );

        return emailIndex.query(r -> r.queryConditional(queryConditional))
                .stream()
                .flatMap(page -> page.items().stream())
                .filter(item -> UserItem.TYPE_USER.equals(item.getItemType()))
                .findFirst();
    }

    /**
     * Tra cứu tài khoản theo userId trên bảng chính.
     *
     * @param userId Định danh người dùng
     * @return UserItem nếu tìm thấy
     */
    public Optional<UserItem> findById(String userId) {
        UserItem item = userTable.getItem(Key.builder().partitionValue(userId).build());
        if (item != null && UserItem.TYPE_USER.equals(item.getItemType())) {
            return Optional.of(item);
        }
        return Optional.empty();
    }
}
