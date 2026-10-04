package uz.app.starter.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.starter.audit.AuditEvent;
import uz.app.starter.audit.AuditService;
import uz.app.starter.common.exception.NotFoundException;
import uz.app.starter.rbac.Permissions;
import uz.app.starter.user.dto.UserResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AuditService auditService;

    @PreAuthorize(Permissions.CAN_READ_USER + " or #id == authentication.principal.id")
    public UserResponse getById(Long id) {
        return userRepository.findById(id)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Foydalanuvchi", id));
    }

    @PreAuthorize(Permissions.CAN_READ_USER)
    public List<UserResponse> getAll() {
        return userMapper.toResponseList(userRepository.findAll());
    }

    @PreAuthorize(Permissions.CAN_READ_USER)
    public UserResponse findByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Foydalanuvchi", email));
    }

    @PreAuthorize(Permissions.CAN_DELETE_USER)
    @Transactional
    public void delete(Long id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Foydalanuvchi", id));

        auditService.event(AuditEvent.USER_DELETED).target("USER", id).record();
        userRepository.delete(user);
    }
}
