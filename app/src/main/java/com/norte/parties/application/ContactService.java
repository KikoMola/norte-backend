package com.norte.parties.application;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.norte.parties.domain.Contact;
import com.norte.parties.domain.Contact.Details;
import com.norte.parties.domain.Contact.Role;
import com.norte.parties.domain.ContactRepository;
import com.norte.shared.application.TenantContext;
import com.norte.shared.application.validation.Violations;
import com.norte.shared.domain.error.NotFoundException;
import com.norte.shared.domain.error.StaleVersionException;
import com.norte.shared.domain.error.ValidationException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contactos de una empresa (paso 1.10). La empresa tiene que existir en el tenant: si no, 404. */
@Service
public class ContactService {

	/** Una empresa tiene pocos contactos: se devuelven todos, pero con un tope por si acaso. */
	public static final int MAX_PER_COMPANY = 200;

	private final ContactRepository contacts;

	private final CompanyService companies;

	private final Clock clock;

	ContactService(ContactRepository contacts, CompanyService companies, Clock clock) {
		this.contacts = contacts;
		this.companies = companies;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<Contact> list(UUID companyId) {
		companies.get(companyId);
		return contacts.findByCompanyIdOrderByFirstNameAscLastNameAscIdAsc(companyId);
	}

	@Transactional(readOnly = true)
	public Contact get(UUID companyId, UUID contactId) {
		companies.get(companyId);
		return contacts.findByIdAndCompanyId(contactId, companyId)
			.orElseThrow(() -> new NotFoundException("contact.not_found", "No existe el contacto"));
	}

	@Transactional
	public Contact create(UUID companyId, ContactInput input) {
		Details details = validate(input);
		companies.get(companyId);
		if (contacts.countByCompanyId(companyId) >= MAX_PER_COMPANY) {
			throw ValidationException.of("companyId", "contact.limit",
					"Una empresa no puede tener más de " + MAX_PER_COMPANY + " contactos.");
		}
		return contacts.saveAndFlush(Contact.create(TenantContext.requireTenantId(), companyId, details, clock.instant()));
	}

	@Transactional
	public Contact update(UUID companyId, UUID contactId, ContactInput input, long expectedVersion) {
		Details details = validate(input);
		Contact contact = get(companyId, contactId);
		StaleVersionException.check(expectedVersion, contact.version());
		contact.update(details, clock.instant());
		return contacts.saveAndFlush(contact);
	}

	@Transactional
	public void delete(UUID companyId, UUID contactId) {
		contacts.delete(get(companyId, contactId));
	}

	static Details validate(ContactInput input) {
		var check = new Violations();
		String firstName = check.required("firstName", input.firstName(), 100);
		String lastName = check.optional("lastName", input.lastName(), 100);
		String jobTitle = check.optional("jobTitle", input.jobTitle(), 100);
		String email = check.email("email", input.email());
		String phone = check.phone("phone", input.phone());
		Optional<Role> role = Optional.empty();
		if (input.role() != null && !input.role().isBlank()) {
			role = Role.fromCode(input.role());
			if (role.isEmpty()) {
				check.add("role", "contact.role.invalid", "La función tiene que ser una de "
						+ Arrays.stream(Role.values()).map(Role::code).toList() + ".");
			}
		}
		check.throwIfAny();
		return new Details(firstName, lastName, jobTitle, email, phone, role);
	}

	/** Datos de un contacto tal como llegan, sin validar. {@code role} es el código del contrato o {@code null}. */
	public record ContactInput(String firstName, String lastName, String jobTitle, String email, String phone,
			String role) {
	}

}
