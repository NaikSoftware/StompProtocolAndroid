package ua.naiksoftware.stomp

import groovy.util.logging.Log4j
import groovy.util.logging.Slf4j
import io.reactivex.Flowable
import io.reactivex.annotations.NonNull
import io.reactivex.functions.Predicate
import io.reactivex.subscribers.TestSubscriber
import org.testcontainers.containers.GenericContainer
import spock.lang.Shared
import ua.naiksoftware.stomp.Configuration
import ua.naiksoftware.stomp.Stomp

import java.util.concurrent.TimeUnit

class ConnectionTests extends Configuration {

    def "connection must be opened"() {
        given:
        def client = Stomp.over(Stomp.ConnectionProvider.OKHTTP,
                'ws://' + Configuration.testServer.getContainerIpAddress()
                        + ':' + Configuration.testServer.getMappedPort(80) + '/example-endpoint/websocket')
        client.connect()
        def testSubscriber = new TestSubscriber<LifecycleEvent>()

        when:
        client.lifecycle().subscribe(testSubscriber)

        then:
        testSubscriber.awaitCount(1).assertValue((Predicate) { event ->
            if (event.exception) {
                event.exception.printStackTrace()
            }
            return event.type == LifecycleEvent.Type.OPENED
        })

        cleanup:
        client.disconnect()
    }
}
