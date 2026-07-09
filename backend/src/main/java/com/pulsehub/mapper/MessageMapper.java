package com.pulsehub.mapper;

import com.pulsehub.dto.response.MessageResponse;
import com.pulsehub.entity.Message;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MessageMapper {
    MessageResponse toResponse(Message message);
}
