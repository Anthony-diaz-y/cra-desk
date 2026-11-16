package io.cradesk.api.vulns;

import java.util.List;
import java.util.Map;

public interface OsvClient {

    // Consulta qué vulnerabilidades afectan a cada purl y devuelve, por purl, sus ids con la fecha de modificación.
    Map<String, List<OsvHit>> queryByPurl(List<String> purls);

    // Descarga el detalle de una vulnerabilidad por su id de OSV, o devuelve null si OSV ya no la tiene.
    OsvVulnerability fetch(String id);
}
