package org.tensorflow.lite.examples.detection;

import android.net.Uri;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;

import java.io.File;

public class PDFViewActivity extends AppCompatActivity implements OnPageChangeListener, OnLoadCompleteListener {
    private static final String TAG = "PDFViewActivity";
    PDFView pdfView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdfview);

        Bundle bundle = getIntent().getExtras();
        String pdf_name = bundle.getString("PDF_File_Path");

        File path = new File(Environment.getExternalStorageDirectory() + "/RoadBounce/PDF");
        if (!path.isDirectory()) {
            path.mkdirs();
        }
        Log.i(TAG, "onCreate:" + pdf_name);
        File file = new File(path, pdf_name);

        pdfView = (PDFView) findViewById(R.id.pdf_view);
//        pdfView.fromUri(Uri.parse(pdf_path)).load();
//        pdfView.fromFile(file);
        pdfView.fromFile(file)
                .defaultPage(0)
                .enableSwipe(true)

                .swipeHorizontal(false)
                .onPageChange(this)
                .enableAnnotationRendering(true)
                .onLoad(this)
                .scrollHandle(new DefaultScrollHandle(this))
                .load();
        Toast.makeText(PDFViewActivity.this, pdf_name, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void loadComplete(int nbPages) {

    }

    @Override
    public void onPageChanged(int page, int pageCount) {

    }
}
