package api

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"sort"
	"strings"
	"time"
)

const requestTimeout = 60 * time.Second

type Client struct {
	baseURL   string
	apiKey    string
	userAgent string
	http      *http.Client
}

type Me struct {
	Email            string `json:"email"`
	OrganizationID   string `json:"organizationId"`
	OrganizationName string `json:"organizationName"`
	Role             string `json:"role"`
}

type SbomSummary struct {
	ID                    string `json:"id"`
	ProductID             string `json:"productId"`
	VersionID             string `json:"versionId"`
	SpecVersion           string `json:"specVersion"`
	ComponentCount        int    `json:"componentCount"`
	ComponentsWithoutPurl int    `json:"componentsWithoutPurl"`
	Sha256                string `json:"sha256"`
}

type Vulnerabilities struct {
	Status string          `json:"status"`
	Error  string          `json:"error"`
	Items  []Vulnerability `json:"items"`
}

type Vulnerability struct {
	ID               string   `json:"id"`
	Aliases          []string `json:"aliases"`
	Severity         string   `json:"severity"`
	Kev              bool     `json:"kev"`
	ComponentName    string   `json:"componentName"`
	ComponentVersion string   `json:"componentVersion"`
}

type APIError struct {
	Status int               `json:"status"`
	Title  string            `json:"title"`
	Detail string            `json:"detail"`
	Errors map[string]string `json:"errors"`
}

// Error arma una línea con estado, título y detalle, y una línea por cada error de campo ordenado.
func (e *APIError) Error() string {
	var b strings.Builder
	fmt.Fprintf(&b, "%d %s", e.Status, e.Title)
	if e.Detail != "" {
		fmt.Fprintf(&b, ": %s", e.Detail)
	}
	fields := make([]string, 0, len(e.Errors))
	for field := range e.Errors {
		fields = append(fields, field)
	}
	sort.Strings(fields)
	for _, field := range fields {
		fmt.Fprintf(&b, "\n  %s: %s", field, e.Errors[field])
	}
	return b.String()
}

// New crea un cliente de la API con timeout por petición y el proxy del entorno.
func New(baseURL, apiKey, userAgent string) *Client {
	return &Client{
		baseURL:   strings.TrimRight(baseURL, "/"),
		apiKey:    apiKey,
		userAgent: userAgent,
		http: &http.Client{
			Timeout:       requestTimeout,
			Transport:     &http.Transport{Proxy: http.ProxyFromEnvironment},
			CheckRedirect: doNotFollow,
		},
	}
}

// Me devuelve la empresa y el rol a los que pertenece la clave de API.
func (c *Client) Me(ctx context.Context) (Me, error) {
	var me Me
	_, err := c.do(ctx, http.MethodGet, "/api/auth/me", nil, "", &me)
	return me, err
}

// UploadSBOM sube el SBOM por nombre de producto y versión e indica si reemplazó uno anterior.
func (c *Client) UploadSBOM(ctx context.Context, product, version string, createVersion bool, body io.Reader) (SbomSummary, bool, error) {
	query := url.Values{}
	query.Set("product", product)
	query.Set("version", version)
	query.Set("createVersion", fmt.Sprint(createVersion))
	var summary SbomSummary
	status, err := c.do(ctx, http.MethodPut, "/api/sbom?"+query.Encode(), body, "application/vnd.cyclonedx+json", &summary)
	return summary, status == http.StatusOK, err
}

// VersionVulnerabilities devuelve el estado del escaneo y las vulnerabilidades de una versión.
func (c *Client) VersionVulnerabilities(ctx context.Context, productID, versionID string) (Vulnerabilities, error) {
	var v Vulnerabilities
	path := "/api/products/" + url.PathEscape(productID) + "/versions/" + url.PathEscape(versionID) + "/vulnerabilities"
	_, err := c.do(ctx, http.MethodGet, path, nil, "", &v)
	return v, err
}

// do envía la petición con la clave y decodifica la respuesta o la convierte en APIError.
func (c *Client) do(ctx context.Context, method, path string, body io.Reader, contentType string, out any) (int, error) {
	req, err := http.NewRequestWithContext(ctx, method, c.baseURL+path, body)
	if err != nil {
		return 0, fmt.Errorf("invalid request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+c.apiKey)
	req.Header.Set("User-Agent", c.userAgent)
	req.Header.Set("Accept", "application/json")
	if contentType != "" {
		req.Header.Set("Content-Type", contentType)
	}
	resp, err := c.http.Do(req)
	if err != nil {
		var urlErr *url.Error
		if errors.As(err, &urlErr) {
			err = urlErr.Err
		}
		return 0, fmt.Errorf("cannot reach CRA Desk at %s: %v", c.baseURL, err)
	}
	defer resp.Body.Close()
	data, err := io.ReadAll(io.LimitReader(resp.Body, 10<<20))
	if err != nil {
		return resp.StatusCode, fmt.Errorf("cannot read the response from CRA Desk: %v", err)
	}
	if resp.StatusCode >= 300 && resp.StatusCode < 400 {
		return resp.StatusCode, fmt.Errorf("CRA Desk redirected to %s; use that address as the API URL",
			resp.Header.Get("Location"))
	}
	if resp.StatusCode >= 400 {
		return resp.StatusCode, toAPIError(resp, data)
	}
	if out != nil && len(data) > 0 {
		if err := json.Unmarshal(data, out); err != nil {
			return resp.StatusCode, fmt.Errorf("unexpected response from CRA Desk: %v", err)
		}
	}
	return resp.StatusCode, nil
}

// doNotFollow evita seguir redirecciones, para no reenviar la clave ni convertir un PUT en GET.
func doNotFollow(*http.Request, []*http.Request) error {
	return http.ErrUseLastResponse
}

// toAPIError lee un problem details o, si el cuerpo no lo es, usa el texto del estado HTTP.
func toAPIError(resp *http.Response, data []byte) error {
	apiErr := &APIError{}
	if json.Unmarshal(data, apiErr) != nil || apiErr.Title == "" {
		apiErr = &APIError{}
	}
	apiErr.Status = resp.StatusCode
	if apiErr.Title == "" {
		apiErr.Title = http.StatusText(resp.StatusCode)
	}
	return apiErr
}
