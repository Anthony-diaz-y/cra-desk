package config

import (
	"os"
	"path/filepath"
	"runtime"
	"testing"
)

// noEnv simula un entorno sin variables definidas.
func noEnv(string) string { return "" }

// TestLoadMissingFileReturnsEmpty comprueba que sin archivo se obtiene una configuración vacía sin error.
func TestLoadMissingFileReturnsEmpty(t *testing.T) {
	c, err := Load(filepath.Join(t.TempDir(), "missing.json"), noEnv)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if c != (Config{}) {
		t.Fatalf("expected empty config, got %+v", c)
	}
}

// TestEnvOverridesFile comprueba que las variables de entorno tienen prioridad sobre el archivo.
func TestEnvOverridesFile(t *testing.T) {
	path := filepath.Join(t.TempDir(), "config.json")
	if err := Save(path, Config{APIURL: "https://file.example", APIKey: "crd_file"}); err != nil {
		t.Fatal(err)
	}
	env := map[string]string{"CRADESK_API_URL": "https://env.example", "CRADESK_API_KEY": "crd_env"}

	c, err := Load(path, func(k string) string { return env[k] })

	if err != nil {
		t.Fatal(err)
	}
	if c.APIURL != "https://env.example" || c.APIKey != "crd_env" {
		t.Fatalf("env did not override file: %+v", c)
	}
}

// TestSaveThenLoadRoundTrip comprueba que lo guardado se vuelve a leer igual.
func TestSaveThenLoadRoundTrip(t *testing.T) {
	path := filepath.Join(t.TempDir(), "nested", "config.json")
	want := Config{APIURL: "https://cradesk.example", APIKey: "crd_abcdefgh_secret"}

	if err := Save(path, want); err != nil {
		t.Fatal(err)
	}
	got, err := Load(path, noEnv)

	if err != nil {
		t.Fatal(err)
	}
	if got != want {
		t.Fatalf("got %+v, want %+v", got, want)
	}
}

// TestSaveUsesPrivatePermissions comprueba que el archivo con la clave solo lo puede leer su dueño.
func TestSaveUsesPrivatePermissions(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("Unix permission bits do not apply on Windows")
	}
	path := filepath.Join(t.TempDir(), "config.json")
	if err := Save(path, Config{APIURL: "https://x.example", APIKey: "crd_x"}); err != nil {
		t.Fatal(err)
	}

	info, err := os.Stat(path)

	if err != nil {
		t.Fatal(err)
	}
	if info.Mode().Perm() != 0o600 {
		t.Fatalf("expected 0600, got %v", info.Mode().Perm())
	}
}

// TestValidate comprueba los mensajes de error de una configuración incompleta o mal formada.
func TestValidate(t *testing.T) {
	cases := []struct {
		name    string
		config  Config
		wantErr string
	}{
		{"missing url", Config{APIKey: "crd_x"}, "API URL is not set (run 'cradesk login' or set CRADESK_API_URL)"},
		{"missing key", Config{APIURL: "https://x.example"}, "API key is not set (run 'cradesk login' or set CRADESK_API_KEY)"},
		{"url without scheme", Config{APIURL: "x.example", APIKey: "crd_x"}, "API URL must start with http:// or https://"},
		{"key without prefix", Config{APIURL: "https://x.example", APIKey: "abc"}, "API key must start with crd_"},
		{"valid", Config{APIURL: "https://x.example", APIKey: "crd_x"}, ""},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			err := tc.config.Validate()
			got := ""
			if err != nil {
				got = err.Error()
			}
			if got != tc.wantErr {
				t.Fatalf("got %q, want %q", got, tc.wantErr)
			}
		})
	}
}

// TestTrailingSlashIsRemoved comprueba que la barra final de la URL se quita al leer.
func TestTrailingSlashIsRemoved(t *testing.T) {
	c, err := Load(filepath.Join(t.TempDir(), "none.json"), func(k string) string {
		if k == "CRADESK_API_URL" {
			return "https://cradesk.example/"
		}
		return ""
	})

	if err != nil {
		t.Fatal(err)
	}
	if c.APIURL != "https://cradesk.example" {
		t.Fatalf("trailing slash kept: %q", c.APIURL)
	}
}

// TestDefaultPathEndsInCradeskConfig comprueba que la ruta por defecto está en la carpeta de configuración del usuario.
func TestDefaultPathEndsInCradeskConfig(t *testing.T) {
	path, err := DefaultPath()

	if err != nil {
		t.Fatal(err)
	}
	if filepath.Base(path) != "config.json" || filepath.Base(filepath.Dir(path)) != "cradesk" {
		t.Fatalf("unexpected default path %q", path)
	}
}
