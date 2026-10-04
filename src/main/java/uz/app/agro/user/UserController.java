package uz.app.agro.user;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uz.app.agro.user.dto.UserResponse;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    public List<UserResponse> getAll() {
        return userService.getAll();
    }

    @GetMapping("/find-by-email")
    public UserResponse findByEmail(@RequestParam String email) {
        return userService.findByEmail(email);
    }

    @GetMapping("/{id}")
    public UserResponse getOne(@PathVariable Long id) {
        return userService.getById(id);
    }
}
