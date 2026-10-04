package uz.app.agro.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.user.UserRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final LoginProperties props;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFailure(String email) {
        userRepository.incrementFailedAttempts(email);
        userRepository.lockIfExceeded(
                email,
                LocalDateTime.now().plus(props.lockDuration()),
                props.maxAttempts());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSuccess(String email) {
        userRepository.resetFailedAttempts(email);
    }
}