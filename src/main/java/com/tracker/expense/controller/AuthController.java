package com.tracker.expense.controller;

import com.tracker.expense.model.Role;
import com.tracker.expense.model.User;
import com.tracker.expense.repository.UserRepository;
import com.tracker.expense.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/api/auth/login")
    public String login(
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            HttpServletResponse response,
            Model model) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );

            // Generate JSON Web Token
            String jwt = tokenProvider.generateToken(email);

            // Package JWT in a secure HttpOnly Cookie to authorize browser MVC page transitions
            Cookie cookie = new Cookie("jwt_token", jwt);
            cookie.setHttpOnly(true);
            cookie.setSecure(false); // Set to true if running behind TLS/SSL in staging
            cookie.setPath("/");
            cookie.setMaxAge(86400); // Expires in 24 hours
            response.addCookie(cookie);

            return "redirect:/dashboard";
        } catch (Exception e) {
            model.addAttribute("error", "Incorrect email or password credential matching.");
            return "login";
        }
    }

    @PostMapping("/api/auth/register")
    public String register(
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("baseCurrency") String baseCurrency,
            Model model) {
        if (userRepository.existsByEmail(email)) {
            model.addAttribute("error", "The specified email is already in use.");
            return "register";
        }

        User user = new User(
                email,
                passwordEncoder.encode(password),
                baseCurrency.toUpperCase(),
                Role.ROLE_USER
        );
        userRepository.save(user);

        model.addAttribute("success", "Registration successful! You may now log in.");
        return "login";
    }

    @GetMapping("/logout")
    public String logout(HttpServletResponse response) {
        // Clear cookies
        Cookie cookie = new Cookie("jwt_token", null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        
        return "redirect:/login?logout";
    }
}
