package com.foobnix.work;

import android.content.Context;
import android.graphics.RectF;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.Objects;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.dao2.FileMeta;
import com.foobnix.ext.CacheZipUtils;
import com.foobnix.model.AppData;
import com.foobnix.model.AppProfile;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.AppsConfig;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.model.BookCSS;
import com.foobnix.ui2.AppDB;

import org.ebookdroid.BookType;
import org.ebookdroid.common.bitmaps.BitmapRef;
import org.ebookdroid.core.codec.CodecContext;
import org.ebookdroid.core.codec.CodecDocument;
import org.ebookdroid.core.codec.CodecPage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class SelfTestWorker extends MessageWorker {

    public SelfTestWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }


    @Override
    public void doWorkInner() {
        try
        {
            AppProfile.init(getApplicationContext());
            AppProfile.syncTestFolder.mkdirs();
            File logFile = AppData.getTestFileName();
            logFile.delete();

            BufferedWriter out = new BufferedWriter(new FileWriter(logFile));


            List<FileMeta> all = AppDB.get().searchBy("", AppDB.SORT_BY.getByID(AppState.get().sortBy), AppState.get().isSortAsc);

            int w = Dips.screenWidth();
            int h = Dips.screenHeight();
            int s = BookCSS.get().fontSizeSp;
            int count = all.size();
            int n = 0;
            int errors = 0;

            writeLine(out, "ApplicationName: " + Apps.getApplicationName(getApplicationContext()));
            writeLine(out, "VersionName: " + Apps.getVersionName(getApplicationContext()));
            writeLine(out, "PackageName: " + Apps.getPackageName(getApplicationContext()));
            writeLine(out, "os.arch: " + System.getProperty("os.arch"));
            writeLine(out, "MUPDF_VERSION: " + AppsConfig.MUPDF_FZ_VERSION);
            writeLine(out, "Build.VERSION.SDK_INT: " + Build.VERSION.SDK_INT);
            writeLine(out, "Height x Width: " + Dips.screenHeight() + "x" + Dips.screenWidth());
            out.newLine();
            writeLine(out, "Build.MANUFACTURER: " + Build.MANUFACTURER);
            writeLine(out, "Build.PRODUCT: " + Build.PRODUCT);
            writeLine(out, "Build.DEVICE: " + Build.DEVICE);
            writeLine(out, "Build.BRAND: " + Build.BRAND);
            writeLine(out, "Build.MODEL: " + Build.MODEL);
            out.newLine();
            writeLine(out, "[CSS]");
            writeLine(out, BookCSS.get().toCssString().replace("}", "}\n"));
            writeLine(out, "[BookCSS]");
            writeLine(out, Objects.toJSONString(BookCSS.get()).replace(",", ",\n"));
            writeLine(out, "[AppState]");
            writeLine(out, Objects.toJSONString(AppState.get()).replace(",", ",\n"));


            writeLine(out, "Books: " + count);


            sendNotifyAll();

            for (FileMeta item : all) {
                n++;

                writeLine(out, item.getPath());

                if (TxtUtils.isEmpty(item.getPath())) {
                    writeLine(out, "Skip");
                    continue;
                }

                if (ExtUtils.isZip(item.getPath()) && !CacheZipUtils.isSingleAndSupportEntry(item.getPath()).first) {
                    writeLine(out, "Skip");
                    continue;
                }
                sendTextMessage("Test: " + n + "/" + count);
                try {
                    CodecContext codecContex = BookType.getCodecContextByPath(item.getPath());
                    CodecDocument codecDocument = codecContex.openDocument(item.getPath(), "");
                    int pageCount = codecDocument.getPageCount(w, h, s);
                    if (pageCount == 0) {
                        codecDocument.recycle();
                        writeLine(out, "Error");
                        errors++;
                        sendNotifyAll();
                        continue;
                    }
                    CodecPage page = codecDocument.getPage(pageCount / 2);
                    RectF rectF = new RectF(0, 0, 1f, 1f);
                    BitmapRef bitmapRef = page.renderBitmap(w, h, rectF, false);
                    bitmapRef.getBitmap().recycle();
                    page.getText();
                    page.getPageLinks();
                    page.getPageHTML();

                    if (!page.isRecycled()) {
                        page.recycle();
                    }

                    if (!BookType.DJVU.is(item.getPath())) {
                        codecDocument.recycle();
                    }
                } catch (Exception e) {
                    writeLine(out, "Error");
                    errors++;
                    sendNotifyAll();
                }

            }
            writeLine(out, "Errors: " + errors);
            writeLine(out, "Finish");
            out.close();
        } catch (Exception e) {
            LOG.e(e);
        }
        sendNotifyAll();
    }

    public void writeLine(BufferedWriter out, String line) throws IOException {
        LOG.d("Self-test", line);
        out.write(line);
        out.newLine();
        out.flush();
    }
}
