package com.pulsehub.service.impl;

import com.pulsehub.dto.response.UserResponse;
import com.pulsehub.mapper.UserMapper;
import com.pulsehub.repository.UserRepository;
import com.pulsehub.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public List<UserResponse> listContacts(Long currentUserId) {
        return userRepository.findByIdNotOrderByNameAsc(currentUserId).stream()
                .map(userMapper::toResponse)
                .toList();
    }

}
