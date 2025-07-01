package tracego.tracegoserver.repository;

import tracego.tracegoserver.entity.CartList;
import tracego.tracegoserver.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartListRepository extends JpaRepository<CartList, Long> {
    CartList findByUser(User user);
}
