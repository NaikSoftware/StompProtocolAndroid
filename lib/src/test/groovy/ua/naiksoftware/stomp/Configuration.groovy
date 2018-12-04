package ua.naiksoftware.stomp

import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import spock.lang.Shared
import spock.lang.Specification

class Configuration extends Specification implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Shared
    static GenericContainer testServer = setupServer()

    GenericContainer setupServer() {

        new ProcessBuilder('./gradlew bootJar')
                .start().waitForProcessOutput(System.out as Appendable, System.err as Appendable)

        return new GenericContainer('openjdk:8-jre-alpine')
                .withClasspathResourceMapping('./build/artifacts/test-server-1.0.jar', '/app.jar', BindMode.READ_ONLY)
                .withCommand('java -jar /app.jar')
                .waitingFor(Wait.forHttp('/health'))
                .withExposedPorts(80)
    }
}