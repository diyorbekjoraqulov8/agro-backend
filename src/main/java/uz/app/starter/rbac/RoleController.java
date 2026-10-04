package uz.app.starter.rbac;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import uz.app.starter.rbac.dto.RoleResponse;
import uz.app.starter.rbac.dto.RoleResponseWithPermission;
import uz.app.starter.rbac.dto.RoleRequest;
import uz.app.starter.rbac.dto.RoleUpdateRequest;

import java.util.List;

@Tag(name = "Rollar", description = "Rol va huquqlarni boshqarish")
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@Validated
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "Rollar ro'yxati",
              description = "Talab qilinadigan huquq: `role:read`")
    @GetMapping
    public List<RoleResponse> getAll() {
        return this.roleService.getAll();
    }

    @GetMapping("/detailed")
    public List<RoleResponseWithPermission> getAllWithPermissions() {
        return this.roleService.getAllWithPermissions();
    }

    @GetMapping("/{id}")
    public RoleResponse findOne(@PathVariable Long id) {
        return this.roleService.getOne(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse create(@Valid @RequestBody RoleRequest body) { return this.roleService.create(body); }

    @PutMapping("/{id}")
    public RoleResponse update(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest body) {
        return this.roleService.update(id, body);
    }
}
