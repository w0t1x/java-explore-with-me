package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.user.NewUserRequest;
import ru.practicum.dto.user.UserDto;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.UserMapper;
import ru.practicum.model.User;
import ru.practicum.repository.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional
    public UserDto createUser(NewUserRequest newUserRequest) {
        if (userRepository.existsByEmail(newUserRequest.getEmail())) {
            throw new ConflictException("Электронная почта уже существует: " + newUserRequest.getEmail());
        }

        User user = userMapper.toUser(newUserRequest);
        User savedUser = userRepository.save(user);
        log.info("Создание пользователя с id={} и email={}", savedUser.getId(), savedUser.getEmail());

        return userMapper.toUserDto(savedUser);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("пользователь с id=" + userId + " не найден"));

        userRepository.delete(user);
        log.info("удаление пользователя с id={}", userId);
    }

    public List<UserDto> getUsers(List<Long> ids, Integer from, Integer size) {
        // Валидация параметров
        if (from == null || from < 0) {
            from = 0;
        }
        if (size == null || size <= 0) {
            size = 10;
        }

        if (size == 0) {
            size = 10;
        }

        int page = from / size;
        Pageable pageable = PageRequest.of(page, size);
        List<User> users;

        if (ids == null || ids.isEmpty()) {
            users = userRepository.findAll(pageable).getContent();
        } else {
            users = userRepository.findUsersByIds(ids, pageable).getContent();
        }

        return users.stream()
                .map(userMapper::toUserDto)
                .collect(Collectors.toList());
    }

    public UserDto getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));

        return userMapper.toUserDto(user);
    }
}