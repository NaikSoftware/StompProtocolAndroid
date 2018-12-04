import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import spock.lang.Shared
import spock.lang.Specification
import ua.naiksoftware.test_server.Main

@ActiveProfiles(['test'])
@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = BaseContextLoader.class)
class BaseContextLoader extends Specification implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Shared
    static GenericContainer testServer

    @Override
    void initialize(ConfigurableApplicationContext configurableApplicationContext) {

        new ProcessBuilder('./gradlew bootJar')
                .start().waitForProcessOutput(System.out as Appendable, System.err as Appendable)

        testServer = new GenericContainer('openjdk:8-jre-alpine')
                .withClasspathResourceMapping('./build/artifacts/test-server-1.0.jar', '/app.jar', BindMode.READ_ONLY)
                .withCommand('java -jar /app.jar')
                .waitingFor(Wait.forHttp('/health'))
                .withExposedPorts(80)
    }
}
