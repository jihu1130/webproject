package com.webschool.webschool;

import com.webschool.webschool.user.entity.User;
import com.webschool.webschool.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// 개발용: 로그인 아이디(username)가 "admin"인 계정을 총관리자(ROLE_SUPER_ADMIN)로 승격시키는 시더.
// 앱 안에는 총관리자를 지정하는 UI/API가 없으므로(admin 계정 하나로 고정하는 설계) 이 시더로 DB를
// 직접 갱신한다. 이미 총관리자면 아무 것도 하지 않으므로 여러 번 실행해도 안전하다.
@SpringBootTest
class SuperAdminSeeder {

    private static final String SUPER_ADMIN_USERNAME = "admin";

    @Autowired
    private UserRepository userRepository;

    @Test
    void promoteAdminToSuperAdmin() {
        User admin = userRepository.findByUsername(SUPER_ADMIN_USERNAME)
                .orElseThrow(() -> new IllegalStateException(
                        "\"" + SUPER_ADMIN_USERNAME + "\" 계정이 없습니다. 먼저 /register에서 "
                                + "username=" + SUPER_ADMIN_USERNAME + " 계정을 만들어주세요."));

        if (admin.getRole() == User.Role.ROLE_SUPER_ADMIN) {
            return;
        }

        admin.setRole(User.Role.ROLE_SUPER_ADMIN);
        userRepository.save(admin);
    }
}
