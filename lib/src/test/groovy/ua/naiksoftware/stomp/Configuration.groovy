package ua.naiksoftware.stomp

import com.andrewreitz.spock.android.AndroidSpecification
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import spock.lang.Shared

class Configuration extends AndroidSpecification {

    @Shared
    static GenericContainer testServer = setupServer()

    static GenericContainer setupServer() {

        def projectRoot = new File('../')
        new ProcessBuilder(['./gradlew', 'test-server:bootJar'])
                .directory(projectRoot)
                .start().waitForProcessOutput(System.out as Appendable, System.err as Appendable)

        def testServerPath = new File(projectRoot.getAbsoluteFile().getParentFile().getParent(),
                'test-server/build/artifacts/test-server-1.0.jar').path
        testServer = new GenericContainer('openjdk:8-jre-alpine')
                .withFileSystemBind(testServerPath, '/app.jar', BindMode.READ_ONLY)
                .withCommand('java -jar /app.jar')
                .withLogConsumer({ frame -> println frame.utf8String })
//                .waitingFor(Wait.forHttp('/health'))
                .withExposedPorts(8080)
        testServer.start()
        return testServer
    }
}