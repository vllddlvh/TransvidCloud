package com.transvidcloud.backend.project.mapper;

import com.transvidcloud.backend.project.dto.ProjectListResponse;
import com.transvidcloud.backend.project.dto.ProjectResponse;
import com.transvidcloud.backend.project.entity.ProjectItem;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * MapStruct mapper chuyển đổi giữa ProjectItem (Entity) và Project DTOs.
 */
@Mapper(componentModel = "spring")
public interface ProjectMapper {

    ProjectResponse toProjectResponse(ProjectItem projectItem);

    List<ProjectResponse> toProjectResponseList(List<ProjectItem> items);

    default ProjectListResponse toProjectListResponse(List<ProjectItem> items) {
        if (items == null) {
            return new ProjectListResponse(List.of());
        }
        return new ProjectListResponse(toProjectResponseList(items));
    }
}
