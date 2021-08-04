package ua.naiksoftware.stomp

import io.reactivex.rxjava3.functions.Predicate
import io.reactivex.rxjava3.subscribers.TestSubscriber
import org.junit.Test
import ua.naiksoftware.stomp.dto.LifecycleEvent

class ConnectionTests extends Configuration {

    @Test
    def "connection must be opened"() {
        given:
        def client = Stomp.over(Stomp.ConnectionProvider.OKHTTP,
                'ws://' + Configuration.testServer.getContainerIpAddress()
                        + ':' + Configuration.testServer.getFirstMappedPort() + '/example-endpoint/websocket')
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
