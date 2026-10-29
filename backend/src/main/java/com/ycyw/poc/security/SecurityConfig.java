package com.ycyw.poc.security;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() {
        var encoder = new Pbkdf2PasswordEncoder("",16,600000,256);
        encoder.setAlgorithm(Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        return encoder;
    }
    @Bean UserDetailsService users(JdbcTemplate db) {
        return username -> {
            var rows = db.query("SELECT email,password_hash,role FROM account WHERE email=? AND active=true",
                (rs,n)-> User.withUsername(rs.getString(1)).password(rs.getString(2)).roles(rs.getString(3)).build(),username);
            if(rows.isEmpty()) throw new UsernameNotFoundException("Accès refusé");
            return rows.getFirst();
        };
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        // A dedicated same-origin API uses the plain token supplied in X-XSRF-TOKEN.
        var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");
        http.csrf(c -> c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/v1/csrf","/api/v1/health","/","/index.html","/*.js","/*.css","/favicon.ico").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .formLogin(f -> f.loginProcessingUrl("/api/v1/session")
                .successHandler((req,res,auth)-> {res.setContentType("application/json"); res.getWriter().write("{\"ok\":true}");})
                .failureHandler((req,res,e)-> {res.setStatus(401); res.setContentType("application/json");res.getWriter().write("{\"code\":\"INVALID_CREDENTIALS\",\"title\":\"Identifiants invalides\"}");}))
            .logout(l -> l.logoutUrl("/api/v1/logout").invalidateHttpSession(true).deleteCookies("YCYWSESSION","XSRF-TOKEN")
                .logoutSuccessHandler((req,res,a)->res.setStatus(204)))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req,res,x)->{res.setStatus(401);res.setContentType("application/json");res.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"title\":\"Connexion requise\"}");})
                .accessDeniedHandler((req,res,x)->{res.setStatus(403);res.setContentType("application/json");res.getWriter().write("{\"code\":\"FORBIDDEN\",\"title\":\"Action refusée\"}");}))
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'")))
            .sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()));
        return http.build();
    }
}
