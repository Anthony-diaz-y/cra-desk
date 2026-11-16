package config

import (
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
)

type Config struct {
	APIURL string `json:"apiUrl"`
	APIKey string `json:"apiKey"`
}

// DefaultPath devuelve la ruta del archivo de configuración dentro de la carpeta de configuración del usuario.
func DefaultPath() (string, error) {
	dir, err := os.UserConfigDir()
	if err != nil {
		return "", fmt.Errorf("cannot find the user config directory: %w", err)
	}
	return filepath.Join(dir, "cradesk", "config.json"), nil
}

// Load lee el archivo si existe y aplica encima las variables CRADESK_API_URL y CRADESK_API_KEY.
func Load(path string, getenv func(string) string) (Config, error) {
	var c Config
	data, err := os.ReadFile(path)
	switch {
	case err == nil:
		if err := json.Unmarshal(data, &c); err != nil {
			return Config{}, fmt.Errorf("config file %s is not valid JSON: %w", path, err)
		}
	case errors.Is(err, fs.ErrNotExist):
	default:
		return Config{}, fmt.Errorf("cannot read config file %s: %w", path, err)
	}
	if v := getenv("CRADESK_API_URL"); v != "" {
		c.APIURL = v
	}
	if v := getenv("CRADESK_API_KEY"); v != "" {
		c.APIKey = v
	}
	c.APIURL = strings.TrimRight(strings.TrimSpace(c.APIURL), "/")
	c.APIKey = strings.TrimSpace(c.APIKey)
	return c, nil
}

// Save escribe la configuración con permisos 0600 en una carpeta 0700, porque contiene la clave de API.
func Save(path string, c Config) error {
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return fmt.Errorf("cannot create config directory: %w", err)
	}
	data, err := json.MarshalIndent(c, "", "  ")
	if err != nil {
		return err
	}
	if err := os.WriteFile(path, data, 0o600); err != nil {
		return fmt.Errorf("cannot write config file %s: %w", path, err)
	}
	return os.Chmod(path, 0o600)
}

// Validate comprueba que haya URL con esquema http(s) y una clave de API con el prefijo crd_.
func (c Config) Validate() error {
	switch {
	case c.APIURL == "":
		return errors.New("API URL is not set (run 'cradesk login' or set CRADESK_API_URL)")
	case c.APIKey == "":
		return errors.New("API key is not set (run 'cradesk login' or set CRADESK_API_KEY)")
	case !strings.HasPrefix(c.APIURL, "http://") && !strings.HasPrefix(c.APIURL, "https://"):
		return errors.New("API URL must start with http:// or https://")
	case !strings.HasPrefix(c.APIKey, "crd_"):
		return errors.New("API key must start with crd_")
	}
	return nil
}
