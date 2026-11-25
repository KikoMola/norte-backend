package com.norte.shared.api.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.norte.shared.domain.access.Permission;

/**
 * Permiso que exige un endpoint (paso 1.3). Sin él, 403 {@code auth.forbidden}. Todo método de un controlador lo
 * lleva, salvo los de sesión ({@code GET /me}); lo comprueba {@code AccessArchitectureTests}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresPermission {

	Permission value();

}
