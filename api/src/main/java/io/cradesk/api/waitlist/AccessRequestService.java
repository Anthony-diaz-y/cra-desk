package io.cradesk.api.waitlist;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cradesk.api.common.Constraints;
import io.cradesk.api.identity.Emails;

@Service
public class AccessRequestService {

    private final AccessRequestRepository requests;

    // Recibe el repositorio de solicitudes por inyección de dependencias.
    public AccessRequestService(AccessRequestRepository requests) {
        this.requests = requests;
    }

    // Guarda la solicitud si el email no estaba ya en la lista; un duplicado se ignora sin error.
    @Transactional
    public void submit(AccessRequestForm form) {
        String email = Emails.normalize(form.email());
        if (requests.existsByEmail(email)) {
            return;
        }
        try {
            requests.saveAndFlush(new AccessRequest(email, blankToNull(form.company()), blankToNull(form.message())));
        } catch (DataIntegrityViolationException e) {
            if (!Constraints.isViolated(e, AccessRequest.EMAIL_UNIQUE)) {
                throw e;
            }
        }
    }

    // Lista todas las solicitudes para el admin, de la más reciente a la más antigua.
    @Transactional(readOnly = true)
    public List<AccessRequestResponse> list() {
        return requests.findAllByOrderByCreatedAtDesc().stream()
                .map(r -> new AccessRequestResponse(r.getEmail(), r.getCompany(), r.getMessage(), r.getCreatedAt()))
                .toList();
    }

    // Convierte un texto vacío o solo con espacios en null y recorta los demás.
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
