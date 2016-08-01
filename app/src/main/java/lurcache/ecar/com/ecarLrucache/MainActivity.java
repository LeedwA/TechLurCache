package lurcache.ecar.com.ecarLrucache;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import lurcache.ecar.com.lurcachelib.DiskLruCacheUtils;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        DiskLruCacheUtils ut=new  DiskLruCacheUtils(getApplication(),"test",2);

    }
}
