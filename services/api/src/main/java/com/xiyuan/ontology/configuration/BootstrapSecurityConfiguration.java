package com.xiyuan.ontology.configuration;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration(proxyBeanMethods = false)
@Profile("!oidc")
class BootstrapSecurityConfiguration {

	@Bean
	SecurityFilterChain bootstrapSecurity(HttpSecurity http) throws Exception {
		// MVP 阶段放行 /api/**（无状态、禁用 CSRF）；身份认证与授权切面在后续版本补充
		// （用户确认：非功能与安全需求后置，见 TDD-OM-002 §13.1 MVP 范围）。
		return http
			.authorizeHttpRequests(requests -> requests
				// 保留原始错误状态，避免 /api/** 的 404/500 在 /error 分派时被改成 401。
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				.requestMatchers("/api/**").permitAll()
				.anyRequest().denyAll())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(AbstractHttpConfigurer::disable)
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.build();
	}

}
