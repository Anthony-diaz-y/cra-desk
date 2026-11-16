package main

import (
	"os"

	"github.com/Anthony-diaz-y/cra-desk/cli/internal/app"
)

var version = "dev"

// main ejecuta la CLI con los argumentos y la entrada/salida del proceso y termina con su código de salida.
func main() {
	os.Exit(app.Run(app.Env{
		Args:    os.Args[1:],
		Stdin:   os.Stdin,
		Stdout:  os.Stdout,
		Stderr:  os.Stderr,
		Getenv:  os.Getenv,
		Version: version,
	}))
}
