package io.cradesk.api.demo;

import java.time.LocalDate;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.cradesk.api.catalog.Product;
import io.cradesk.api.catalog.ProductClass;
import io.cradesk.api.catalog.ProductRepository;
import io.cradesk.api.catalog.ProductVersion;
import io.cradesk.api.catalog.ProductVersionRepository;
import io.cradesk.api.identity.AccessRole;
import io.cradesk.api.identity.DemoProperties;
import io.cradesk.api.identity.Organization;
import io.cradesk.api.identity.OrganizationRepository;
import io.cradesk.api.identity.UserAccount;
import io.cradesk.api.identity.UserAccountRepository;

@Component
public class DemoAccountInitializer implements ApplicationRunner {

    private final DemoProperties properties;
    private final OrganizationRepository organizations;
    private final UserAccountRepository users;
    private final ProductRepository products;
    private final ProductVersionRepository versions;
    private final TransactionTemplate transactions;

    // Recibe la configuración de la demo, los repositorios donde se crean sus datos y el gestor de transacciones.
    public DemoAccountInitializer(DemoProperties properties, OrganizationRepository organizations,
            UserAccountRepository users, ProductRepository products, ProductVersionRepository versions,
            TransactionTemplate transactions) {
        this.properties = properties;
        this.organizations = organizations;
        this.users = users;
        this.products = products;
        this.versions = versions;
        this.transactions = transactions;
    }

    // Al arrancar la aplicación crea la cuenta demo si la demo está activada.
    @Override
    public void run(ApplicationArguments args) {
        if (properties.enabled()) {
            seed();
        }
    }

    // Crea en una sola transacción los datos de la demo si todavía no existe el usuario demo.
    public void seed() {
        transactions.executeWithoutResult(status -> {
            if (users.existsByEmail(DemoProperties.EMAIL)) {
                return;
            }
            createDemoData();
        });
    }

    // Guarda la empresa demo, su usuario sin contraseña y dos productos con sus versiones.
    private void createDemoData() {
        Organization demo = organizations.save(new Organization(DemoProperties.ORGANIZATION_NAME));
        users.save(new UserAccount(demo, DemoProperties.EMAIL, null, AccessRole.DEMO));

        Product lock = products.save(new Product(demo.getId(), "Smart Door Lock",
                "Wi-Fi door lock with companion mobile app", ProductClass.IMPORTANT_CLASS_I));
        versions.save(new ProductVersion(lock, "1.0.0", LocalDate.of(2026, 6, 1)));
        versions.save(new ProductVersion(lock, "1.1.0", LocalDate.of(2026, 10, 1)));

        Product gateway = products.save(new Product(demo.getId(), "Home Gateway",
                "Zigbee and Thread home automation hub", ProductClass.DEFAULT));
        versions.save(new ProductVersion(gateway, "2.3.1", LocalDate.of(2026, 9, 15)));
    }
}
