package com.smartplanner.smartplanner.dto.task;

import com.smartplanner.smartplanner.model.Task;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface TaskMapper {
    TaskMapper INSTANCE = Mappers.getMapper(TaskMapper.class);

    @Mapping(source = "course.id", target = "courseId")
    TaskResponse toResponse(Task task);
}
