package com.qwikride.prc.service;

import com.qwikride.model.User;
import com.qwikride.prc.domain.MembershipStatus;
import com.qwikride.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MembershipService {
    private final UserRepository userRepository;

    public MembershipStatus resolveMembership(Long userId) {
        return userRepository.findById(userId)
                .map(User::getMembershipStatus)
                .orElse(MembershipStatus.ENTRY);
    }
}
