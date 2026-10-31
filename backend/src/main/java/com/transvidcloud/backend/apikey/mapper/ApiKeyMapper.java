package com.transvidcloud.backend.apikey.mapper;

import com.transvidcloud.backend.apikey.dto.ApiKeyCreatedResponse;
import com.transvidcloud.backend.apikey.dto.ApiKeyListResponse;
import com.transvidcloud.backend.apikey.dto.ApiKeyMetadataResponse;
import com.transvidcloud.backend.apikey.entity.ApiKeyItem;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper chuyển đổi giữa ApiKeyItem (Entity) và API Key DTOs.
 */
@Mapper(componentModel = "spring")
public interface ApiKeyMapper {

    ApiKeyMetadataResponse toMetadataResponse(ApiKeyItem item);

    List<ApiKeyMetadataResponse> toMetadataResponseList(List<ApiKeyItem> items);

    @Mapping(target = "rawKey", source = "rawKey")
    ApiKeyCreatedResponse toCreatedResponse(ApiKeyItem item, String rawKey);

    default ApiKeyListResponse toListResponse(List<ApiKeyItem> items) {
        if (items == null) {
            return new ApiKeyListResponse(List.of());
        }
        return new ApiKeyListResponse(toMetadataResponseList(items));
    }
}
