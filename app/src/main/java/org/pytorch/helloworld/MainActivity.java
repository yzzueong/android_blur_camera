package org.pytorch.helloworld;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;

public class MainActivity extends AppCompatActivity {
    private int TAKE_PHOTO = 0;
    private int CHOOSE_PHOTO = 3;
    private ImageView viewFinder = null;
    private Bitmap bitmap = null;
    private ImageSegmentationModelExecutor imageSegmentationModelExecutor = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageSegmentationModelExecutor = new ImageSegmentationModelExecutor(this, true);

        viewFinder = findViewById(R.id.view_finder);
        Button portraitmode = findViewById(R.id.portrait);
        Button proportraitmode = findViewById(R.id.proportrait);

        viewFinder.setOnClickListener(view -> {
          Intent intent = new Intent();
          intent.setClass(MainActivity.this, CameraActivity.class);
          startActivityForResult(intent, TAKE_PHOTO);
        });

        viewFinder.setOnLongClickListener(view -> {
          Intent intent = new Intent(Intent.ACTION_PICK, null);
          intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
          startActivityForResult(intent, CHOOSE_PHOTO);
          return true;
        });

        portraitmode.setOnClickListener(view -> {
          bitmap = portraitProcess();
          if (bitmap != null){
              viewFinder.setImageBitmap(bitmap);
              Toast.makeText(this, "portrait mode finash", Toast.LENGTH_SHORT).show();
          }
        });

        proportraitmode.setOnClickListener(view -> {
            bitmap = proPortraitProcess();
          if (bitmap != null){
              viewFinder.setImageBitmap(bitmap);
              Toast.makeText(this, " pro portrait mode finash", Toast.LENGTH_SHORT).show();
          }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0 && resultCode == 1){
            String filepath = data.getStringExtra("filename");
            Toast.makeText(this, filepath, Toast.LENGTH_SHORT).show();
            displayImageFromPath(filepath);
        }else if (requestCode == 3 && resultCode != 0){
            Uri url = data.getData();
            displayImageFromUri(url);
        }
    }

    private void displayImageFromUri(Uri url) {
        RequestBuilder temp = Glide.with(this).asBitmap().load(url);
        temp.into(viewFinder);
        temp.into((Target) new CustomTarget<Bitmap>(){

            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                bitmap = resource;
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {

            }
        });

    }

    private void displayImageFromPath(String filepath) {
        RequestBuilder temp = Glide.with(this).asBitmap().load(filepath);
        temp.into(viewFinder);
        temp.into((Target) new CustomTarget<Bitmap>(){

            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                bitmap = resource;
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {

            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (checkBitmap() == false){
            Toast.makeText(this, "Please choose a picture", Toast.LENGTH_SHORT).show();
        }else {
            switch (item.getItemId()){
                case R.id.action_save: String filepath = saveBitmap();
                    Toast.makeText(this, filepath, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private String saveBitmap() {
        File file = new File(String.valueOf(getExternalMediaDirs()[0]), String.valueOf(System.currentTimeMillis())+".jpg");
        String filepath = file.getAbsolutePath();
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return filepath;
    }

    private Bitmap portraitProcess(){
        if (checkBitmap() == false){
            Toast.makeText(this, "Please choose a picture", Toast.LENGTH_SHORT).show();
            return null;
        }else {
            Long currenttime = System.currentTimeMillis();
            ModelExecutionResult result = imageSegmentationModelExecutor.execute(bitmap);
            Bitmap mask_result = result.getBitmapMaskOnly();
            Bitmap portrait_simple_image = PortraitModel.simple(this, bitmap, mask_result);
            System.out.println("----------------------------");
            System.out.println(System.currentTimeMillis() - currenttime);
            return portrait_simple_image;
        }
    }

    private Bitmap proPortraitProcess(){
        if (checkBitmap() == false){
            Toast.makeText(this, "Please choose a picture", Toast.LENGTH_SHORT).show();
            return null;
        }else {
            Long currenttime = System.currentTimeMillis();
            double bitmap_width = bitmap.getWidth();
            double bitmap_height = bitmap.getHeight();
            int bitmap_new_width = 1200;
            int bitmap_new_height = (int)((bitmap_height / bitmap_width) * bitmap_new_width);
            bitmap = Bitmap.createScaledBitmap(bitmap, bitmap_new_width, bitmap_new_height, true);
            ModelExecutionResult result = imageSegmentationModelExecutor.execute(bitmap);
            Bitmap mask_257 = result.getBitmapResult();
            Bitmap depth_result = null;
            try {
                depth_result = ProcessDepthMap.run(this, bitmap, mask_257, imageSegmentationModelExecutor);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Bitmap portrait_pro_image = PortraitModel.proprocess(this, bitmap, depth_result);
            System.out.println("###################################");
            System.out.println(System.currentTimeMillis() - currenttime);
            return portrait_pro_image;
        }
    }


    private Boolean checkBitmap(){
        if (bitmap == null){
            return false;
        }else {
            return true;
        }
    }
}
