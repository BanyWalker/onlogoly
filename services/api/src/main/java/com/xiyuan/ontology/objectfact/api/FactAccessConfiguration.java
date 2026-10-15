package com.xiyuan.ontology.objectfact.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Local role bridge (DEV-OF-1), to be replaced by authenticated authorities. */
@Configuration
public class FactAccessConfiguration implements WebMvcConfigurer {
	@Override public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new HandlerInterceptor() {
			@Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
				if (!java.util.Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod()) && !"MODELER".equals(request.getHeader("X-Xiyuan-Role")))
					throw new FactException(403, "OF_FORBIDDEN", "此操作仅建模人员可执行");
				return true;
			}
		}).addPathPatterns("/api/v1/facts/**");
	}
}
