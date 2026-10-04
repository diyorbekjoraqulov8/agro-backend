package uz.app.starter.rbac;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByName(String name);

    Set<Permission> findAllByNameIn(Collection<String> names);

    boolean existsByName(String name);
}
