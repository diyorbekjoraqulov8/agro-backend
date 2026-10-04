package uz.app.starter.user;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import uz.app.starter.rbac.Role;
import uz.app.starter.user.dto.UserRequest;
import uz.app.starter.user.dto.UserResponse;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {

    UserResponse toResponse(UserEntity entity);

    List<UserResponse> toResponseList(List<UserEntity> entities);

    default Set<String> rolesToNames(Set<Role> roles) {
        return roles == null ? Set.of()
                : roles.stream().map(Role::getName).collect(Collectors.toSet());
    }

    @Mapping(target = "id",          ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "updatedDate", ignore = true)
    @Mapping(target = "deleted",     ignore = true)
    @Mapping(target = "password",    ignore = true)
    @Mapping(target = "roles",       ignore = true)
    @Mapping(target = "active",      ignore = true)
    @Mapping(target = "failedAttempts",  ignore = true)
    @Mapping(target = "lockedUntil",     ignore = true)
    UserEntity toEntity(UserRequest request);
}
