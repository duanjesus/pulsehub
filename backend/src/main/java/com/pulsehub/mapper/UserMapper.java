package com.pulsehub.mapper;

import com.pulsehub.dto.response.UserResponse;
import com.pulsehub.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponse toResponse(User user);
}
