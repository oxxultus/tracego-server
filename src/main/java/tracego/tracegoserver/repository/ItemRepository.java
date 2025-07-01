package tracego.tracegoserver.repository;

import tracego.tracegoserver.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {
    Optional<Item> findByUniqueValue(String uniqueValue);
}
