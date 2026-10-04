package uz.app.agro.rbac;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    @EntityGraph(attributePaths = "permissions")
    @Query("SELECT r FROM Role r")
    List<Role> findAllWithPermissions();

    Optional<Role> findByName(String name);

    boolean existsByName(String name);
}
