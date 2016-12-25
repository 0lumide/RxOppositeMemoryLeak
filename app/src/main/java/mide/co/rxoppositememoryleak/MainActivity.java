package mide.co.rxoppositememoryleak;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subjects.BehaviorSubject;

import static android.widget.Toast.LENGTH_LONG;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    TextView mirroredCountdown;
    Subscription subscription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.button).setOnClickListener(this);
        mirroredCountdown = (TextView) findViewById(R.id.mirror);
    }

    @Override
    public void onClick(View v) {
        CountDownView countdownView = new CountDownView(this, 60);

        LeakCanaryApplication.getRefWatcher(this).watch(countdownView);
        Toast toast = new Toast(this);
        toast.setDuration(LENGTH_LONG);
        toast.setView(countdownView);
        subscription = ((CountDownView)toast.getView()).getCountdownObservable()
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((count) -> {
                    mirroredCountdown.setText(count);
                }, (throwable) -> {
                    Log.e("RxOppositeMemoryLeak", "CountDownView error");
                    throwable.printStackTrace();
                });
        toast.show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(subscription != null) {
            subscription.unsubscribe();
        }
    }
}

class CountDownView extends TextView {
    BehaviorSubject<String> countdownSubject;
    Subscription subscription;
    int count;

    public CountDownView(Context context) {
        this(context, 60);
    }

    public CountDownView(Context context, int count) {
        super(context);
        this.count = count;
        countdownSubject = BehaviorSubject.create();
        subscription = Observable.interval(1, TimeUnit.SECONDS)
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((elapsedTime) -> {
                    if(this.count >= 0) {
                        String num = this.count + "";
                        countdownSubject.onNext(num);
                        this.setText(num);
                        this.count--;
                    }
                    if(count < 0 ) {
                        countdownSubject.onCompleted();
                    }
                });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if(subscription != null) {
            subscription.unsubscribe();
        }
        if(!countdownSubject.hasCompleted()) {
            String msg = "View detached before observable completed";
            countdownSubject.onError(new IllegalStateException(msg));
        }
    }

    public Observable<String> getCountdownObservable() {
        return countdownSubject;
    }
}

