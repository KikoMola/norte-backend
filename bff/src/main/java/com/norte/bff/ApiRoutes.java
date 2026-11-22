package com.norte.bff;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.removeRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.TokenRelayFilterFunctions.tokenRelay;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration(proxyBeanMethods = false)
class ApiRoutes {

	/** /api/** va al backend con el access token de la sesión como Bearer. La cookie no sale del BFF. */
	@Bean
	RouterFunction<ServerResponse> apiRoute(BffProperties properties) {
		return route("api")
			.route(path("/api/**"), http())
			.before(uri(properties.apiUri()))
			.before(removeRequestHeader(HttpHeaders.COOKIE))
			.filter(tokenRelay())
			.build();
	}

}
