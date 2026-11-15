package io.cradesk.api.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "cradesk.admin.token=" + TestTenants.ADMIN_TOKEN)
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestTenants.class, FakeExternalFeeds.class })
public @interface IntegrationTest {
}
