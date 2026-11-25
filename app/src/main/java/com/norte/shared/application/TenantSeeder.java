package com.norte.shared.application;

import java.util.UUID;

/**
 * Datos iniciales de un módulo para un tenant nuevo: series de facturación, tipos de IVA, formas de pago… Cada módulo
 * implementa el suyo como bean y el alta de tenant los llama todos (paso 1.1).
 *
 * <p>Se ejecuta dentro de la transacción del alta, con el tenant ya fijado para RLS, y tiene que ser idempotente: el
 * alta se puede repetir y entonces no debe duplicar nada.
 */
public interface TenantSeeder {

	/** @param tenantName nombre del tenant en el alta; sirve de valor inicial (la razón social de los ajustes) */
	void seed(UUID tenantId, String tenantName);

}
