package com.transvidcloud.backend.auth.mapper;

import com.transvidcloud.backend.auth.dto.UserSummary;
import com.transvidcloud.backend.auth.entity.UserItem;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper chuyển đổi giữa UserItem (Entity) và UserSummary (DTO).
 * Tuyệt đối không map passwordHash ra ngoài.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    UserSummary toUserSummary(UserItem userItem);
}
