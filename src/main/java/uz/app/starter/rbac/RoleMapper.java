package uz.app.starter.rbac;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import uz.app.starter.rbac.dto.RoleResponse;
import uz.app.starter.rbac.dto.RoleResponseWithPermission;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RoleMapper {

    RoleResponse toResponse(Role role);

    List<RoleResponse> toResponseList(List<Role> roles);

    RoleResponseWithPermission toResponseWithPermission(Role role);

    List<RoleResponseWithPermission> toResponseWithPermissionList(List<Role> roles);

}
