package org.example.userauthenticationservice.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import org.example.userauthenticationservice.exceptions.UserAlreadyExistsException;
import org.example.userauthenticationservice.exceptions.UserNotFoundException;
import org.example.userauthenticationservice.exceptions.WrongPasswordException;
import org.example.userauthenticationservice.models.Session;
import org.example.userauthenticationservice.models.SessionState;
import org.example.userauthenticationservice.models.User;
import org.example.userauthenticationservice.repositories.SessionRepository;
import org.example.userauthenticationservice.repositories.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class UserAuthenticationService implements IAuthService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final SessionRepository sessionRepository;
    private final SecretKey secretKey;

    public UserAuthenticationService(
            UserRepository userRepository,
            BCryptPasswordEncoder bCryptPasswordEncoder,
            SecretKey secretKey,
            SessionRepository sessionRepository
    ) {
        this.userRepository = userRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.secretKey = secretKey;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public boolean signUp(String email, String password) throws UserAlreadyExistsException {
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isPresent()) {
            throw new UserAlreadyExistsException("User already exists");
        }

        User newUser = new User();
        newUser.setEmail(email);
        newUser.setPassword(bCryptPasswordEncoder.encode(password));
        userRepository.save(newUser);
        return true;
    }

    @Override
    public String login(String email, String password) throws UserNotFoundException, WrongPasswordException {
        Optional<User> user = userRepository.findByEmail(email);
        User foundUser;

        if(user.isEmpty()) {
            throw new UserNotFoundException("User not found");
        }

        foundUser = user.get();

        if(!bCryptPasswordEncoder.matches(password, foundUser.getPassword())) {
            throw new WrongPasswordException("Wrong password");
        }

        //check the current time stamp and compare with session and then mark entry as
        //active or expired: TODO

        //JWT Token Generation
        Map<String, Object> claims = new HashMap<>();
        long currentTime = System.currentTimeMillis();
        claims.put("iat", currentTime);
        claims.put("exp", currentTime + 2592000);
        claims.put("user_id", foundUser.getId());
        claims.put("issuer", "scaler");

        String jwtToken = Jwts.builder().claims(claims).signWith(this.secretKey).compact();

        Session session = new Session();
        session.setToken(jwtToken);
        session.setSessionState(SessionState.ACTIVE);
        session.setUser(foundUser);

        sessionRepository.save(session);
        return jwtToken;
    }

    @Override
    public Boolean validateToken(Long userId, String token) {
        Optional<Session> optionalSession = sessionRepository.findByTokenAndUserId(token, userId);

        if(optionalSession.isEmpty()) {
            return false;
        }

        JwtParser jwtParser = Jwts.parser().verifyWith(this.secretKey).build();
        Claims claims = jwtParser.parseSignedClaims(token).getPayload();

        long expiry = (long)claims.get("exp");
        long now = System.currentTimeMillis();

        if(now > expiry) {
            Session session = optionalSession.get();
            session.setSessionState(SessionState.EXPIRED);
            sessionRepository.save(session);
            return false;
        }
        return true;
    }
}
