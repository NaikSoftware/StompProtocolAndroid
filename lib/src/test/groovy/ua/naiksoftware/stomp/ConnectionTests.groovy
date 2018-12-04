package ua.naiksoftware.stomp

import ua.naiksoftware.stomp.Configuration
import ua.naiksoftware.stomp.Stomp

class ConnectionTests extends Configuration {

    def "connection must be recreated when reconnect"() {
        given:
        def client = Stomp.over(Stomp.ConnectionProvider.OKHTTP, 'localhost/websocket')
        client.connect()

        when:
        client.disconnect()

        then:
        client.isConnected() == false
        client.isConnecting() == false
    }
}
