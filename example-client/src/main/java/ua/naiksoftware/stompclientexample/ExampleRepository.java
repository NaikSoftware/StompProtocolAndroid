package ua.naiksoftware.stompclientexample;

import retrofit2.http.POST;
import retrofit2.http.Query;
import rx.Observable;

/**
 * Created by Naik on 24.02.17.
 */
public interface ExampleRepository {

    @POST("hello-convert-and-send")
    Observable<Void> sendRestEcho(@Query("msg") String message);
}
