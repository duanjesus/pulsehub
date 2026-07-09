package com.pulsehub.service;

import com.pulsehub.entity.User;
import com.pulsehub.exception.InvalidCredentialsException;
import com.pulsehub.mapper.UserMapper;
import com.pulsehub.repository.UserRepository;
import com.pulsehub.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AvatarStorageService avatarStorageService;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void changePassword_rejectsWrongCurrentPassword() {
        User user = User.builder().id(1L).password("encoded-old").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-old")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(1L, "wrong", "newpassword"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_encodesAndSavesNewPassword() {
        User user = User.builder().id(1L).password("encoded-old").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("newpassword")).thenReturn("encoded-new");

        userService.changePassword(1L, "correct", "newpassword");

        assertThat(user.getPassword()).isEqualTo("encoded-new");
        verify(userRepository).save(user);
    }

    @Test
    void updateAvatar_storesFileAndPersistsReturnedUrl() {
        User user = User.builder().id(1L).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(avatarStorageService.store(eq(1L), any())).thenReturn("/uploads/avatars/1-abc.png");
        when(userRepository.save(user)).thenReturn(user);

        userService.updateAvatar(1L, null);

        assertThat(user.getAvatarUrl()).isEqualTo("/uploads/avatars/1-abc.png");
    }

}
