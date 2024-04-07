package team.boerse.tauschboerse.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
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
                requests -> requests.requestMatchers("/**", "/assets/**")
                        .permitAll());

        http.addFilterBefore(customTokenFilter(), UsernamePasswordAuthenticationFilter.class);

        http.cors(o -> o.configurationSource(corsConfigurationSource()));
        return http.build();
    }

    @Bean
    CustomTokenFilter customTokenFilter() {
        return new CustomTokenFilter();
    }

    @Value(value = "${cors.allowedOrigins:*}")
    private String allowedOrigins;

    @Bean
    CorsConfigurationSource corsConfigurationSource() {

        if (allowedOrigins == null || allowedOrigins.equals("*")) {
            allowedOrigins = "http://localhost:5173,http://192.168.178.28:5173,https://tauschboerse.nkwebservices.de";
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