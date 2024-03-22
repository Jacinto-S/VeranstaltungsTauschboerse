package team.boerse.tauschboerse.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.headers.FrameOptionsDsl;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import team.boerse.tauschboerse.UserRepository;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    @Autowired
    UserRepository userRepository;

    @Bean
    DefaultSecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Deaktiviere csrf. SameSite=Strict löst das csrf Problem. Die Cookie
        // Eigenschaft wird von ca. 99% aller Browser(versionen) nach Nutzung
        // unterstützt. Authentifizierte Requests sind gegen csrf geschützt.
        http.csrf(o -> o.disable());
        http.authorizeHttpRequests(
                (requests) -> requests.requestMatchers("/**")
                        .permitAll());

        http.addFilterBefore(CustomTokenFilter(), UsernamePasswordAuthenticationFilter.class);

        http.cors(o -> o.configurationSource(corsConfigurationSource()));
        return http.build();
    }

    @Bean
    public CustomTokenFilter CustomTokenFilter() {
        return new CustomTokenFilter();
    }

    @Value(value = "${cors.allowedOrigins:*}")
    private String allowedOrigins;

    @Bean
    CorsConfigurationSource corsConfigurationSource() {

        if (allowedOrigins == null || allowedOrigins.equals("*")) {
            allowedOrigins = "http://localhost:5173";
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("Content-Type"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;

    }

}