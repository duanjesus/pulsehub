package com.pulsehub.service.impl;

import com.pulsehub.dto.response.UserResponse;
import com.pulsehub.entity.User;
import com.pulsehub.exception.InvalidCredentialsException;
import com.pulsehub.exception.ResourceNotFoundException;
import com.pulsehub.mapper.UserMapper;
import com.pulsehub.repository.UserRepository;
import com.pulsehub.service.AvatarStorageService;
import com.pulsehub.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AvatarStorageService avatarStorageService;

    @Override
    public List<UserResponse> listContacts(Long currentUserId) {
        return userRepository.findByIdNotOrderByNameAsc(currentUserId).stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    public UserResponse getProfile(Long userId) {
        return userMapper.toResponse(findUser(userId));
    }

    @Override
    @Transactional
    public UserResponse updateName(Long userId, String name) {
        User user = findUser(userId);
        user.setName(name);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public UserResponse updateAvatar(Long userId, MultipartFile file) {
        User user = findUser(userId);
        String avatarUrl = avatarStorageService.store(userId, file);
        user.setAvatarUrl(avatarUrl);
        return userMapper.toResponse(userRepository.save(user));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

}
