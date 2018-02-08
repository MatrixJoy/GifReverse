package aidchow.com.gifreverse;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import aidchow.com.gifreverse.gifencoder.AnimatedGifEncoder;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final int WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 1;
    private static final int CHOOSEPHOTO_REQUEST_CODE = 20002;

    private ImageView mOriginView;
    private ImageView mReverseView;
    private View mLoadingView;
    private TextView mLoadingText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mOriginView = findViewById(R.id.original_img);
        mReverseView = findViewById(R.id.reverse_img);
        mLoadingView = findViewById(R.id.loading_layout);
        mLoadingText = findViewById(R.id.loading_text);
        mOriginView.setOnClickListener(this);
    }

    @SuppressLint("StaticFieldLeak")
    @Override
    protected void onActivityResult(final int requestCode, int resultCode, Intent data) {
        if (requestCode == CHOOSEPHOTO_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                String imagePath = handleImagePath(this, data);
                Glide.with(this).load(imagePath).into(mOriginView);
                mReverseView.setVisibility(View.GONE);
                mLoadingView.setVisibility(View.VISIBLE);
                new AsyncTask<String, Integer, String>() {
                    @Override
                    protected String doInBackground(String... strings) {
                        if (strings != null && strings.length > 0) {
                            GifDecoder decoder = new GifDecoder();
                            try {
                                InputStream inputStream = new FileInputStream(strings[0]);
                                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                                int size = 0;
                                byte[] data = new byte[16 * 1024];
                                while ((size = inputStream.read(data, 0, data.length)) != -1) {
                                    outputStream.write(data, 0, size);
                                }
                                outputStream.flush();
                                int status = decoder.read(outputStream.toByteArray());
                                if (status == GifDecoder.STATUS_OK) {
                                    List<Bitmap> bitmaps = new ArrayList<>();
                                    List<Integer> delays = new ArrayList<>();
                                    for (int i = decoder.getFrameCount(); i > 0; --i) {
                                        decoder.advance();
                                        Bitmap bitmap = Bitmap.createBitmap(decoder.getNextFrame());
                                        bitmaps.add(bitmap);
                                        delays.add(decoder.getDelay(i));
                                    }
                                    Collections.reverse(bitmaps);
                                    Collections.reverse(delays);
                                    AnimatedGifEncoder encoder = new AnimatedGifEncoder();
                                    String dirPath =
                                            Environment.getExternalStorageDirectory()
                                                    .getAbsolutePath()
                                                    + "/DCIM/gifReverse";
                                    File dir = new File(dirPath);
                                    if (!dir.exists()) {
                                        dir.mkdirs();
                                    }
                                    String resultFilePath =
                                            dir.getAbsolutePath() + "/" + System.currentTimeMillis()
                                                    + ".gif";
                                    encoder.start(resultFilePath);
                                    for (int i = 0; i < bitmaps.size(); i++) {
                                        encoder.setDelay(delays.get(i));
                                        encoder.addFrame(bitmaps.get(i));
                                    }
                                    encoder.finish();
                                    return resultFilePath;
                                } else {
                                    return null;
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return null;
                        } else {
                            return null;
                        }
                    }

                    @Override
                    protected void onPostExecute(String result) {
                        mReverseView.setVisibility(View.VISIBLE);
                        mLoadingView.setVisibility(View.GONE);
                        if (result != null) {
                            Glide.with(MainActivity.this).load(result).into(mReverseView);
                            Toast.makeText(MainActivity.this, R.string.trans_code_success,
                                    Toast.LENGTH_SHORT).show();
                            MediaScannerConnection.scanFile(MainActivity.this, new String[]{result},
                                    new String[]{"image/gif"},
                                    new MediaScannerConnection.OnScanCompletedListener() {
                                        @Override
                                        public void onScanCompleted(String path, Uri uri) {
                                            //ignore
                                        }
                                    });
                        } else {
                            Glide.with(MainActivity.this).load(R.drawable.ic_broken_image).into(
                                    mReverseView);
                            Toast.makeText(MainActivity.this, R.string.trans_code_error,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }.execute(imagePath);
            }
        }
    }


    /**
     * handle the image path when from the file
     */
    private String handleImagePath(Context context, Intent data) {
        String imagePath = null;
        Uri uri = data.getData();
        if (uri == null) {
            return null;
        }
        if (DocumentsContract.isDocumentUri(context, uri)) {
            String docId = DocumentsContract.getDocumentId(uri);
            if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                String id = docId.split(":")[1];
                String selection = MediaStore.Images.Media._ID + "=" + id;
                imagePath = getImagePath(context, MediaStore.
                        Images.Media.EXTERNAL_CONTENT_URI, selection);
            } else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
                Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.parseLong(docId));
                imagePath = getImagePath(context, contentUri, null);
            }
        } else if ("content".equals(uri.getScheme())) {
            imagePath = getImagePath(context, uri, null);

        } else if ("file".equals(uri.getScheme())) {
            imagePath = uri.getPath();
        }
        return imagePath;
    }

    private String getImagePath(Context context, Uri uri, String selection) {
        String path = null;
        Cursor cursor = context.getContentResolver().query(uri,
                null, selection, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
            }
            cursor.close();
        }
        return path;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        if (requestCode == WRITE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openAlbum();
            } else {
                Toast.makeText(MainActivity.this, R.string.auth_fail,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onClick(View view) {
        if (view == mOriginView) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setMessage(R.string.permisstion_tips);
                        builder.setPositiveButton(R.string.access,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        ActivityCompat.requestPermissions(MainActivity.this,
                                                new String[]{
                                                        Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                                WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
                                    }
                                });
                        builder.setNegativeButton(R.string.refuse,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        Toast.makeText(MainActivity.this, R.string.auth_fail,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                        builder.create();
                        builder.show();
                    } else {
                        ActivityCompat.requestPermissions(this,
                                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
                    }
                } else {
                    openAlbum();
                }
            } else {
                openAlbum();
            }
        }
    }


    private void openAlbum() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/gif");
        startActivityForResult(intent, CHOOSEPHOTO_REQUEST_CODE);
    }
}
