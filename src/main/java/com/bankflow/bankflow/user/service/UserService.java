package com.bankflow.bankflow.user.service;

import com.bankflow.bankflow.global.exception.DuplicateEmailException;
import com.bankflow.bankflow.global.exception.NotFoundException;
import com.bankflow.bankflow.user.domain.User;
import com.bankflow.bankflow.user.dto.UserCreateRequest;
import com.bankflow.bankflow.user.dto.UserResponse;
import com.bankflow.bankflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {

        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("이미 사용 중인 이메일입니다.");
        }

        User user = new User(
                request.email(),
                request.name()
        );

        return UserResponse.from(userRepository.save(user));
    }

    public List<UserResponse> getUsers() {
        return userRepository.findAll(Sort.by("id")).stream()
                .map(UserResponse::from)
                .toList();
    }

    public UserResponse getUser(Long userId) {
        return UserResponse.from(findUser(userId));
    }

    public User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 사용자입니다."));
    }
}
