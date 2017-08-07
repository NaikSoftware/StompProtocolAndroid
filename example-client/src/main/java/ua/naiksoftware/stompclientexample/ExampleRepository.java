package ua.naiksoftware.stompclientexample;

import io.reactivex.Flowable;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Created by Naik on 24.02.17.
 */
public interface ExampleRepository {

    @POST("hello-convert-and-send")
    Flowable<Void> sendRestEcho(@Query("msg") String message);
}
