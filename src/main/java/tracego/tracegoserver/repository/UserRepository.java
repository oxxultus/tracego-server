package tracego.tracegoserver.repository;

import tracego.tracegoserver.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);  // 이메일로 사용자 찾기
}
