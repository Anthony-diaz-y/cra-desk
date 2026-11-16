package api

import (
	"context"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

const testKey = "crd_abcdefgh_supersecretvalue"

// newServer arranca un servidor de prueba que responde con el manejador indicado.
func newServer(t *testing.T, handler http.HandlerFunc) (*httptest.Server, *Client) {
	t.Helper()
	server := httptest.NewServer(handler)
	t.Cleanup(server.Close)
	return server, New(server.URL, testKey, "cradesk/test")
}

// TestMeSendsBearerAndUserAgent comprueba que Me envía la clave y el User-Agent y lee la respuesta.
func TestMeSendsBearerAndUserAgent(t *testing.T) {
	_, client := newServer(t, func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/auth/me" || r.Method != http.MethodGet {
			t.Errorf("unexpected request %s %s", r.Method, r.URL.Path)
		}
		if r.Header.Get("Authorization") != "Bearer "+testKey {
			t.Errorf("missing bearer, got %q", r.Header.Get("Authorization"))
		}
		if r.Header.Get("User-Agent") != "cradesk/test" {
			t.Errorf("unexpected user agent %q", r.Header.Get("User-Agent"))
		}
		w.Header().Set("Content-Type", "application/json")
		io.WriteString(w, `{"userId":"u1","email":null,"organizationId":"o1","organizationName":"Acme","role":"CLI"}`)
	})

	me, err := client.Me(context.Background())

	if err != nil {
		t.Fatal(err)
	}
	if me.OrganizationName != "Acme" || me.Role != "CLI" || me.OrganizationID != "o1" {
		t.Fatalf("unexpected me %+v", me)
	}
}

// TestUploadSendsQueryAndBody comprueba la URL, el tipo de contenido, el cuerpo y si fue reemplazo según 201 o 200.
func TestUploadSendsQueryAndBody(t *testing.T) {
	status := http.StatusCreated
	_, client := newServer(t, func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPut || r.URL.Path != "/api/sbom" {
			t.Errorf("unexpected request %s %s", r.Method, r.URL.Path)
		}
		q := r.URL.Query()
		if q.Get("product") != "Smart Lock" || q.Get("version") != "1.0.0" || q.Get("createVersion") != "true" {
			t.Errorf("unexpected query %v", q)
		}
		if r.Header.Get("Content-Type") != "application/vnd.cyclonedx+json" {
			t.Errorf("unexpected content type %q", r.Header.Get("Content-Type"))
		}
		body, _ := io.ReadAll(r.Body)
		if string(body) != `{"bomFormat":"CycloneDX"}` {
			t.Errorf("unexpected body %q", body)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(status)
		io.WriteString(w, `{"id":"s1","productId":"p1","versionId":"v1","specVersion":"1.5","componentCount":5,"componentsWithoutPurl":1,"sha256":"ab"}`)
	})

	summary, replaced, err := client.UploadSBOM(context.Background(), "Smart Lock", "1.0.0", true,
		strings.NewReader(`{"bomFormat":"CycloneDX"}`))

	if err != nil {
		t.Fatal(err)
	}
	if replaced || summary.VersionID != "v1" || summary.ComponentCount != 5 || summary.ComponentsWithoutPurl != 1 {
		t.Fatalf("unexpected result %+v replaced=%v", summary, replaced)
	}
	status = http.StatusOK
	_, replaced, err = client.UploadSBOM(context.Background(), "Smart Lock", "1.0.0", true, strings.NewReader(`{"bomFormat":"CycloneDX"}`))
	if err != nil || !replaced {
		t.Fatalf("expected replaced=true, got %v err=%v", replaced, err)
	}
}

// TestVersionVulnerabilities comprueba que se leen el estado del escaneo y las vulnerabilidades.
func TestVersionVulnerabilities(t *testing.T) {
	_, client := newServer(t, func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/products/p1/versions/v1/vulnerabilities" {
			t.Errorf("unexpected path %s", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		io.WriteString(w, `{"status":"DONE","error":null,"items":[{"id":"GHSA-jfh8-c2jp-5v3q","aliases":["CVE-2021-44228"],"severity":"CRITICAL","kev":true,"componentName":"log4j-core","componentVersion":"2.14.1"}]}`)
	})

	v, err := client.VersionVulnerabilities(context.Background(), "p1", "v1")

	if err != nil {
		t.Fatal(err)
	}
	if v.Status != "DONE" || len(v.Items) != 1 || !v.Items[0].Kev || v.Items[0].Aliases[0] != "CVE-2021-44228" {
		t.Fatalf("unexpected vulnerabilities %+v", v)
	}
}

// TestProblemDetailsBecomeAPIError comprueba que una respuesta de error RFC 9457 se convierte en APIError legible.
func TestProblemDetailsBecomeAPIError(t *testing.T) {
	_, client := newServer(t, func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/problem+json")
		w.WriteHeader(http.StatusBadRequest)
		io.WriteString(w, `{"title":"Invalid request","status":400,"detail":"Not a valid CycloneDX JSON SBOM","errors":{"specVersion":"must be one of 1.4, 1.5, 1.6","bomFormat":"must be CycloneDX"}}`)
	})

	_, _, err := client.UploadSBOM(context.Background(), "Smart Lock", "1.0.0", true, strings.NewReader("{}"))

	var apiErr *APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("expected APIError, got %v", err)
	}
	if apiErr.Status != 400 || apiErr.Errors["bomFormat"] != "must be CycloneDX" {
		t.Fatalf("unexpected APIError %+v", apiErr)
	}
	want := "400 Invalid request: Not a valid CycloneDX JSON SBOM\n  bomFormat: must be CycloneDX\n  specVersion: must be one of 1.4, 1.5, 1.6"
	if err.Error() != want {
		t.Fatalf("got %q, want %q", err.Error(), want)
	}
}

// TestNonJSONErrorStillBecomesAPIError comprueba que un error que no es problem details igual se informa con su estado.
func TestNonJSONErrorStillBecomesAPIError(t *testing.T) {
	_, client := newServer(t, func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadGateway)
		io.WriteString(w, "<html>bad gateway</html>")
	})

	_, err := client.Me(context.Background())

	var apiErr *APIError
	if !errors.As(err, &apiErr) || apiErr.Status != 502 {
		t.Fatalf("expected APIError 502, got %v", err)
	}
	if err.Error() != "502 Bad Gateway" {
		t.Fatalf("unexpected message %q", err.Error())
	}
}

// TestUnreachableServer comprueba que un servidor inaccesible da un error claro con la URL.
func TestUnreachableServer(t *testing.T) {
	server := httptest.NewServer(http.NotFoundHandler())
	url := server.URL
	server.Close()
	client := New(url, testKey, "cradesk/test")

	_, err := client.Me(context.Background())

	if err == nil || !strings.HasPrefix(err.Error(), "cannot reach CRA Desk at "+url) {
		t.Fatalf("unexpected error %v", err)
	}
}

// TestErrorsNeverContainTheKey comprueba que ningún mensaje de error incluye la clave de API.
func TestErrorsNeverContainTheKey(t *testing.T) {
	_, client := newServer(t, func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/problem+json")
		w.WriteHeader(http.StatusUnauthorized)
		io.WriteString(w, `{"title":"Unauthorized","status":401,"detail":"Invalid API key"}`)
	})

	_, err := client.Me(context.Background())

	if err == nil || strings.Contains(err.Error(), "supersecret") {
		t.Fatalf("error leaks the key or is nil: %v", err)
	}
}
