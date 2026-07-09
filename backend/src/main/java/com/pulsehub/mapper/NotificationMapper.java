package com.pulsehub.mapper;

import com.pulsehub.dto.response.NotificationResponse;
import com.pulsehub.entity.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    NotificationResponse toResponse(Notification notification);
}
